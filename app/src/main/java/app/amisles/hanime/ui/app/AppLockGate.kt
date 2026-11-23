package app.amisles.hanime.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.AppSnackbar
import app.amisles.hanime.domain.repository.AppLockResult
import app.amisles.hanime.domain.repository.AppLockSettings
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

/**
 * 本进程内是否已解锁。
 *
 * 只存活在内存里：进程被杀后重新拉起必须重新输入 PIN，而转屏 / 深浅色切换这类重建不能重复索要。
 */
private object AppLockSession {
    var unlocked = false
}

/**
 * PIN 应用锁的宿主：盖在整棵界面之上，冷启动与从后台回到前台时要求输入 PIN。
 *
 * 锁定时机取 ON_STOP 而非 ON_PAUSE —— 画中画、权限弹窗与系统分屏都会触发 ON_PAUSE，
 * 用它会把这些场景误判成「用户离开了应用」。
 */
@Composable
internal fun AppLockGate(
    appLock: AppLockSettings,
    content: @Composable () -> Unit
) {
    val enabled by appLock.appLockEnabledFlow.collectAsStateWithLifecycle()
    var locked by remember { mutableStateOf(enabled && !AppLockSession.unlocked) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && enabled) {
                AppLockSession.unlocked = false
                locked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (enabled && locked) {
            AppLockScreen(
                verify = appLock::verifyPin,
                onUnlocked = {
                    AppLockSession.unlocked = true
                    locked = false
                },
                onUnavailable = {
                    appLock.disableAppLock()
                    AppLockSession.unlocked = true
                    locked = false
                }
            )
        }
    }
}

@Composable
private fun AppLockScreen(
    verify: suspend (String) -> AppLockResult,
    onUnlocked: () -> Unit,
    onUnavailable: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val unavailableHint = stringResource(R.string.app_lock_unavailable)
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }

    fun submit(pin: String) {
        verifying = true
        scope.launch {
            when (verify(pin)) {
                AppLockResult.UNLOCKED -> onUnlocked()
                AppLockResult.MISMATCH -> {
                    error = true
                    entered = ""
                }
                AppLockResult.UNAVAILABLE -> {
                    AppSnackbar.show(unavailableHint)
                    onUnavailable()
                }
            }
            verifying = false
        }
    }

    BackHandler(enabled = true) {}

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.app_lock_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 14.dp)
            )
            PinDots(
                count = entered.length,
                error = error,
                modifier = Modifier.padding(vertical = 22.dp)
            )
            if (error) {
                Text(
                    text = stringResource(R.string.app_lock_wrong_pin),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            PinKeypad(
                enabled = !verifying,
                onDigit = { digit ->
                    if (!verifying && entered.length < PIN_LENGTH) {
                        error = false
                        entered += digit
                        if (entered.length == PIN_LENGTH) submit(entered)
                    }
                },
                onDelete = {
                    if (!verifying && entered.isNotEmpty()) {
                        error = false
                        entered = entered.dropLast(1)
                    }
                },
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}

@Composable
private fun PinDots(count: Int, error: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> MaterialTheme.colorScheme.error
                            index < count -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9").chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    PinKey(label = digit, enabled = enabled, onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(modifier = Modifier.size(72.dp))
            PinKey(label = "0", enabled = enabled, onClick = { onDigit("0") })
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = stringResource(R.string.cd_backspace),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
