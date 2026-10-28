package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.HanimeVideo

private data class VideoAction(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false
)

/**
 * 视频卡片长按快捷菜单：收藏 / 下载 / 分享 / 搜索相关视频。
 *
 * 动作的可用性由调用方决定：[isFavorite] 控制收藏项文案，为 false 时整项隐藏，
 * 避免在收藏页里出现「收藏」这种自指操作；[onDownload] 为 null 时隐藏下载项（无法拿到
 * videoId 的场景，如无 id 的列表项）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionSheet(
    video: HanimeVideo,
    isFavorite: Boolean,
    onFavorite: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onShare: () -> Unit,
    onSearchRelated: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = video.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            if (onFavorite != null) {
                VideoActionRow(
                    icon = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    label = stringResource(if (isFavorite) R.string.detail_unfavorite else R.string.detail_favorite),
                    onClick = { onDismiss(); onFavorite() }
                )
            }
            if (onDownload != null) {
                VideoActionRow(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.detail_download),
                    onClick = { onDismiss(); onDownload() }
                )
            }
            VideoActionRow(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.detail_share),
                onClick = { onDismiss(); onShare() }
            )
            VideoActionRow(
                icon = Icons.Filled.Search,
                label = stringResource(R.string.card_search_related),
                onClick = { onDismiss(); onSearchRelated() }
            )
        }
    }
}

@Composable
private fun VideoActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
