package app.amisles.hanime.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.WatchLaterVideo

private val WATCH_LATER_CARD_WIDTH = 150.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WatchLaterSection(
    items: List<WatchLaterVideo>,
    onItemClick: (WatchLaterVideo) -> Unit,
    onRemove: (WatchLaterVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val cfg = LocalUiConfig.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.watch_later_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(WATCH_LATER_CARD_WIDTH)
                        .clip(RoundedCornerShape(cfg.cardCornerRadius))
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onRemove(item) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(cfg.thumbnailAspectRatio)
                            .clip(RoundedCornerShape(cfg.cardCornerRadius))
                    ) {
                        VideoThumbnail(
                            thumbnailUrl = item.thumbnailUrl,
                            duration = item.duration,
                            likeRate = "",
                            viewCount = "",
                            crop = true,
                            targetWidth = WATCH_LATER_CARD_WIDTH,
                            videoId = item.id,
                            contentDescription = item.title
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
