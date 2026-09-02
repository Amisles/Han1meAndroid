package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.feature.detail.util.videoEmoji
import app.amisles.hanime.feature.detail.util.videoGradient

/**
 * 相关推荐列表项：左侧缩略图 + 右侧标题/作者/数据行。
 * 作者名可点击进入搜索；卡片整体可点击播放。
 * 占位配色按视频 id 稳定散列，与拆分前逐次计算的取值完全一致。
 */
@Composable
internal fun DetailRelatedVideoCard(
    video: HanimeVideo,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    val gradient = videoGradient(video.id)
    val emoji = videoEmoji(video.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .shadow(
                elevation = if (isSystemInDarkTheme()) 0.dp else 2.dp,
                shape = RoundedCornerShape(8.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.18f)
            )
            .background(MaterialTheme.colorScheme.surface)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onVideoClick(video.videoUrl) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.amisles.hanime.core.ui.components.VideoThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            emoji = emoji,
            gradient = gradient,
            duration = "",
            likeRate = "",
            viewCount = "",
            crop = true,
            modifier = Modifier
                .width(120.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
