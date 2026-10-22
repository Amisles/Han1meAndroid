package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.ui.theme.surfaceEdge

private val RELATED_THUMB_WIDTH = 150.dp

private val RELATED_TITLE_FONT_SIZE = 12.sp
private val RELATED_TITLE_LINE_HEIGHT = 16.sp

/**
 * 相关推荐列表项：左侧缩略图 + 右侧标题 / 作者 / 数据行；作者名可点击进入搜索，卡片整体可点击播放。
 */
@Composable
internal fun DetailRelatedVideoCard(
    video: HanimeVideo,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .surfaceEdge(RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onVideoClick(video.videoUrl) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.amisles.hanime.core.ui.components.VideoThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            duration = "",
            likeRate = "",
            viewCount = "",
            crop = true,
            targetWidth = RELATED_THUMB_WIDTH,
            modifier = Modifier
                .width(RELATED_THUMB_WIDTH)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val titleBlockHeight = with(LocalDensity.current) {
                (RELATED_TITLE_LINE_HEIGHT * 2).toDp()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleBlockHeight),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = video.title,
                    fontSize = RELATED_TITLE_FONT_SIZE,
                    lineHeight = RELATED_TITLE_LINE_HEIGHT,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.CenterVertically)
                )
            }
            if (video.author.isNotEmpty()) {
                Text(
                    text = video.author,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { onAuthorClick(video.author) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = video.duration,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = video.likeRate,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = video.viewCount,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
