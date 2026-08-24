package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

@Composable
fun VideoThumbnail(
    thumbnailUrl: String,
    emoji: String,
    gradient: Pair<Color, Color>,
    duration: String,
    likeRate: String,
    viewCount: String,
    modifier: Modifier = Modifier,
    adaptHeight: Boolean = false,
    crop: Boolean = false
) {
    var imageState by remember(thumbnailUrl) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val boxModifier = if (adaptHeight) {
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
    } else {
        modifier.fillMaxSize()
    }

    val contentScale = if (crop) ContentScale.Crop else ContentScale.FillWidth

    Box(
        modifier = boxModifier
    ) {
        if (thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onState = { state ->
                    imageState = state
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(gradient.first, gradient.second)
                        )
                    )
            ) {
                Text(
                    text = emoji,
                    fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        if (duration.isNotEmpty()) {
            Text(
                text = duration,
                fontSize = 8.sp,
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
                    Text(
                        text = "👍 $likeRate",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
                if (viewCount.isNotEmpty()) {
                    Text(
                        text = " $viewCount",
                        fontSize = 8.sp,
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
