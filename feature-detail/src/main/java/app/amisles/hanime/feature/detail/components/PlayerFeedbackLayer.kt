package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.domain.preferences.PlayerLayout
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun BoxScope.PlayerFeedbackLayer(
    exoPlayer: ExoPlayer,
    isLongPressBoost: Boolean,
    boostSpeed: Float,
    showBoostSlideHint: Boolean,
    gestureHint: String?,
    showGestureIntro: Boolean,
    isInPip: Boolean,
    sleepTimer: PlayerSleepTimer,
    isBuffering: Boolean,
    playbackError: PlaybackException?,
    isControlsVisible: Boolean,
    relatedActive: Boolean,
    activeLayout: PlayerLayout,
    layoutEditing: Boolean,
    videoZoom: Float,
    onMarkControlTap: () -> Unit,
    onRetry: () -> Unit,
    onResetZoom: () -> Unit
) {
    // 手势提示覆盖层：长按加速期间常驻显示实时倍率（上下滑动即时变化，松手才消失）；
    // 其余手势提示沿用 800ms 自动清除；首次播放的入门提示由 showGestureIntro 控制时长
    val boostText = if (isLongPressBoost) {
        stringResource(R.string.player_long_press_boost, trimSpeedText(boostSpeed))
    } else {
        null
    }
    val introText = if (showGestureIntro) stringResource(R.string.player_gesture_intro) else null
    val hintText = boostText ?: gestureHint ?: introText
    if (hintText != null && !isInPip) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = hintText,
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                if (boostText != null && showBoostSlideHint) {
                    Text(
                        text = stringResource(R.string.player_boost_slide_hint),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }

    if (!isInPip) {
        SleepCountdownHint(
            endAtMillis = sleepTimer.endAtMillis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        )
    }

    if (isBuffering) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.Center),
            color = Color.White,
            strokeWidth = 3.dp
        )
    }

    val currentPlaybackError = playbackError
    if (currentPlaybackError != null && !isInPip) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(playbackErrorTextRes(currentPlaybackError)),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = currentPlaybackError.errorCodeName,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(R.string.common_retry),
                color = HanimePrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        // 与播放器内既有控件一致：标记点击落在控件上，避免同一击又被外层手势当作空白点击
                        onMarkControlTap()
                        onRetry()
                    }
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
    }

    // 控件隐藏时，底部显示一条仅用于展示进度的简易进度条
    if (!isControlsVisible && !isInPip && !relatedActive && activeLayout.showMiniProgress) {
        MiniPlaybackProgress(
            exoPlayer = exoPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    }

    // 双指放大后几乎无法精确捏回原比例，给一个只在缩放态出现的还原出口
    if (activeLayout.showResetZoom && isControlsVisible && !isInPip && !relatedActive &&
        !layoutEditing && abs(videoZoom - 1f) > ZOOM_EPSILON
    ) {
        ResetZoomButton(
            onClick = {
                onMarkControlTap()
                onResetZoom()
            },
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = RESET_ZOOM_OFFSET)
        )
    }
}

private fun playbackErrorTextRes(error: PlaybackException): Int = when (error.errorCode) {
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> R.string.player_error_source
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.player_error_network
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> R.string.player_error_expired
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED -> R.string.player_error_decode
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> R.string.player_error_source
    else -> R.string.player_error_unknown
}

@Composable
private fun SleepCountdownHint(endAtMillis: Long, modifier: Modifier = Modifier) {
    var remainingMs by remember(endAtMillis) {
        mutableLongStateOf(endAtMillis - System.currentTimeMillis())
    }
    LaunchedEffect(endAtMillis) {
        if (endAtMillis <= 0L) return@LaunchedEffect
        delay((endAtMillis - System.currentTimeMillis() - SLEEP_COUNTDOWN_WINDOW_MS).coerceAtLeast(0L))
        while (true) {
            remainingMs = (endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remainingMs <= 0L) break
            delay(1000L)
        }
    }
    val seconds = (remainingMs / 1000).toInt()
    if (seconds in 1..(SLEEP_COUNTDOWN_WINDOW_MS / 1000).toInt()) {
        Text(
            text = stringResource(R.string.player_sleep_remaining, seconds),
            color = Color.White,
            fontSize = 13.sp,
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
