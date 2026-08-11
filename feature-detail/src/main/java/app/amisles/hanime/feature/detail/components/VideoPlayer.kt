package app.amisles.hanime.feature.detail.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.core.ui.theme.HanimePrimary
import java.util.Locale
import java.util.concurrent.TimeUnit

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer,
    posterUrl: String = "",
    videoSources: List<VideoSource> = emptyList(),
    initialSourceUrl: String = "",
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
    onFullscreenToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val initialBarsBehavior = remember(activity) {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView)?.systemBarsBehavior
                ?: WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
        } ?: WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var currentSourceUrl by remember { mutableStateOf(initialSourceUrl) }
    var speedBtnBounds by remember { mutableStateOf(Rect.Zero) }
    var qualityBtnBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    // 手势状态
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var seekPreview by remember { mutableLongStateOf(0L) }
    var seekStart by remember { mutableLongStateOf(0L) }
    var brightness by remember {
        val initial = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initial < 0f) 0.5f else initial)
    }
    var videoZoom by remember { mutableFloatStateOf(1f) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var activeGesture by remember { mutableStateOf<String?>(null) }
    var playerWidth by remember { mutableFloatStateOf(0f) }
    var playerHeight by remember { mutableFloatStateOf(0f) }

    val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val sortedSources = remember(videoSources) {
        videoSources.sortedByDescending { it.size }
    }
    val currentResolution = remember(currentSourceUrl, sortedSources) {
        sortedSources.firstOrNull { it.url == currentSourceUrl }?.resolution ?: ""
    }

    val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                isReady = true
                duration = exoPlayer.duration
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            isBuffering = false
            isReady = true
        }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        exoPlayer.addListener(listener)
        val currentVol = exoPlayer.volume
        if (currentVol == 0f) {
            isMuted = true
            volume = 1f
        } else {
            isMuted = false
            volume = currentVol
        }
        playbackSpeed = exoPlayer.playbackParameters.speed
        isPlaying = exoPlayer.isPlaying
        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
        if (exoPlayer.playbackState == Player.STATE_READY) {
            isReady = true
            duration = exoPlayer.duration
        }
        currentPosition = exoPlayer.currentPosition
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { w ->
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller?.show(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior = initialBarsBehavior
            }
            if (activity?.requestedOrientation != initialOrientation) {
                try { activity?.requestedOrientation = initialOrientation } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(isReady, isPlaying) {
        if (isReady && !isPlaying) {
            while (!isPlaying) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekBackward() {
        exoPlayer.seekTo((exoPlayer.currentPosition - 15000).coerceAtLeast(0L))
        currentPosition = exoPlayer.currentPosition
    }

    fun seekForward() {
        exoPlayer.seekTo((exoPlayer.currentPosition + 15000).coerceAtMost(exoPlayer.duration))
        currentPosition = exoPlayer.currentPosition
    }

    fun toggleMute() {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0f else volume
    }

    fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        val attrs = window.attributes
        attrs.screenBrightness = value.coerceIn(0f, 1f)
        window.attributes = attrs
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer.setPlaybackSpeed(speed)
        showSpeedMenu = false
    }

    fun switchQuality(source: VideoSource) {
        if (source.url == currentSourceUrl) {
            showQualityMenu = false
            return
        }
        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition
        currentSourceUrl = source.url
        isBuffering = true
        showQualityMenu = false
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(source.url)))
        exoPlayer.prepare()
        exoPlayer.seekTo(position)
        if (wasPlaying) exoPlayer.play()
    }

    fun toggleFullscreen() {
        onFullscreenToggle(!isFullscreen)
    }

    LaunchedEffect(isFullscreen, activity) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                try {
                    activity.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR
                } catch (_: Exception) {}
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = initialBarsBehavior
                try { activity.requestedOrientation = initialOrientation } catch (_: Exception) {}
            }
        }
    }

    // 缩放手势结束后自动清除提示（detectTransformGestures 无 onEnd 回调）
    LaunchedEffect(videoZoom) {
        if (gestureHint?.startsWith("缩放") == true) {
            kotlinx.coroutines.delay(800)
            gestureHint = null
            activeGesture = null
        }
    }

    Box(
        modifier = modifier
            .then(
                if (isFullscreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                playerWidth = coords.size.width.toFloat()
                playerHeight = coords.size.height.toFloat()
            }
            // 双指缩放
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (activeGesture == null || activeGesture == "zoom") {
                        activeGesture = "zoom"
                        videoZoom = (videoZoom * zoom).coerceIn(0.5f, 2.0f)
                        gestureHint = "缩放: ${String.format(Locale.getDefault(), "%.1f", videoZoom)}x"
                    }
                }
            }
            // 水平滑动调节进度
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (activeGesture == null || activeGesture == "zoom") {
                            activeGesture = "seek"
                            dragStartX = offset.x
                            dragStartY = offset.y
                            seekStart = exoPlayer.currentPosition
                            seekPreview = seekStart
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (activeGesture == "seek" && duration > 0 && playerWidth > 0f) {
                            val totalDelta = change.position.x - dragStartX
                            val timeDelta = (totalDelta / playerWidth * duration.toFloat()).toLong()
                            seekPreview = (seekStart + timeDelta).coerceIn(0L, duration)
                            gestureHint = "${formatTime(seekPreview)} / ${formatTime(duration)}"
                        }
                    },
                    onDragEnd = {
                        if (activeGesture == "seek") {
                            exoPlayer.seekTo(seekPreview)
                            currentPosition = seekPreview
                            activeGesture = null
                            gestureHint = null
                        }
                    },
                    onDragCancel = {
                        if (activeGesture == "seek") {
                            activeGesture = null
                            gestureHint = null
                        }
                    }
                )
            }
            // 垂直滑动调节亮度/音量
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (activeGesture == null || activeGesture == "zoom") {
                            dragStartX = offset.x
                            dragStartY = offset.y
                            // 左半屏调节亮度，右半屏调节音量
                            if (offset.x < playerWidth / 2f) {
                                activeGesture = "brightness"
                            } else {
                                activeGesture = "volume"
                            }
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        when (activeGesture) {
                            "brightness" -> {
                                if (playerHeight > 0f) {
                                    val delta = -dragAmount / playerHeight
                                    brightness = (brightness + delta).coerceIn(0f, 1f)
                                    setBrightness(brightness)
                                    gestureHint = "亮度: ${(brightness * 100).toInt()}%"
                                }
                            }
                            "volume" -> {
                                if (playerHeight > 0f) {
                                    val delta = -dragAmount / playerHeight
                                    volume = (volume + delta).coerceIn(0f, 1f)
                                    exoPlayer.volume = volume
                                    isMuted = volume == 0f
                                    gestureHint = "音量: ${(volume * 100).toInt()}%"
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (activeGesture == "brightness" || activeGesture == "volume") {
                            activeGesture = null
                            gestureHint = null
                        }
                    },
                    onDragCancel = {
                        if (activeGesture == "brightness" || activeGesture == "volume") {
                            activeGesture = null
                            gestureHint = null
                        }
                    }
                )
            }
            .clickable {
                isControlsVisible = !isControlsVisible
                showSpeedMenu = false
                showQualityMenu = false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { view ->
                view.scaleX = videoZoom
                view.scaleY = videoZoom
            },
            modifier = Modifier.fillMaxSize()
        )

        // 手势提示覆盖层
        if (gestureHint != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = gestureHint ?: "",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
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

        if (isControlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            if (!isPlaying && !isBuffering && isReady) {
                IconButton(
                    onClick = { togglePlayPause() },
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                IconButton(
                    onClick = { seekBackward() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "快退15秒",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = { togglePlayPause() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = { seekForward() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "快进15秒",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
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
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { value ->
                            currentPosition = (value * duration).toLong()
                        },
                        onValueChangeFinished = {
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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(Color.White.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    )
                }

                IconButton(
                    onClick = { toggleMute() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted || volume == 0f) Icons.Default.VolumeMute
                        else if (volume < 0.5f) Icons.Default.VolumeDown
                        else Icons.Default.VolumeUp,
                        contentDescription = "音量",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = {
                        showSpeedMenu = !showSpeedMenu
                        showQualityMenu = false
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .onGloballyPositioned { coords ->
                            speedBtnBounds = Rect(
                                left = coords.positionInRoot().x,
                                top = coords.positionInRoot().y,
                                right = coords.positionInRoot().x + coords.size.width,
                                bottom = coords.positionInRoot().y + coords.size.height
                            )
                        }
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                if (sortedSources.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            showQualityMenu = !showQualityMenu
                            showSpeedMenu = false
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .onGloballyPositioned { coords ->
                                qualityBtnBounds = Rect(
                                    left = coords.positionInRoot().x,
                                    top = coords.positionInRoot().y,
                                    right = coords.positionInRoot().x + coords.size.width,
                                    bottom = coords.positionInRoot().y + coords.size.height
                                )
                            }
                    ) {
                        Text(
                            text = currentResolution.ifEmpty { "画质" },
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { toggleFullscreen() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            if (showSpeedMenu && speedBtnBounds != Rect.Zero) {
                val scrollState = rememberScrollState()
                val menuWidthPx = with(density) { 60.dp.toPx() }
                val menuHeightPx = with(density) { 120.dp.toPx() }
                val gapPx = with(density) { 4.dp.toPx() }
                Column(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (speedBtnBounds.center.x - menuWidthPx / 2f).toInt(),
                                (speedBtnBounds.top - menuHeightPx - gapPx).toInt()
                            )
                        }
                        .width(60.dp)
                        .height(120.dp)
                        .background(Color.Black.copy(alpha = 0.9f))
                        .verticalScroll(scrollState)
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    playbackSpeeds.forEach { speed ->
                        Text(
                            text = "${speed}x",
                            color = if (speed == playbackSpeed) HanimePrimary else Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    setPlaybackSpeed(speed)
                                },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (showQualityMenu && sortedSources.isNotEmpty() && qualityBtnBounds != Rect.Zero) {
                val scrollState = rememberScrollState()
                val menuWidthPx = with(density) { 70.dp.toPx() }
                val menuHeightPx = with(density) { 120.dp.toPx() }
                val gapPx = with(density) { 4.dp.toPx() }
                Column(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (qualityBtnBounds.center.x - menuWidthPx / 2f).toInt(),
                                (qualityBtnBounds.top - menuHeightPx - gapPx).toInt()
                            )
                        }
                        .width(70.dp)
                        .height(120.dp)
                        .background(Color.Black.copy(alpha = 0.9f))
                        .verticalScroll(scrollState)
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    sortedSources.forEach { source ->
                        val isSelected = source.url == currentSourceUrl
                        Text(
                            text = source.resolution,
                            color = if (isSelected) HanimePrimary else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    switchQuality(source)
                                },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}