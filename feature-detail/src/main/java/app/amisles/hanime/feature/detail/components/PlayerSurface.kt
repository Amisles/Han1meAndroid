package app.amisles.hanime.feature.detail.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import coil3.compose.AsyncImage

@Composable
internal fun BoxScope.PlayerSurface(
    exoPlayer: ExoPlayer,
    posterUrl: String,
    showPoster: Boolean,
    resizeMode: PlayerResizeMode,
    videoAspect: Float,
    playerWidth: Float,
    playerHeight: Float,
    videoZoom: Float,
    isFullscreen: Boolean
) {
    // 拉伸模式先在视图层把「适应后」的画面按容器比例放大；裁切交给 PlayerView 自己的 zoom，
    // 两套缩放不要叠加，否则裁切模式会被二次放大
    val (stretchX, stretchY) = if (
        resizeMode == PlayerResizeMode.STRETCH &&
        videoAspect > 0f && playerWidth > 0f && playerHeight > 0f
    ) {
        val containerAspect = playerWidth / playerHeight
        if (containerAspect > videoAspect) containerAspect / videoAspect to 1f
        else 1f to videoAspect / containerAspect
    } else {
        1f to 1f
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
            }
        },
        update = { view ->
            view.resizeMode = if (resizeMode == PlayerResizeMode.CROP) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            view.scaleX = videoZoom * stretchX
            view.scaleY = videoZoom * stretchY
        },
        modifier = if (isFullscreen && resizeMode == PlayerResizeMode.RATIO_16_9) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
        } else {
            Modifier.fillMaxSize()
        }
    )

    // 首帧海报占位：渲染前显示 poster，渲染后淡出，消除黑屏等待
    if (posterUrl.isNotEmpty()) {
        val posterAlpha by animateFloatAsState(
            targetValue = if (showPoster) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "posterAlpha"
        )
        // 淡出完成后不再参与组合：alpha 恒为 0 的整屏位图无保留必要。
        // showPoster 只在 posterUrl 变化时置回 true，故不会出现「需要海报却已移除」。
        if (posterAlpha > 0f) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = posterAlpha }
            )
        }
    }
}
