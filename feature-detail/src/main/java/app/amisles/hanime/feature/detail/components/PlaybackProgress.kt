package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

// 控件隐藏时贴在播放器底部的简易进度条高度
internal val MINI_PROGRESS_HEIGHT = 3.dp

/**
 * 可交互进度条（时间文本 + 滑块）。轨道按三层亮度展示「已播放 / 已缓冲 / 未缓冲」。
 * 进度轮询下沉到本组合内部，仅重排自身、不触发外层 VideoPlayer 重排。
 *
 * @param onSeekInteract 用户点击 / 拖动时回调，外层据此判定触摸落在控件上、不切换控件显隐。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackProgressBar(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    onSeekInteract: () -> Unit = {}
) {
    var currentPosition by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration) }
    // 已缓冲位置（毫秒）：画「已缓冲」段，与拖动无关，始终刷新
    var bufferedPosition by remember { mutableLongStateOf(exoPlayer.bufferedPosition) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            bufferedPosition = exoPlayer.bufferedPosition
            if (!isDragging) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration
            }
            delay(if (exoPlayer.isPlaying) 500 else 1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .offset(y = (-14).dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentPosition), color = Color.White, fontSize = 11.sp)
            Text(text = formatTime(duration), color = Color.White, fontSize = 11.sp)
        }
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onValueChange = { value ->
                isDragging = true
                currentPosition = (value * duration).toLong()
                onSeekInteract()
            },
            onValueChangeFinished = {
                isDragging = false
                exoPlayer.seekTo(currentPosition)
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(y = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            track = {
                // 三层亮度：30% = 未缓冲，60% = 已缓冲未播放，100% = 已播放。
                // 必须 coerceIn：fillMaxWidth(fraction) 要求 fraction 落在 0f..1f，越界会抛异常
                val playedFraction = (if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f)
                    .coerceIn(0f, 1f)
                val bufferedFraction = (if (duration > 0) bufferedPosition.toFloat() / duration.toFloat() else 0f)
                    .coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    // 已缓冲（含已播放部分）：压在已播放段之下，拖拽时可预览可跳范围
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bufferedFraction)
                            .height(8.dp)
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                    // 已播放
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(playedFraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                    )
                }
            }
        )
    }
}

/**
 * 控件隐藏时贴在播放器最底部的简易进度条：展示「已播放 / 已缓冲」两段进度，不接受交互
 * （点击穿透到外层手势，等同点击空白唤回控件）。三层亮度：25% = 未缓冲，55% = 已缓冲，100% = 已播放。
 */
@Composable
internal fun MiniPlaybackProgress(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var buffered by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            val d = exoPlayer.duration
            if (d > 0) {
                progress = (exoPlayer.currentPosition.toFloat() / d).coerceIn(0f, 1f)
                buffered = (exoPlayer.bufferedPosition.toFloat() / d).coerceIn(0f, 1f)
            } else {
                progress = 0f
                buffered = 0f
            }
            delay(if (exoPlayer.isPlaying) 500 else 1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_PROGRESS_HEIGHT)
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        // 已缓冲（含已播放部分）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(buffered)
                .background(Color.White.copy(alpha = 0.55f))
        )
        // 已播放
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(Color.White)
        )
    }
}
