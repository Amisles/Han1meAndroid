package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.R

/** 播放锁锁定期间画面上唯一可见的控件：点按即解锁。 */
@Composable
internal fun PlayerLockBadge(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockLabel = stringResource(R.string.player_unlock)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onUnlock),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = unlockLabel,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
    }
}
