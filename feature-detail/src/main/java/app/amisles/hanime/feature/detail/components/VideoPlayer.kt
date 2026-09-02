package app.amisles.hanime.feature.detail.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.OrientationEventListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.feature.detail.ExoPlayerFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 把传感器角度归类为横屏 / 竖屏。
 *
 * 45° 附近的临界区（设备近乎平放或斜持）返回 null 交由下一次回调判定，
 * 避免角度抖动引发全屏反复切换。
 */
private fun classifyOrientationLandscape(orientation: Int): Boolean? = when {
    orientation in 55..125 -> true                       // 设备顶部朝左
    orientation in 235..305 -> true                      // 设备顶部朝右
    orientation <= 35 || orientation >= 325 -> false     // 正向竖持
    orientation in 145..215 -> false                     // 倒置竖持
    else -> null
}

/**
 * 是否为任一「锁定横屏」方向常量。
 *
 * 全屏是本应用唯一会锁屏幕方向的地方，因此快照到这些值说明快照时机正落在全屏中，
 * 不能作为退出全屏时的恢复目标（否则退出后页面会永远停在横屏）。
 */
private val Int.isLandscapeOrientation: Boolean
    get() = this == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            || this == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            || this == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            || this == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

// 长视频阈值
private const val LONG_VIDEO_MS = 15L * 60 * 1000

// 控件隐藏时贴在播放器底部的简易进度条高度
private val MINI_PROGRESS_HEIGHT = 3.dp

// ABR 升档前需连续稳定的 STATE_READY 次数
private const val STABLE_TICKS_FOR_UPGRADE = 4

// 视频缩放模式：SCALE_TO_FIT
private const val VIDEO_SCALING_MODE_SCALE_TO_FIT = 1

private fun formatTime(ms: Long): String {
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

/**
 * 进度条（时间文本 + 滑块）。进度状态与刷新轮询下沉到本组合内部，
 * 仅重排自身、不触发外层 VideoPlayer 重排。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackProgressBar(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    // 用户点击 / 拖动进度条时回调：外层手势据此判定本次触摸落在控件上，不做「切换控件显隐」
    onSeekInteract: () -> Unit = {}
) {
    var currentPosition by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var duration by remember { mutableLongStateOf(exoPlayer.duration) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
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
}

/**
 * 控件隐藏时贴在播放器最底部的简易进度条。
 *
 * 只展示当前播放进度、不接受任何交互：不加 clickable，点击会穿透到外层手势，
 * 等同于点击空白区域唤回控件，因此不需要可拖动 / 缓冲进度等能力。
 * 轮询仅在自身显示在组合中时才运行，控件显示时无额外开销。
 */
@Composable
private fun MiniPlaybackProgress(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            val d = exoPlayer.duration
            progress = if (d > 0) (exoPlayer.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(if (exoPlayer.isPlaying) 500 else 1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_PROGRESS_HEIGHT)
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(Color.White)
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer,
    posterUrl: String = "",
    videoSources: List<VideoSource> = emptyList(),
    initialSourceUrl: String = "",
    initialPositionMs: Long = 0L,
    preloadUrl: String = "",
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
    onFullscreenToggle: (Boolean) -> Unit = {},
    onPlaybackSpeedChanged: (Float) -> Unit = {},
    onQualityChanged: (String) -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    autoPlayNext: Boolean = true,
    onAutoPlayNextChanged: (Boolean) -> Unit = {},
    autoFullscreen: Boolean = true,
    onAutoFullscreenChanged: (Boolean) -> Unit = {},
    // 平板分栏等不适合自动全屏的场景由调用方传 false
    autoFullscreenEnabled: Boolean = autoFullscreen
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 退出全屏时恢复的屏幕方向
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation?.takeUnless { it.isLandscapeOrientation }
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val initialBarsBehavior = remember(activity) {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).systemBarsBehavior
        } ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLongPressBoost by remember { mutableStateOf(false) }
    val controlsActivityRef = remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val gestureScope = rememberCoroutineScope()
    val playbackSpeedRef = rememberUpdatedState(playbackSpeed)
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSwitchingQuality by remember { mutableStateOf(false) }
    var currentSourceUrl by remember { mutableStateOf(initialSourceUrl) }
    var speedBtnBounds by remember { mutableStateOf(Rect.Zero) }
    var qualityBtnBounds by remember { mutableStateOf(Rect.Zero) }
    // 播放器 Box 在窗口根坐标下的位置，用于把按钮根坐标换算为弹框（播放器局部坐标）的偏移
    var playerPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 手势状态
    var gestureHint by remember { mutableStateOf<String?>(null) }
    // 双击判定
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var seekPreview by remember { mutableLongStateOf(0L) }
    var brightness by remember {
        val initial = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initial < 0f) 0.5f else initial)
    }
    var videoZoom by remember { mutableFloatStateOf(1f) }
    var activeGesture by remember { mutableStateOf<String?>(null) }
    var playerWidth by remember { mutableFloatStateOf(0f) }
    var playerHeight by remember { mutableFloatStateOf(0f) }
    var isControlTap by remember { mutableStateOf(false) }

    // 画中画状态
    var isInPip by remember { mutableStateOf(false) }

    // 上一次判定出的设备方向（null = 尚未确定）
    var lastOrientationLandscape by remember { mutableStateOf<Boolean?>(null) }

    val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val sortedSources = remember(videoSources) {
        videoSources.sortedByDescending { it.size }
    }
    val currentResolution = remember(currentSourceUrl, sortedSources) {
        sortedSources.firstOrNull { it.url == currentSourceUrl }?.resolution ?: ""
    }

    // 首帧海报占位
    var showPoster by remember { mutableStateOf(posterUrl.isNotEmpty()) }
    // ABR 自适应
    var rebufferCount by remember { mutableStateOf(0) }
    var autoSwitched by remember { mutableStateOf(false) }
    var stableTicks by remember { mutableStateOf(0) }
    // 让 remember 的 Player.Listener 始终读到最新的画质列表，避免 videoSources 变化时闭包陈旧
    val sourcesRef = rememberUpdatedState(sortedSources)
    // 用 ref 持有最新的 onPlaybackEnded，避免 remember 的 Player.Listener 闭包捕获到陈旧 lambda
    val onPlaybackEndedRef = rememberUpdatedState(onPlaybackEnded)
    // 每次切换视频源时复位 seek 标记，确保续播点始终对应当前视频
    val initialPositionMsRef = rememberUpdatedState(initialPositionMs)
    val initialSeekAppliedRef = remember(initialSourceUrl) { mutableStateOf(false) }

    // 切换清晰度
    fun switchQuality(source: VideoSource) {
        if (source.url == currentSourceUrl || isSwitchingQuality) {
            showQualityMenu = false
            return
        }
        isSwitchingQuality = true
        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition
        currentSourceUrl = source.url
        isBuffering = true
        showQualityMenu = false
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(source.url)))
        exoPlayer.prepare()
        exoPlayer.seekTo(position)
        if (wasPlaying) exoPlayer.play()

        onQualityChanged(source.resolution)
    }

    val listener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // 仅"播放中"的缓冲视为 rebuffer
                        if (isPlaying && !autoSwitched) {
                            rebufferCount++
                            if (rebufferCount >= 2) {
                                val sources = sourcesRef.value
                                val idx = sources.indexOfFirst { it.url == currentSourceUrl }
                                if (idx > 0 && !isSwitchingQuality) {
                                    switchQuality(sources[idx - 1]) // 切到更低画质
                                    rebufferCount = 0
                                    autoSwitched = true
                                    stableTicks = 0
                                }
                            }
                        }
                    }
                    Player.STATE_READY -> {
                        isReady = true
                        isSwitchingQuality = false
                        rebufferCount = 0
                        // 续播：首帧就绪且有有效续播点（>5s 且未接近结尾）时跳转到上次位置
                        if (!initialSeekAppliedRef.value && initialPositionMsRef.value > 5000) {
                            val dur = exoPlayer.duration
                            if (dur <= 0 || initialPositionMsRef.value < dur - 5000) {
                                exoPlayer.seekTo(initialPositionMsRef.value)
                            }
                            initialSeekAppliedRef.value = true
                        }
                        // 解码优化：长视频保持解码器热身，降低切回前台解码延迟
                        exoPlayer.setForegroundMode(exoPlayer.duration > LONG_VIDEO_MS)
                        // ABR 升档：之前因卡顿降档且播放稳定一段时间，则尝试回升一档
                        if (autoSwitched) {
                            stableTicks++
                            val sources = sourcesRef.value
                            val idx = sources.indexOfFirst { it.url == currentSourceUrl }
                            if (stableTicks >= STABLE_TICKS_FOR_UPGRADE && idx in 0 until sources.lastIndex) {
                                switchQuality(sources[idx + 1])
                                autoSwitched = false
                                stableTicks = 0
                            }
                        } else {
                            stableTicks = 0
                        }
                    }
                    Player.STATE_ENDED -> {
                        // 播放结束：通知外层（如触发下一集自动播放）
                        onPlaybackEndedRef.value.invoke()
                    }
                }
            }

            //淡出海报占位
            override fun onRenderedFirstFrame() {
                showPoster = false
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                isBuffering = false
                isReady = true
                isSwitchingQuality = false
            }
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
        }
    }

    // 解码优化：统一缩放模式为 SCALE_TO_FIT，避免画面变形
    LaunchedEffect(Unit) {
        exoPlayer.videoScalingMode = VIDEO_SCALING_MODE_SCALE_TO_FIT
    }

    // 控件自动隐藏：播放中且未开菜单/画中画时，空闲 3.5s 后隐藏；手势/暂停/开菜单时重置计时。
    LaunchedEffect(
        isControlsVisible,
        isPlaying,
        isInPip,
        showSpeedMenu,
        showQualityMenu,
        showMoreMenu,
        controlsActivityRef.value
    ) {
        if (isControlsVisible && isPlaying && !isInPip && !showSpeedMenu && !showQualityMenu && !showMoreMenu) {
            delay(3500L)
            if (isPlaying && !showSpeedMenu && !showQualityMenu && !showMoreMenu) {
                isControlsVisible = false
            }
        }
    }

    // 切换视频时重置海报占位显示状态（posterUrl 变化即新视频）
    LaunchedEffect(posterUrl) {
        showPoster = posterUrl.isNotEmpty()
    }

    // 下一集预加载：将相关视频直链首段预热进 SimpleCache，进入即命中本地
    LaunchedEffect(preloadUrl) {
        if (preloadUrl.isNotBlank()) {
            ExoPlayerFactory.warmCacheFor(preloadUrl, context)
        }
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
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = initialBarsBehavior
            }
            if (activity?.requestedOrientation != initialOrientation) {
                try { activity?.requestedOrientation = initialOrientation } catch (_: Exception) {}
            }
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
    }

    fun seekForward() {
        exoPlayer.seekTo((exoPlayer.currentPosition + 15000).coerceAtMost(exoPlayer.duration))
    }

    fun toggleMute() {
        isMuted = !isMuted
        exoPlayer.volume = if (isMuted) 0f else volume
    }

    fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        val target = value.coerceIn(0f, 1f)
        val attrs = window.attributes
        // 与当前亮度差异过小则不写 window，避免手势拖动的每帧系统调用
        if (kotlin.math.abs(attrs.screenBrightness - target) < 0.01f) return
        attrs.screenBrightness = target
        window.attributes = attrs
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer.setPlaybackSpeed(speed)
        showSpeedMenu = false
        onPlaybackSpeedChanged(speed)
    }

    // 长按 2x 加速：仅临时修改播放器与界面倍速，不持久化到 Preferences（松手恢复原始倍速）
    fun setLongPressBoost(active: Boolean, restoreSpeed: Float) {
        if (active) {
            if (!isLongPressBoost) {
                isLongPressBoost = true
                playbackSpeed = 2f
                exoPlayer.setPlaybackSpeed(2f)
            }
        } else {
            if (isLongPressBoost) {
                isLongPressBoost = false
                playbackSpeed = restoreSpeed
                exoPlayer.setPlaybackSpeed(restoreSpeed)
            }
        }
    }

    fun toggleFullscreen() {
        onFullscreenToggle(!isFullscreen)
    }

    /**
     * 标记「本次触摸落在播放器控件上」。
     *
     * 外层手势在抬起时读取该标记：为真则跳过「切换控件显隐」，使点击控件只执行控件自身逻辑。
     * 隐藏途径由此收敛为两种——点击空白区域，或空闲超时。
     */
    fun markControlTap() {
        isControlTap = true
    }

    fun buildPipParams(): PictureInPictureParams {
        val ratio = if (playerWidth > 0f && playerHeight > 0f) {
            Rational(playerWidth.toInt().coerceAtLeast(1), playerHeight.toInt().coerceAtLeast(1))
        } else {
            Rational(16, 9)
        }
        return PictureInPictureParams.Builder().setAspectRatio(ratio).build()
    }

    // 全屏：隐藏系统栏并把屏幕方向锁定为横屏（SENSOR_LANDSCAPE 而非 SENSOR ——
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
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } catch (_: Exception) {}
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = initialBarsBehavior
                try { activity.requestedOrientation = initialOrientation } catch (_: Exception) {}
            }
        }
    }

    // 用 ref 持有最新的方向翻转处理器
    val orientationFlipRef = rememberUpdatedState { landscape: Boolean ->
        if (!isInPip) {
            if (landscape && !isFullscreen) onFullscreenToggle(true)
            else if (!landscape && isFullscreen) onFullscreenToggle(false)
        }
    }

    // 横屏自动全屏：设备由竖转横进入全屏，由横转竖退出全屏
    DisposableEffect(activity, autoFullscreenEnabled) {
        if (!autoFullscreenEnabled) return@DisposableEffect onDispose {}
        val act = activity ?: return@DisposableEffect onDispose {}
        val listener = object : OrientationEventListener(act) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val landscape = classifyOrientationLandscape(orientation) ?: return
                val previous = lastOrientationLandscape
                if (previous == landscape) return
                lastOrientationLandscape = landscape
                if (previous == null && !landscape) return
                orientationFlipRef.value(landscape)
            }
        }
        // 重新启用时清空上次方向，避免开关关闭期间转过的屏被下一次回调误判为翻转
        lastOrientationLandscape = null
        listener.enable()
        onDispose { listener.disable() }
    }

    // 全屏播放中按 Home/概览键自动进入画中画（API 26+）；退出 PiP 时复位 isInPip
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isFullscreen) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (isFullscreen && isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && activity?.isInPictureInPictureMode != true) {
                        activity?.enterPictureInPictureMode(buildPipParams())
                        isInPip = true
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isInPip = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 所有手势提示统一在 800ms 后自动清除（双击快进/快退、缩放、亮度、音量）
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
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
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                if (playerWidth != w || playerHeight != h) {
                    playerWidth = w
                    playerHeight = h
                }
                val pos = coords.positionInRoot()
                if (playerPos != pos) playerPos = pos
            }
            // 统一手势处理：单一 pointerInput 接管双指缩放、单指滑动（进度/亮度/音量）与点击/双击；
            // 以指针数为唯一真相源，避免多检测器竞争与状态卡死。
            .pointerInput(Unit) {
                while (true) {
                    awaitPointerEventScope {
                        var pointerCount = 0
                        var isZoom = false
                        var dragMode: String? = null   // "seek" | "brightness" | "volume"
                        var startX = 0f
                        var lastX = 0f
                        var lastY = 0f
                        var accDx = 0f
                        var accDy = 0f
                        var seekStartPos = 0L
                        var lastPinch = 0f
                        var downX = 0f
                        var downY = 0f
                        var moved = false
                        var initialized = false
                        // 长按 2x 加速：是否已触发、被覆盖前的原始倍速
                        var longPressFired = false
                        var preBoostSpeed = 1f
                        // 长按加速定时器：按下启动 1s 延时协程，松手/移动/缩放即取消，避免点击误触发 2x
                        var longPressJob: Job? = null

                        while (true) {
                            val event = awaitPointerEvent()
                            val presses = event.changes.filter { it.pressed }
                            pointerCount = presses.size
                            if (pointerCount == 0) {
                                // 所有手指抬起：结束本次手势
                                if (longPressFired) {
                                    // 长按加速结束：恢复原始倍速，不触发点击切换/快进退
                                    longPressJob?.cancel()
                                    setLongPressBoost(false, preBoostSpeed)
                                    longPressFired = false
                                    gestureHint = null
                                    break
                                }
                                // 普通抬起（未触发长按）：取消尚未到期的定时器，确保快速点击绝不误加速
                                longPressJob?.cancel()
                                if (!moved && !isZoom) {
                                    // 视为一次点击
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 300L) {
                                        lastTapTime = 0L
                                        if (!isInPip) {
                                            val rewind = downX < playerWidth / 2f
                                            if (rewind) seekBackward() else seekForward()
                                            gestureHint = if (rewind)
                                                context.getString(R.string.player_rewind_15s)
                                            else
                                                context.getString(R.string.player_forward_15s)
                                        }
                                    } else {
                                        lastTapTime = now
                                        if (isControlTap) {
                                            // 单击落在控件上：交由控件自身的 onClick 处理，手势不切换控制栏显隐、也不关闭菜单
                                            isControlTap = false
                                            lastTapTime = 0L
                                        } else {
                                            isControlsVisible = !isControlsVisible
                                            showSpeedMenu = false
                                            showQualityMenu = false
                                            showMoreMenu = false
                                        }
                                    }
                                }
                                if (dragMode == "seek") {
                                    exoPlayer.seekTo(seekPreview)
                                }
                                if (dragMode != null || isZoom) {
                                    gestureHint = null
                                }
                                break
                            }

                            if (pointerCount >= 2 && presses.size >= 2) {
                                // 双指：缩放（捏合/张开）
                                val a = presses[0]
                                val b = presses[1]
                                val dist = (a.position - b.position).getDistance()
                                if (!isZoom) {
                                    isZoom = true
                                    dragMode = null
                                    longPressJob?.cancel()
                                    lastPinch = dist
                                    showSpeedMenu = false
                                    showQualityMenu = false
                                }
                                if (lastPinch > 0f && dist > 0f) {
                                    val factor = dist / lastPinch
                                    videoZoom = (videoZoom * factor).coerceIn(0.5f, 2.0f)
                                    gestureHint = context.getString(
                                        R.string.player_zoom,
                                        String.format(Locale.getDefault(), "%.1f", videoZoom)
                                    )
                                }
                                lastPinch = dist
                            } else if (pointerCount == 1 && !isZoom) {
                                // 单指：先判定方向，再按模式处理
                                val c = presses[0]
                                val x = c.position.x
                                val y = c.position.y
                                if (!initialized) {
                                    downX = x
                                    downY = y
                                    lastX = x
                                    lastY = y
                                    initialized = true
                                    longPressFired = false
                                    preBoostSpeed = playbackSpeedRef.value
                                    // 每次手势开始都清空「落在控件上」标记：控件的 onClick 要到抬起时才置位，
                                    // 若上一次手势因轻微移动没走到判定分支，残留的标记会吞掉下一次空白点击
                                    isControlTap = false
                                    // 手势开始即重置自动隐藏倒计时
                                    controlsActivityRef.value = controlsActivityRef.value + 1
                                    // 启动长按加速定时器：单指静止按住满 1s 才触发 2x，松手/移动/缩放即取消
                                    longPressJob = gestureScope.launch {
                                        delay(1000L)
                                        if (!longPressFired && !moved && !isZoom && dragMode == null) {
                                            longPressFired = true
                                            setLongPressBoost(true, preBoostSpeed)
                                            showSpeedMenu = false
                                            showQualityMenu = false
                                            gestureHint = context.getString(R.string.player_long_press_boost)
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                    continue
                                }
                                if (dragMode == null) {
                                    accDx += x - lastX
                                    accDy += y - lastY
                                    val slop = 8f
                                    if (abs(accDx) > slop || abs(accDy) > slop) {
                                        moved = true
                                        longPressJob?.cancel()
                                        startX = x
                                        showSpeedMenu = false
                                        showQualityMenu = false
                                        seekStartPos = exoPlayer.currentPosition
                                        seekPreview = seekStartPos
                                        dragMode = if (abs(accDx) > abs(accDy)) {
                                            "seek"
                                        } else if (x < playerWidth / 2f) {
                                            "brightness"
                                        } else {
                                            "volume"
                                        }
                                    }
                                } else {
                                    when (dragMode) {
                                        "seek" -> {
                                            val dur = exoPlayer.duration
                                            if (dur > 0 && playerWidth > 0f) {
                                                val totalDelta = x - startX
                                                val timeDelta = (totalDelta / playerWidth * dur.toFloat()).toLong()
                                                seekPreview = (seekStartPos + timeDelta).coerceIn(0L, dur)
                                                gestureHint = "${formatTime(seekPreview)} / ${formatTime(dur)}"
                                            }
                                        }
                                        "brightness" -> {
                                            if (playerHeight > 0f) {
                                                val delta = -(y - lastY) / playerHeight
                                                brightness = (brightness + delta).coerceIn(0f, 1f)
                                                setBrightness(brightness)
                                                gestureHint = context.getString(R.string.player_brightness, (brightness * 100).toInt())
                                            }
                                        }
                                        "volume" -> {
                                            if (playerHeight > 0f) {
                                                val delta = -(y - lastY) / playerHeight
                                                volume = (volume + delta).coerceIn(0f, 1f)
                                                exoPlayer.volume = volume
                                                isMuted = volume == 0f
                                                gestureHint = context.getString(R.string.player_volume, (volume * 100).toInt())
                                            }
                                        }
                                    }
                                }
                                lastX = x
                                lastY = y
                            }

                            // 消费事件，避免父级（如页面/列表）误响应本播放器手势
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
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

        // 首帧海报占位：渲染前显示 poster，渲染后淡出，消除黑屏等待
        if (posterUrl.isNotEmpty()) {
            val posterAlpha by animateFloatAsState(
                targetValue = if (showPoster) 1f else 0f,
                animationSpec = tween(durationMillis = 250),
                label = "posterAlpha"
            )
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = posterAlpha }
            )
        }

        // 手势提示覆盖层
        if (gestureHint != null && !isInPip) {
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

        // 控件隐藏时，底部显示一条仅用于展示进度的简易进度条
        if (!isControlsVisible && !isInPip) {
            MiniPlaybackProgress(
                exoPlayer = exoPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }

        if (isControlsVisible && !isInPip) {
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
                    onClick = {
                        markControlTap()
                        togglePlayPause()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play),
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
                    onClick = {
                        markControlTap()
                        seekBackward()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = stringResource(R.string.cd_rewind_15s),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = {
                        markControlTap()
                        togglePlayPause()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = {
                        markControlTap()
                        seekForward()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = stringResource(R.string.cd_forward_15s),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                PlaybackProgressBar(
                    exoPlayer = exoPlayer,
                    onSeekInteract = { markControlTap() },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        markControlTap()
                        toggleMute()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeMute
                        else if (volume < 0.5f) Icons.AutoMirrored.Filled.VolumeDown
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.cd_volume),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = {
                        markControlTap()
                        showSpeedMenu = !showSpeedMenu
                        showQualityMenu = false
                        showMoreMenu = false
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .onGloballyPositioned { coords ->
                            val newBounds = Rect(
                                left = coords.positionInRoot().x,
                                top = coords.positionInRoot().y,
                                right = coords.positionInRoot().x + coords.size.width,
                                bottom = coords.positionInRoot().y + coords.size.height
                            )
                            if (speedBtnBounds != newBounds) speedBtnBounds = newBounds
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
                            markControlTap()
                            showQualityMenu = !showQualityMenu
                            showSpeedMenu = false
                            showMoreMenu = false
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .onGloballyPositioned { coords ->
                                val newBounds = Rect(
                                    left = coords.positionInRoot().x,
                                    top = coords.positionInRoot().y,
                                    right = coords.positionInRoot().x + coords.size.width,
                                    bottom = coords.positionInRoot().y + coords.size.height
                                )
                                if (qualityBtnBounds != newBounds) qualityBtnBounds = newBounds
                            }
                    ) {
                        Text(
                            text = currentResolution.ifEmpty { stringResource(R.string.cd_quality) },
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        markControlTap()
                        toggleFullscreen()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen)
                            stringResource(R.string.cd_exit_fullscreen)
                        else
                            stringResource(R.string.cd_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // 更多：点击弹出下拉框，内含连播开关
                Box {
                    IconButton(
                        onClick = {
                            markControlTap()
                            showMoreMenu = !showMoreMenu
                            showSpeedMenu = false
                            showQualityMenu = false
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.detail_more),
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.widthIn(min = 160.dp, max = 280.dp),
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ) {
                        // 连播（下一集自动播放）开关
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.detail_auto_play_next),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = autoPlayNext,
                                    onCheckedChange = { onAutoPlayNextChanged(it) },
                                    modifier = Modifier.scale(0.78f)
                                )
                            },
                            onClick = { onAutoPlayNextChanged(!autoPlayNext) },
                            modifier = Modifier.height(40.dp)
                        )

                        // 横屏自动全屏：设备转横屏自动进入全屏，转回竖屏自动退出
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.detail_auto_fullscreen),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = autoFullscreen,
                                    onCheckedChange = { onAutoFullscreenChanged(it) },
                                    modifier = Modifier.scale(0.78f)
                                )
                            },
                            onClick = { onAutoFullscreenChanged(!autoFullscreen) },
                            modifier = Modifier.height(40.dp)
                        )

                        if (isFullscreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.detail_pip),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    activity?.enterPictureInPictureMode(buildPipParams())
                                    isInPip = true
                                },
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }
                }
            }

            // 弹框打开时覆盖一层透明遮罩，点击其他区域即收起弹框
            if (showSpeedMenu || showQualityMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            markControlTap()
                            showSpeedMenu = false
                            showQualityMenu = false
                        }
                )
            }

            if (showSpeedMenu && speedBtnBounds != Rect.Zero) {
                val scrollState = rememberScrollState()
                val menuWidthPx = with(density) { 48.dp.toPx() }
                val menuHeightPx = with(density) { 120.dp.toPx() }
                val gapPx = with(density) { 4.dp.toPx() }
                // 按钮根坐标减播放器根坐标，换算为弹框局部坐标
                val localX = speedBtnBounds.center.x - playerPos.x
                val localY = speedBtnBounds.top - playerPos.y
                Column(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (localX - menuWidthPx / 2f).toInt(),
                                (localY - menuHeightPx - gapPx).toInt()
                            )
                        }
                        .width(48.dp)
                        .height(120.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .verticalScroll(scrollState)
                        .clickable { /* 拦截点击，避免穿透到遮罩关闭 */ }
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
                                    markControlTap()
                                    setPlaybackSpeed(speed)
                                },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (showQualityMenu && sortedSources.isNotEmpty() && qualityBtnBounds != Rect.Zero) {
                val scrollState = rememberScrollState()
                val menuWidthPx = with(density) { 56.dp.toPx() }
                val menuHeightPx = with(density) { 120.dp.toPx() }
                val gapPx = with(density) { 4.dp.toPx() }
                // 按钮根坐标减播放器根坐标，换算为弹框局部坐标
                val localX = qualityBtnBounds.center.x - playerPos.x
                val localY = qualityBtnBounds.top - playerPos.y
                Column(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (localX - menuWidthPx / 2f).toInt(),
                                (localY - menuHeightPx - gapPx).toInt()
                            )
                        }
                        .width(56.dp)
                        .height(120.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .verticalScroll(scrollState)
                        .clickable { /* 拦截点击，避免穿透到遮罩关闭 */ }
                        .padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    sortedSources.forEach { source ->
                        val isSelected = source.url == currentSourceUrl
                        Text(
                            text = source.resolution,
                            color = if (isSelected) HanimePrimary else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    markControlTap()
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