package app.amisles.hanime.feature.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.ComicItem
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision

internal const val COMIC_GRID_COLUMNS = 3
internal const val COMIC_CARD_ASPECT_RATIO = 0.72f
internal const val COMIC_GRID_PADDING = 15
internal const val COMIC_GRID_GAP = 8
internal const val COMIC_LOAD_MORE_THRESHOLD = 6

/** 漫画封面卡片：漫画首页、标签 / 作者作品列表共用，保证三处视觉完全一致。 */
@Composable
internal fun ComicGridCard(comic: ComicItem, targetWidth: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    val context = LocalContext.current
    val density = LocalDensity.current
    val model = remember(comic.coverUrl, targetWidth, density) {
        val widthPx = with(density) { targetWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = (widthPx / COMIC_CARD_ASPECT_RATIO).toInt().coerceAtLeast(1)
        ImageRequest.Builder(context)
            .data(comic.coverUrl)
            .size(Dimension.Pixels(widthPx), Dimension.Pixels(heightPx))
            .precision(Precision.INEXACT)
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COMIC_CARD_ASPECT_RATIO)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
        ) {
            if (comic.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = model,
                    contentDescription = comic.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            text = comic.title,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
