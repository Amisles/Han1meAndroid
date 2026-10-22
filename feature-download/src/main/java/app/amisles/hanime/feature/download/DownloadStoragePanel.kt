package app.amisles.hanime.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.DownloadStorageInfo

/**
 * 存储用量气泡。
 *
 * 用 surface + 描边 + 阴影从页面内容中浮起；颜色全部取自 colorScheme，
 * 深浅色主题下都有足够对比度。
 */
@Composable
internal fun DownloadStorageBubble(
    info: DownloadStorageInfo,
    onClearOrphans: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capacity = info.usedBytes + info.freeBytes
    val usedFraction = if (capacity > 0L) {
        (info.usedBytes.toFloat() / capacity.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = false) {}
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.download_storage_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.download_storage_free, formatFileSize(info.freeBytes)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Text(
            text = stringResource(R.string.download_storage_used, formatFileSize(info.usedBytes)),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = info.directory,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (info.orphanPartmapCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onClearOrphans)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.download_storage_orphans,
                        info.orphanPartmapCount,
                        formatFileSize(info.orphanPartmapBytes)
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.download_storage_clean),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
