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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.HanimeVideo

/**
 * 网格卡：竖向排布，供搜索结果等网格布局使用。
 * 与 [VideoCard] 的差异是时长 / 点赞率 / 播放量下沉为缩略图角标，正文区只留「标题 + 作者」两行；
 * 标题固定占两行（minLines = maxLines = 2），避免长短标题导致同行卡片高度参差。
 */
@Composable
fun VideoGridCard(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    targetWidth: Dp? = null
) {
    val cfg = LocalUiConfig.current
    val coverRatio = videoCoverAspectRatio(video)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 先 clip 再 clickable，让点按涟漪跟随 8dp 圆角而不是方角
            .clip(RoundedCornerShape(cfg.cardCornerRadius))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverRatio)
                .clip(RoundedCornerShape(cfg.cardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true,
                targetWidth = targetWidth,
                aspectRatioOverride = coverRatio
            )
        }

        Text(
            text = video.title,
            fontSize = cfg.gridTitleFontSize,
            lineHeight = cfg.gridTitleLineHeight,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cfg.cardTitleTopPadding)
        )

        if (video.author.isNotEmpty()) {
            Text(
                text = video.author,
                fontSize = cfg.gridMetaFontSize,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 内层 clickable 会优先消费点击，不会连带触发整卡的 onClick
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 6.dp)
                    .clickable { onAuthorClick(video.author) }
            )
        }
    }
}
