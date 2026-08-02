package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import coil3.compose.AsyncImage

@Composable
fun Banner(
    bannerData: HanimeBanner,
    onPlayClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onSearchClick: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
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
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(HanimePrimary, HanimeBackground)
                        )
                    )
            ) {
                Text(
                    text = "🎬",
                    fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            HanimeBackground.copy(alpha = 0.6f),
                            HanimeBackground
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
                color = HanimeTextPrimary,
                maxLines = 2,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = "${bannerData.author} • ${bannerData.viewCount} • ${bannerData.publishTime}",
                fontSize = 12.sp,
                color = HanimeTextSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bannerData.tags.take(6).forEach { tag ->
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        color = HanimeTextPrimary,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .padding(7.dp)
                        .clickable {
                            if (bannerData.videoUrl.isNotEmpty()) {
                                onPlayClick()
                            } else {
                                onSearchClick(bannerData.title)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.common_play),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.common_play),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(7.dp)
                        .clickable {
                            if (bannerData.videoUrl.isNotEmpty()) {
                                onInfoClick()
                            } else {
                                onSearchClick(bannerData.title)
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.common_more_info),
                        tint = HanimeTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.common_more_info),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = HanimeTextPrimary
                    )
                }
            }
        }
    }
}