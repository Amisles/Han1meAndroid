package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.PrivacyAwareAsyncImage
import app.amisles.hanime.domain.model.HanimeVideo

internal val RELATED_CARD_WIDTH = 176.dp
internal val RELATED_CARD_HEIGHT = 99.dp

internal val RELATED_PEEK_WIDTH = (RELATED_CARD_WIDTH.value * 0.28f).dp

internal val RELATED_PANEL_HEIGHT = RELATED_CARD_HEIGHT + 46.dp

// 左滑拉到这个比例的播放器宽度就算完全展开：按整屏宽度换算手感过重，滑一点点只挪一格
private const val RELATED_DRAG_TRAVEL_RATIO = 0.45f

internal const val RELATED_OPEN_THRESHOLD = 0.35f

@Composable
internal fun BoxScope.RelatedPullPane(
    video: HanimeVideo,
    progress: Float,
    playerWidth: () -> Float,
    onOpen: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragFinished: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .graphicsLayerTransform(progress)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onDragFinished() },
                    onDragCancel = { onDragFinished() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val travel = playerWidth() * RELATED_DRAG_TRAVEL_RATIO
                        if (travel > 0f) onDragBy(-dragAmount / travel)
                    }
                )
            }
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOpen() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RelatedPullButton()
        Box(
            modifier = Modifier
                .height(RELATED_CARD_HEIGHT)
                .width(RELATED_PEEK_WIDTH)
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .requiredWidth(RELATED_CARD_WIDTH)
            ) {
                RelatedThumbnail(video = video, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun RelatedPullButton() {
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(72.dp)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = stringResource(R.string.player_related_panel),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun BoxScope.RelatedPanel(
    videos: List<HanimeVideo>,
    progress: Float,
    onCollapse: () -> Unit,
    onVideoClick: (HanimeVideo) -> Unit
) {
    if (progress <= 0.001f) return
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(RELATED_PANEL_HEIGHT)
            .graphicsLayerSlide(progress)
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

private fun Modifier.graphicsLayerTransform(progress: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        translationX = size.width * progress
        alpha = 1f - progress
    }
)

private fun Modifier.graphicsLayerSlide(progress: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        translationX = size.width * (1f - progress)
        alpha = progress
    }
)
