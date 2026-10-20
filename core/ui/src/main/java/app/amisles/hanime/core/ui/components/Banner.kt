package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.domain.model.HanimeBanner
import coil3.compose.AsyncImage

/**
 * 首页大图轮播。
 *
 * 点击行为作用在整张大图上（不再单独渲染播放按钮）：有直链时跳详情播放，没有直链时退回按标题搜索。
 */
@Composable
fun Banner(
    bannerData: HanimeBanner,
    onPlayClick: () -> Unit = {},
    onSearchClick: (String) -> Unit = {}
) {
    val clickable = bannerData.videoUrl.isNotEmpty() || bannerData.title.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable(enabled = clickable) {
                if (bannerData.videoUrl.isNotEmpty()) {
                    onPlayClick()
                } else {
                    onSearchClick(bannerData.title)
                }
            }
    ) {
        if (bannerData.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = bannerData.imageUrl,
                contentDescription = bannerData.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            Text(
                text = bannerData.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = "${bannerData.author} • ${bannerData.viewCount} • ${bannerData.publishTime}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bannerData.tags.take(6).forEach { tag ->
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
