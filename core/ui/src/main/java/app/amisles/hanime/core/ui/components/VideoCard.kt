package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients


private fun stableIndex(key: String, size: Int): Int {
    if (size <= 0) return 0
    return Math.floorMod(key.hashCode(), size)
}

@Composable
fun VideoCard(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gradient = gradients[stableIndex(video.id, gradients.size)]
    val emoji = emojis[stableIndex(video.id, emojis.size)]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                emoji = emoji,
                gradient = gradient,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (video.author.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = video.author,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onAuthorClick(video.author) }
                    )
                    if (video.publishTime.isNotEmpty()) {
                        Text(
                            text = " • ${video.publishTime}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * 紧凑列表行：整宽卡片，左侧小缩略图 + 右侧多行信息。
 * 风格对齐视频详情页「相关视频」区块，信息密度高于横滑大卡片。
 */
@Composable
fun VideoListItem(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gradient = gradients[stableIndex(video.id, gradients.size)]
    val emoji = emojis[stableIndex(video.id, emojis.size)]
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardShape = RoundedCornerShape(8.dp)
    val thumbShape = RoundedCornerShape(6.dp)
    val shadowColor = Color.Black.copy(alpha = 0.18f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 2.dp,
                shape = cardShape,
                clip = false,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .background(MaterialTheme.colorScheme.surface, cardShape)
            .clip(cardShape)
            .clickable { onClick() }
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            emoji = emoji,
            gradient = gradient,
            duration = video.duration,
            likeRate = "",
            viewCount = "",
            crop = true,
            modifier = Modifier
                .width(120.dp)
                .height(90.dp)
                .shadow(
                    elevation = if (isDark) 0.dp else 1.5.dp,
                    shape = thumbShape,
                    clip = false,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .clip(thumbShape)
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clickable { onAuthorClick(video.author) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (video.viewCount.isNotEmpty()) {
                    Text(
                        text = video.viewCount,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (video.likeRate.isNotEmpty()) {
                    Text(
                        text = "👍 ${video.likeRate}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
