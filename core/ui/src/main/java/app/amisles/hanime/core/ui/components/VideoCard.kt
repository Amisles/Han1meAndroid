package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.surfaceEdge

@Composable
fun VideoCard(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    targetWidth: Dp? = null
) {
    val cfg = LocalUiConfig.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cfg.thumbnailAspectRatio)
                .clip(RoundedCornerShape(cfg.cardCornerRadius))
        ) {
            VideoThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                likeRate = video.likeRate,
                viewCount = video.viewCount,
                crop = true,
                targetWidth = targetWidth
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cfg.cardTitleTopPadding)
        ) {
            Text(
                text = video.title,
                fontSize = cfg.cardTitleFontSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (video.author.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(cfg.cardMetaSpacing)
                ) {
                    Text(
                        text = video.author,
                        fontSize = cfg.cardMetaFontSize,
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
                            fontSize = cfg.cardMetaFontSize,
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

/** 紧凑列表行：整宽卡片，左侧小缩略图 + 右侧多行信息，信息密度高于横滑大卡片。 */
@Composable
fun VideoListItem(
    video: HanimeVideo,
    onClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    thumbnailWidth: Dp? = null
) {
    val cfg = LocalUiConfig.current
    val cardShape = RoundedCornerShape(cfg.cardCornerRadius)
    // 兜底：缩略图圆角需 thumbR + pad >= cardR，否则左边界会伸到卡片背景之外（浅色阴影下可见）
    val thumbShape = RoundedCornerShape(
        cfg.listThumbnailCornerRadius.coerceIn(
            (cfg.cardCornerRadius - cfg.listItemPadding).coerceAtLeast(0.dp),
            cfg.cardCornerRadius
        )
    )
    val thumbWidth = thumbnailWidth ?: cfg.listThumbnailWidth

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, cardShape)
            .surfaceEdge(cardShape)
            .clip(cardShape)
            .clickable { onClick() }
            .padding(cfg.listItemPadding),
        horizontalArrangement = Arrangement.spacedBy(cfg.listItemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            duration = video.duration,
            likeRate = "",
            viewCount = "",
            crop = true,
            targetWidth = thumbWidth,
            modifier = Modifier
                .width(thumbWidth)
                .aspectRatio(cfg.thumbnailAspectRatio)
                .clip(thumbShape)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                fontSize = cfg.listTitleFontSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.author.isNotEmpty()) {
                Text(
                    text = video.author,
                    fontSize = cfg.listMetaFontSize,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clickable { onAuthorClick(video.author) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(cfg.listMetaSpacing),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (video.viewCount.isNotEmpty()) {
                    Text(
                        text = video.viewCount,
                        fontSize = cfg.listMetaFontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (video.likeRate.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = video.likeRate,
                            fontSize = cfg.listMetaFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
