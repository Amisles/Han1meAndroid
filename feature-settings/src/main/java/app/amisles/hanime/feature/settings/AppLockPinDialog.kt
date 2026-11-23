package app.amisles.hanime.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.repository.AppLockResult
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

private val pinMask = VisualTransformation {
    TransformedText(AnnotatedString("●".repeat(it.text.length)), OffsetMapping.Identity)
}

internal enum class AppLockDialogMode { ENABLE, DISABLE }

/**
 * 应用锁的 PIN 设置 / 关闭弹窗。
 *
 * 开启走两步（输入 + 确认）：PIN 只输一次就落盘，输错一个数字就会把自己锁在应用外。
 */
@Composable
internal fun AppLockPinDialog(
    mode: AppLockDialogMode,
    verify: suspend (String) -> AppLockResult,
    onEnable: (String) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    val entered = if (confirming) confirmPin else pin
    val title = when {
        mode == AppLockDialogMode.DISABLE -> stringResource(R.string.app_lock_disable_title)
        confirming -> stringResource(R.string.app_lock_confirm_title)
        else -> stringResource(R.string.app_lock_set_title)
    }

    fun submit() {
        when {
            mode == AppLockDialogMode.DISABLE -> {
                checking = true
                scope.launch {
                    when (verify(pin)) {
                        AppLockResult.UNLOCKED, AppLockResult.UNAVAILABLE -> onDisable()
                        AppLockResult.MISMATCH -> {
                            mismatch = true
                            pin = ""
                        }
                    }
                    checking = false
                }
            }
            !confirming -> {
                confirming = true
                confirmPin = ""
            }
            pin == confirmPin -> onEnable(pin)
            else -> {
                mismatch = true
                confirming = false
                pin = ""
                confirmPin = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = entered,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(PIN_LENGTH)
                        mismatch = false
                        if (confirming) confirmPin = digits else pin = digits
                    },
                    singleLine = true,
                    enabled = !checking,
                    visualTransformation = pinMask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (mismatch) {
                    Text(
                        text = stringResource(
                            if (mode == AppLockDialogMode.DISABLE) {
                                R.string.app_lock_wrong_pin
                            } else {
                                R.string.app_lock_confirm_mismatch
                            }
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = entered.length == PIN_LENGTH && !checking,
                onClick = { submit() }
            ) {
                Text(
                    text = stringResource(
                        if (mode == AppLockDialogMode.DISABLE) {
                            R.string.app_lock_disable
                        } else {
                            R.string.common_confirm
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
