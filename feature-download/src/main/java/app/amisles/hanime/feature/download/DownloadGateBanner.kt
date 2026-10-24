package app.amisles.hanime.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.DownloadGateReason
import app.amisles.hanime.domain.model.DownloadGateState

/**
 * 调度阻塞提示条。
 *
 * 只在「确实有任务在排队」时展示：没有等待中的任务时提示没有意义，
 * 反而会让用户以为下载被无故限制。
 */
@Composable
internal fun DownloadGateBanner(state: DownloadGateState) {
    if (!state.blocked) return
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    // 先逐个解析文案再拼接：joinToString 的 lambda 不是 @Composable 上下文
    val reasons = state.blockedReasons.map { reasonLabel(it) }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = reasons,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.download_gate_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun reasonLabel(reason: DownloadGateReason): String = when (reason) {
    DownloadGateReason.NEED_WIFI -> stringResource(R.string.download_gate_blocked_wifi)
    DownloadGateReason.NEED_CHARGING -> stringResource(R.string.download_gate_blocked_charging)
    DownloadGateReason.NEED_NIGHT -> stringResource(R.string.download_gate_blocked_night)
}
