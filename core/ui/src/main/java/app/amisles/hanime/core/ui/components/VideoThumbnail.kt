package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.ThumbnailQuality
import app.amisles.hanime.core.ui.theme.UiConfig

@Composable
fun VideoThumbnail(
    thumbnailUrl: String,
    duration: String,
    likeRate: String,
    viewCount: String,
    modifier: Modifier = Modifier,
    crop: Boolean = false,
    targetWidth: Dp? = null
) {
    val cfg = LocalUiConfig.current
    val quality = cfg.thumbnailQuality
    val aspectRatio = cfg.thumbnailAspectRatio
    val context = LocalContext.current
    val density = LocalDensity.current

    val model: Any? = if (thumbnailUrl.isEmpty() || quality == ThumbnailQuality.STANDARD || targetWidth == null) {
        thumbnailUrl
    } else {
        remember(thumbnailUrl, quality, targetWidth, aspectRatio, density) {
            val widthPx = (with(density) { targetWidth.roundToPx() } * quality.sizeScale).toInt().coerceAtLeast(1)
            val heightPx = (widthPx / aspectRatio).toInt().coerceAtLeast(1)
            ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(Dimension.Pixels(widthPx), Dimension.Pixels(heightPx))
                .precision(Precision.INEXACT)
                .build()
        }
    }

    ThumbnailBox(
        model = model,
        hasThumbnail = thumbnailUrl.isNotEmpty(),
        duration = duration,
        likeRate = likeRate,
        viewCount = viewCount,
        contentScale = if (crop) ContentScale.Crop else ContentScale.FillWidth,
        cfg = cfg,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun ThumbnailBox(
    model: Any?,
    hasThumbnail: Boolean,
    duration: String,
    likeRate: String,
    viewCount: String,
    contentScale: ContentScale,
    cfg: UiConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        if (hasThumbnail) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }

        if (duration.isNotEmpty()) {
            Text(
                text = duration,
                fontSize = cfg.badgeFontSize,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }

        if (likeRate.isNotEmpty() || viewCount.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (likeRate.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = likeRate,
                            fontSize = cfg.badgeFontSize,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                if (viewCount.isNotEmpty()) {
                    Text(
                        text = viewCount,
                        fontSize = cfg.badgeFontSize,
                        fontWeight = FontWeight.Normal,
                        color = Color.White,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
