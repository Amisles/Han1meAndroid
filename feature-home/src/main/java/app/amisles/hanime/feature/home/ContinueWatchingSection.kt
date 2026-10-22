package app.amisles.hanime.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.WatchHistory

private val CONTINUE_CARD_WIDTH = 150.dp

/**
 * 「继续观看」横滑分区。
 *
 * 进度条与文字均取自 [MaterialTheme.colorScheme]，深浅色主题下自动适配。
 * 时长为 0（历史记录未拿到总时长）时不画进度条，避免出现一条永远是 0% 的空条。
 */
@Composable
internal fun ContinueWatchingSection(
    items: List<WatchHistory>,
    onItemClick: (WatchHistory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val cfg = LocalUiConfig.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_continue_watching),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { history ->
                Column(
                    modifier = Modifier
                        .width(CONTINUE_CARD_WIDTH)
                        .clip(RoundedCornerShape(cfg.cardCornerRadius))
                        .clickable { onItemClick(history) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(cfg.thumbnailAspectRatio)
                            .clip(RoundedCornerShape(cfg.cardCornerRadius))
                    ) {
                        VideoThumbnail(
                            thumbnailUrl = history.thumbnailUrl,
                            duration = history.duration,
                            likeRate = "",
                            viewCount = "",
                            crop = true
                        )
                        if (history.playbackDuration > 0L) {
                            LinearProgressIndicator(
                                progress = {
                                    (history.playbackPosition.toFloat() / history.playbackDuration.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            )
                        }
                    }

                    Text(
                        text = history.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (history.playbackDuration > 0L) {
                            val remainMs =
                                (history.playbackDuration - history.playbackPosition).coerceAtLeast(0L)
                            Text(
                                text = stringResource(
                                    R.string.home_continue_remaining,
                                    formatRemaining(remainMs)
                                ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 剩余时长文案：超过一小时按「X 小时 Y 分」，否则「X 分」，不足一分钟按「不到 1 分」。 */
internal fun formatRemaining(remainMs: Long): String {
    val totalMinutes = remainMs / 60_000L
    return when {
        totalMinutes <= 0L -> "<1"
        totalMinutes >= 60L -> "${totalMinutes / 60L}h${totalMinutes % 60L}m"
        else -> "${totalMinutes}m"
    }
}
