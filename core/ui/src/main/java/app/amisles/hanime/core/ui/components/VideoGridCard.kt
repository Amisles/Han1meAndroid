package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.theme.cardGeometry
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.cardSurface
import app.amisles.hanime.domain.model.HanimeVideo

/**
 * 网格卡：竖向排布，供搜索结果等网格布局使用。
 * 时长 / 点赞率 / 播放量由 VideoThumbnail 渲染为缩略图角标，正文区为标题行 + 作者行；
 * 标题行数取 card.titleLines，minLines 与 maxLines 用同一值，避免长短标题导致同行卡片高度参差。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    targetWidth: Dp? = null
) {
    val cfg = LocalUiConfig.current
    val card = cfg.card
    val coverRatio = videoCoverAspectRatio(video)
    val geometry = cardGeometry(cfg)

    val cardDescription = buildString {
        append(video.title)
        if (video.author.isNotEmpty()) append("，").append(video.author)
        if (video.duration.isNotEmpty()) append("，").append(video.duration)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(geometry.style, geometry.shape)
            // 先 clip 再 clickable，让状态层点击反馈跟随圆角而不是方角
            .clip(geometry.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = cardDescription }
            .padding(geometry.inset)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverRatio)
                .clip(geometry.thumbShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true,
                targetWidth = targetWidth,
                aspectRatioOverride = coverRatio,
                videoId = video.id
            )
        }

        Text(
            text = video.title,
            fontSize = cfg.gridTitleFontSize,
            lineHeight = cfg.gridTitleLineHeight,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            minLines = card.titleLines,
            maxLines = card.titleLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cfg.cardTitleTopPadding)
        )

        if ((card.showAuthor && video.author.isNotEmpty()) ||
            (card.showPublishTime && video.publishTime.isNotEmpty())
        ) {
            Row(
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (card.showAuthor && video.author.isNotEmpty()) {
                    Text(
                        text = video.author,
                        fontSize = cfg.gridMetaFontSize,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 内层 clickable 会优先消费点击，不会连带触发整卡的 onClick
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onAuthorClick(video.author) }
                    )
                }
                // 与 VideoCard 一致：作者后接「• 时间」，同处一行，不新增行高
                if (card.showPublishTime && video.publishTime.isNotEmpty()) {
                    Text(
                        text = "• ${video.publishTime}",
                        fontSize = cfg.gridMetaFontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
