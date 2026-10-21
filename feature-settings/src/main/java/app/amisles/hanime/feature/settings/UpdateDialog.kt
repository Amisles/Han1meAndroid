package app.amisles.hanime.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.AppUpdate

/**
 * 启动时的更新提示弹窗。
 *
 * 刻意做成「三选一」而不是「两个按钮 + 一个可关闭的叉」：
 * 用户明确表达「不再提示」需要一次点击，藏在关闭手势里会被误触或难以发现，
 * 于是下次启动又弹一遍 —— 正是本功能要避免的体验。
 */
@Composable
fun UpdateAvailableDialog(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.update_dialog_title, update.versionName),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 变更说明可能很长（有的发行版写了一整页），限高滚动而非撑满屏幕
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (update.releaseNotes.isNotBlank()) {
                    Text(
                        text = update.releaseNotes,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.update_available),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onSkipVersion) {
                    Text(
                        text = stringResource(R.string.update_do_not_remind),
                        fontSize = 13.sp
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.update_later),
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}
