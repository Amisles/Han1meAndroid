package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.domain.model.HanimeVideo

/**
 * 网格卡：竖向排布，供搜索结果等网格布局使用。
 *
 * 与 [VideoCard] 的差异：
 * - 缩略图 16:9（视频原生比例，全站视频卡片统一为该比例）；
 * - **时长、点赞率、播放量下沉为缩略图角标**（复用 [VideoThumbnail] 已有的角标能力），
 *   正文区只留「标题 + 作者」两行 —— 网格里卡片等高、扫读更快，也顺带修掉了
 *   「简化卡没有 duration 时，正文那行只剩两个没有语境的裸数字」的问题。
 *
 * 标题固定占两行（minLines = maxLines = 2），避免长短标题导致同一行卡片高度参差。
 */
@Composable
fun VideoGridCard(
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
            // 先 clip 再 clickable，让点按涟漪跟随 8dp 圆角而不是方角
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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

        Text(
            text = video.title,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )

        if (video.author.isNotEmpty()) {
            Text(
                text = video.author,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 内层 clickable 会优先消费点击，不会连带触发整卡的 onClick
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp)
                    .clickable { onAuthorClick(video.author) }
            )
        }
    }
}
