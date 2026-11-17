package app.amisles.hanime.feature.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.PrivacyAwareAsyncImage
import app.amisles.hanime.domain.model.HanimeVideo

internal val RELATED_CARD_WIDTH = 190.dp
internal val RELATED_CARD_HEIGHT = 107.dp

internal val RELATED_PEEK_WIDTH = (RELATED_CARD_WIDTH.value * 0.4f).dp

internal val RELATED_PANEL_HEIGHT = RELATED_CARD_HEIGHT + 46.dp

@Composable
internal fun BoxScope.RelatedPeek(
    video: HanimeVideo,
    visible: Boolean,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(240)) { it },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { it },
        modifier = Modifier.align(Alignment.CenterEnd)
    ) {
        Box(
            modifier = Modifier
                .height(RELATED_CARD_HEIGHT)
                .width(RELATED_PEEK_WIDTH)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .requiredWidth(RELATED_CARD_WIDTH)
            ) {
                RelatedThumbnail(video = video, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.player_related_panel),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
            )
        }
    }
}

@Composable
internal fun BoxScope.RelatedPanel(
    videos: List<HanimeVideo>,
    visible: Boolean,
    onCollapse: () -> Unit,
    onVideoClick: (HanimeVideo) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it },
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RELATED_PANEL_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.player_related_panel),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.player_related_collapse),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onCollapse() }
                            .padding(4.dp)
                    )
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = videos) { video ->
                        RelatedVideoCard(video = video, onClick = { onVideoClick(video) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoCard(
    video: HanimeVideo,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(RELATED_CARD_WIDTH)
            .height(RELATED_CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        RelatedThumbnail(video = video, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 6.dp)
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RelatedMeta(Icons.Outlined.Schedule, video.duration)
                RelatedMeta(Icons.Outlined.ThumbUp, video.likeRate)
                RelatedMeta(Icons.Default.PlayArrow, video.viewCount)
            }
        }
    }
}

@Composable
private fun RelatedMeta(
    icon: ImageVector,
    text: String
) {
    if (text.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun RelatedThumbnail(video: HanimeVideo, modifier: Modifier = Modifier) {
    val scrim = remember {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.72f),
            0.45f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.78f)
        )
    }
    Box(modifier = modifier) {
        PrivacyAwareAsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(scrim))
    }
}
