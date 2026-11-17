package app.amisles.hanime.feature.detail.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import androidx.compose.ui.draw.clipToBounds
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.amisles.hanime.core.common.AppFeatures
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.LocalPrivacyMode
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.feature.detail.playback.PlaybackSessionBridge
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// 常量
// ─────────────────────────────────────────────────────────────────────────────

// ABR 升档前需连续稳定的 STATE_READY 次数
private const val STABLE_TICKS_FOR_UPGRADE = 4

// 睡眠定时进入倒计时提示的窗口：最后这段时间才逐秒刷新
private const val SLEEP_COUNTDOWN_WINDOW_MS = 30_000L

// ─────────────────────────────────────────────────────────────────────────────
// 主组件：VideoPlayer
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer,
    posterUrl: String = "",
    videoSources: List<VideoSource> = emptyList(),
    initialSourceUrl: String = "",
    initialPositionMs: Long = 0L,
    onInitialSeekApplied: (Long) -> Unit = {},
    isFullscreen: Boolean = false,
    modifier: Modifier = Modifier,
    onFullscreenToggle: (Boolean) -> Unit = {},
    onPlaybackSpeedChanged: (Float) -> Unit = {},
    onQualityChanged: (String) -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    autoPlayNext: Boolean = false,
    onAutoPlayNextChanged: (Boolean) -> Unit = {},
    // 循环播放：开启后当前视频播完自动从头重播（默认关闭）
    isLoopPlayback: Boolean = false,
    onLoopPlaybackChanged: (Boolean) -> Unit = {},
    // 隐私模式遮罩上的「退出」入口；由调用方写回偏好
    onDisablePrivacyMode: () -> Unit = {},
    // 平板分栏左半屏已是放大播放器、横持属常态握持，由调用方传 false 关闭自动全屏；
    // 用户也可在设置里关掉「旋转自动全屏」，两者同时成立才生效
    autoFullscreenEnabled: Boolean = true,
    // 长按临时加速倍率（1.5–3.0），只在按住期间生效
    longPressBoostSpeed: Float = 2f,
    // 双击左右区域快退 / 快进；关闭后双击不再触发跳转，也不会误切控制栏
    doubleTapSeekEnabled: Boolean = true,
    // 卡顿自动降档 / 回升；关闭后手动选择的画质不会被覆盖
    autoQualityEnabled: Boolean = true,
    // 媒体通知 / 锁屏 / 蓝牙控件展示的标题与作者；为空时通知只显示应用名
    mediaTitle: String = "",
    mediaArtist: String = "",
    // 源数据无法解析（响应不是视频）时通知外部重新拉取视频页，换取带新签名的直链
    onSourceError: () -> Unit = {},
    // 播放器左上角返回 / 回到首页入口；与其它控件同显隐，全屏时由调用方关闭
    onBackClick: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    showNavButtons: Boolean = true
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 退出全屏时恢复的屏幕方向：不能回落竖屏，否则平板退出全屏后会被钉在竖屏且不再跟随旋转
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation?.takeUnless { it.isLandscapeOrientation }
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val initialBarsBehavior = remember(activity) {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).systemBarsBehavior
        } ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    // ── 状态声明 ────────────────────────────────────────────────────────────
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLongPressBoost by remember { mutableStateOf(false) }
    // 长按加速的当前倍率：按住后上下滑动可在 1x–3x 之间连续调整，松手恢复用户选择的常驻倍速
    var boostSpeed by remember { mutableFloatStateOf(longPressBoostSpeed) }
    // 「上下滑动调速」上手提示：只在加速刚开始的几秒内出现，长时间按住不常驻遮挡画面
    var showBoostSlideHint by remember { mutableStateOf(false) }
    val controlsActivityRef = remember { mutableLongStateOf(0L) }
    val volumeState = remember { mutableFloatStateOf(1f) }
    var volume by volumeState
    val isMutedState = remember { mutableStateOf(false) }
    var isMuted by isMutedState

    // 旋转自动全屏：设置项是总开关，调用方仍可按布局单独关闭
    val appPrefs = LocalAppPreferences.current
    val autoRotateFullscreen by appPrefs.autoRotateFullscreenFlow.collectAsStateWithLifecycle()
    val orientationPinned by appPrefs.orientationPinnedFlow.collectAsStateWithLifecycle()

    val playerResizeMode by appPrefs.playerResizeModeFlow.collectAsStateWithLifecycle()
    // 睡眠定时与播放锁都是会话级状态：不落盘，避免下次打开莫名停住或锁住
    var sleepTimer by remember { mutableStateOf(PlayerSleepTimer()) }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    // 手动钉住的全屏方向（SCREEN_ORIENTATION_* 常量）；null 表示跟随 deviceTilt 自动选择
    var pinnedOrientation by remember { mutableStateOf<Int?>(null) }
    // 视频真实宽高比（含像素宽高比）：拉伸模式据此推算铺满容器所需的缩放系数
    var videoAspect by remember { mutableFloatStateOf(0f) }

    val privacyMode = LocalPrivacyMode.current
    // 隐私模式只遮罩缩略图，播放器照常可用；遮罩只在息屏后出现，亮屏时保持到用户主动点开
    var privacyMasked by rememberSaveable { mutableStateOf(false) }
    var resumeOnUnmask by remember { mutableStateOf(false) }

    LaunchedEffect(privacyMode) {
        if (!privacyMode) {
            privacyMasked = false
            resumeOnUnmask = false
        }
    }

    // 息屏必须立即停住画面：接收器在组合期间常驻，不依赖 Activity 的生命周期回调
    DisposableEffect(context, exoPlayer, privacyMode) {
        if (!privacyMode) return@DisposableEffect onDispose {}
        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_OFF) return
                resumeOnUnmask = runCatching { exoPlayer.isPlaying }.getOrDefault(false)
                runCatching { exoPlayer.pause() }
                privacyMasked = true
            }
        }
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(screenReceiver) } }
    }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val gestureScope = rememberCoroutineScope()
    val showSpeedMenuState = remember { mutableStateOf(false) }
    var showSpeedMenu by showSpeedMenuState
    val showQualityMenuState = remember { mutableStateOf(false) }
    var showQualityMenu by showQualityMenuState
    val showMoreMenuState = remember { mutableStateOf(false) }
    var showMoreMenu by showMoreMenuState
    var isSwitchingQuality by remember { mutableStateOf(false) }
    var currentSourceUrl by remember { mutableStateOf(initialSourceUrl) }
    var speedBtnBounds by remember { mutableStateOf(Rect.Zero) }
    var qualityBtnBounds by remember { mutableStateOf(Rect.Zero) }
    // 播放器 Box 在窗口根坐标下的位置，用于把按钮根坐标换算为弹框（播放器局部坐标）的偏移
    var playerPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 手势状态
    val gestureHintState = remember { mutableStateOf<String?>(null) }
    var gestureHint by gestureHintState
    var showGestureIntro by remember { mutableStateOf(AppFeatures.ONBOARDING_ENABLED && !Preferences.playerGestureIntroShown) }
    val lastTapTimeState = remember { mutableLongStateOf(0L) }
    var lastTapTime by lastTapTimeState
    val seekPreviewState = remember { mutableLongStateOf(0L) }
    var seekPreview by seekPreviewState
    val brightnessState =     remember {
        val initial = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initial < 0f) 0.5f else initial)
    }
    var brightness by brightnessState
    val videoZoomState = remember { mutableFloatStateOf(1f) }
    var videoZoom by videoZoomState
    var activeGesture by remember { mutableStateOf<String?>(null) }
    var playerWidth by remember { mutableFloatStateOf(0f) }
    var playerHeight by remember { mutableFloatStateOf(0f) }
    val isControlTapState = remember { mutableStateOf(false) }
    var isControlTap by isControlTapState

    // 画中画状态
    var isInPip by remember { mutableStateOf(false) }

    // 设备当前物理朝向（由下方 OrientationEventListener 维护，null = 尚未确定），仅用于全屏锁横屏一侧
    var deviceTilt by remember { mutableStateOf<DeviceTilt?>(null) }

    val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    val sortedSources = remember(videoSources) {
        videoSources.sortedByDescending { it.size }
    }
    val currentResolution = remember(currentSourceUrl, sortedSources) {
        sortedSources.firstOrNull { it.url == currentSourceUrl }?.resolution ?: ""
    }

    // 首帧海报占位
    var showPoster by remember { mutableStateOf(posterUrl.isNotEmpty()) }
    // ABR 自适应：缓冲次数 / 是否已自动降档 / 稳定计数
    var rebufferCount by remember { mutableStateOf(0) }
    var autoSwitched by remember { mutableStateOf(false) }
    var stableTicks by remember { mutableStateOf(0) }
    // 用户从清晰度菜单手动选择后置位：本次播放内不再被 ABR 覆盖。复位只依赖实例重建：
    // 切集/连播都是导航替换条目（新页面 = 新播放器 = 新实例）；不能以 initialSourceUrl 变化
    // 作为复位信号 —— 手动切画质回写偏好后 initialSourceUrl 也会变，会把刚设置的锁定误清
    var manualQualityOverride by remember { mutableStateOf(false) }
    // 上一次播放状态：用于区分「首次起播 / 换源的缓冲」与「播放中重新缓冲」
    var lastPlaybackState by remember { mutableStateOf(Player.STATE_IDLE) }
    // 播放失败：非空即进入可重试的失败态
    var playbackError by remember { mutableStateOf<PlaybackException?>(null) }
    // 链接类错误自动降档重试只做一次，避免多档全部失效时反复切换
    var autoErrorRetryUsed by remember { mutableStateOf(false) }
    // 源无法解析时只向外部请求一次刷新，避免刷新无果时循环
    var sourceRefreshRequested by remember { mutableStateOf(false) }
    val onSourceErrorRef = rememberUpdatedState(onSourceError)
    // 用 ref 持有最新值，避免 Player.Listener 闭包捕获到陈旧 lambda / 画质列表
    val sourcesRef = rememberUpdatedState(sortedSources)
    val onPlaybackEndedRef = rememberUpdatedState(onPlaybackEnded)
    val isLoopPlaybackRef = rememberUpdatedState(isLoopPlayback)
    val sleepAfterEpisodeRef = rememberUpdatedState(sleepTimer.afterEpisode)
    val initialPositionMsRef = rememberUpdatedState(initialPositionMs)
    // 浮层开关的实时快照：pointerInput 的闭包在首次组合时捕获，直接读状态会永远是旧值
    val menuOpenRef = rememberUpdatedState(showSpeedMenu || showQualityMenu)
    // 长按加速倍率同样要给手势闭包一个实时快照：播放器实例跨集复用，
    // 直接读参数会把首次组合时的倍率一直用下去
    val longPressBoostSpeedRef = rememberUpdatedState(longPressBoostSpeed)
    // 续播标记跟随播放器实例（一次实例 = 一部视频），不能以 initialSourceUrl 为键：
    // 画质切换会经「偏好回写 → 父级重组 → initialSourceUrl 变化」把标记复位，
    // 之后任意一次缓冲恢复（BUFFERING→READY）都会把位置跳回续播点，吞掉用户当前进度
    val initialSeekAppliedRef = remember(exoPlayer) { mutableStateOf(false) }

    // ── 清晰度切换 ──────────────────────────────────────────────────────────
    /**
     * 带元数据的媒体项。媒体通知、锁屏与蓝牙控件展示的标题 / 作者 / 封面都从这里来；
     * 换源与重试必须复用同一构造，否则通知会退回「未知曲目」。
     */
    fun buildMediaItem(url: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(mediaTitle)
            .setArtist(mediaArtist)
            .apply { if (posterUrl.isNotBlank()) setArtworkUri(Uri.parse(posterUrl)) }
            .build()
        val mimeType = sortedSources.firstOrNull { it.url == url }?.normalizedMimeType.orEmpty()
        val builder = MediaItem.Builder().setUri(url).setMediaMetadata(metadata)
        if (mimeType.isNotEmpty()) builder.setMimeType(mimeType)
        return builder.build()
    }

    fun switchQuality(source: VideoSource, userInitiated: Boolean = false) {
        if (source.url == currentSourceUrl || isSwitchingQuality) {
            showQualityMenu = false
            return
        }
        // 只有菜单点选才算「手动锁定」；ABR 自动换源走默认参数，不能把自己锁死
        if (userInitiated) {
            manualQualityOverride = true
            autoSwitched = false
            stableTicks = 0
            rebufferCount = 0
        }
        isSwitchingQuality = true
        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition
        currentSourceUrl = source.url
        isBuffering = true
        showQualityMenu = false
        exoPlayer.setMediaItem(buildMediaItem(source.url))
        exoPlayer.prepare()
        exoPlayer.seekTo(position)
        if (wasPlaying) exoPlayer.play()

        // 只有手动选择才回写持久化画质偏好：ABR 自动换源若也回写，弱网两次卡顿
        // 就会把用户显式设置的默认画质永久改成降档值（影响之后所有新视频的起播画质）
        if (userInitiated) onQualityChanged(source.resolution)
    }

    /** 失败态重试：清除错误后重新 prepare 当前媒体项，并尽量回到原播放位置。 */
    fun retryPlayback() {
        val position = exoPlayer.currentPosition
        playbackError = null
        isBuffering = true
        // 媒体项被清空（极端路径）时按当前源重建
        if (exoPlayer.mediaItemCount == 0 && currentSourceUrl.isNotBlank()) {
            exoPlayer.setMediaItem(buildMediaItem(currentSourceUrl))
        }
        exoPlayer.prepare()
        if (position > 0L) exoPlayer.seekTo(position)
    }

    // ── 播放器监听 ───────────────────────────────────────────────────────────
    val listener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val previousState = lastPlaybackState
                lastPlaybackState = playbackState
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // 仅「从 READY 掉回 BUFFERING 且用户意图播放」算一次 rebuffer。
                        // 不能用 isPlaying 判定：ExoPlayer 进入缓冲会先回调 onIsPlayingChanged(false) 再回调
                        // STATE_BUFFERING，以它为条件会让计数恒为 0。首次起播与换画质均不计入。
                        val isRebuffer = previousState == Player.STATE_READY &&
                            exoPlayer.playWhenReady && !isSwitchingQuality
                        if (isRebuffer && !autoSwitched && autoQualityEnabled && !manualQualityOverride) {
                            rebufferCount++
                            if (rebufferCount >= 2) {
                                val sources = sourcesRef.value
                                val idx = sources.indexOfFirst { it.url == currentSourceUrl }
                                if (idx > 0 && !isSwitchingQuality) {
                                    switchQuality(sources[idx - 1]) // 降一档
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
                        sourceRefreshRequested = false
                        rebufferCount = 0
                        playbackError = null
                        // 续播：首帧就绪且有有效续播点（>5s 且未接近结尾）时跳转到上次位置
                        if (!initialSeekAppliedRef.value && initialPositionMsRef.value > 5000) {
                            val dur = exoPlayer.duration
                            if (dur <= 0 || initialPositionMsRef.value < dur - 5000) {
                                exoPlayer.seekTo(initialPositionMsRef.value)
                                onInitialSeekApplied(initialPositionMsRef.value)
                            }
                            initialSeekAppliedRef.value = true
                        }
                        // 不调用 setForegroundMode(true)：前台状态由 PlaybackService 的媒体通知承担，
                        // 播放器本身无需再为后台保活另开一条路径。
                        // ABR 升档：之前因卡顿降档且播放稳定一段时间，则尝试回升一档
                        if (autoSwitched && autoQualityEnabled && !manualQualityOverride) {
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
                        // 循环播放时必须跳过：ExoPlayer 已在 repeatMode=REPEAT_MODE_ONE 下自行重播，
                        // 若仍走「结束」流程会切到下一集或停在结束画面，循环被打断
                        if (!isLoopPlaybackRef.value) {
                            // 「播完当前集」定时到期：停在结束画面，不再触发连播
                            if (sleepAfterEpisodeRef.value) {
                                sleepTimer = PlayerSleepTimer()
                                isControlsVisible = true
                                gestureHint = context.getString(R.string.player_sleep_finished)
                            } else {
                                onPlaybackEndedRef.value.invoke()
                            }
                        }
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoAspect = if (videoSize.height > 0) {
                    videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                } else {
                    0f
                }
            }

            // 淡出海报占位
            override fun onRenderedFirstFrame() {
                showPoster = false
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // 真正开始播放时才拉起前台服务：提前启动会让一个永不进入前台的服务常驻
                if (playing) PlaybackSessionBridge.ensureService(context)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                isBuffering = false
                isSwitchingQuality = false
                if (isSourceUnreadable(error) && !sourceRefreshRequested) {
                    sourceRefreshRequested = true
                    onSourceErrorRef.value()
                }
                val sources = sourcesRef.value
                val index = sources.indexOfFirst { it.url == currentSourceUrl }
                val linkBroken = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
                if (linkBroken && !autoErrorRetryUsed && index > 0) {
                    autoErrorRetryUsed = true
                    playbackError = null
                    gestureHint = context.getString(R.string.player_quality_fallback)
                    switchQuality(sources[index - 1])
                    exoPlayer.playWhenReady = true
                    return
                }
                // 保留错误用于渲染可重试的失败态；把 isReady 置 true 会让播放按钮显示为可播放却不响应
                playbackError = error
            }
        }
    }

    // ── 副作用：初始化 / 监听注册 / 屏幕常亮 / 解码模式 / 控件自动隐藏 ──
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 循环播放：由 player.repeatMode 实现，偏好变化即时下发。
    // 切集 / 换画质复用同一播放器实例，repeatMode 会保留，故以 isLoopPlayback 为键重新下发
    LaunchedEffect(isLoopPlayback) {
        exoPlayer.repeatMode =
            if (isLoopPlayback) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(Unit) {
        PlaybackSessionBridge.bind(exoPlayer, context)
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

    // 画面比例：适应与拉伸都要求解码器不裁切（拉伸由视图缩放实现），只有裁切模式才铺满容器
    LaunchedEffect(playerResizeMode) {
        exoPlayer.videoScalingMode = if (playerResizeMode == PlayerResizeMode.CROP) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // 睡眠定时：长延时到期即暂停（不停止，保留最后一帧）；AFTER_EPISODE 由 STATE_ENDED 处理
    LaunchedEffect(sleepTimer.endAtMillis) {
        val endAt = sleepTimer.endAtMillis
        if (endAt <= 0L) return@LaunchedEffect
        val remaining = endAt - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        runCatching { exoPlayer.pause() }
        sleepTimer = PlayerSleepTimer()
        isControlsVisible = true
        gestureHint = context.getString(R.string.player_sleep_paused)
    }

    // 控件自动隐藏：播放中且未开菜单 / 画中画时，空闲 3.5s 后隐藏；手势 / 暂停 / 开菜单时重置计时
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

    DisposableEffect(Unit) {
        onDispose {
            // 先解绑播放服务（会话随之释放），调用方随后才释放播放器
            PlaybackSessionBridge.unbind(exoPlayer)
            exoPlayer.removeListener(listener)
        }
    }

    // 退出组合时复位系统栏与屏幕方向，避免残留全屏状态
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

    // ── 内部方法 ─────────────────────────────────────────────────────────────
    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekBackward() {
        exoPlayer.seekTo((exoPlayer.currentPosition - 15000).coerceAtLeast(0L))
    }

    fun seekForward() {
        val target = exoPlayer.currentPosition + 15000
        val duration = exoPlayer.duration
        // 时长未知时 duration 返回 C.TIME_UNSET（负值），直接 coerceAtMost 会把目标值钳成负数，
        // 再被 seekTo 归零 —— 表现为「快进 15s 却跳回开头」。故仅在时长有效时做上限钳制。
        exoPlayer.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
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
        if (abs(attrs.screenBrightness - target) < 0.01f) return
        attrs.screenBrightness = target
        window.attributes = attrs
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer.setPlaybackSpeed(speed)
        showSpeedMenu = false
        onPlaybackSpeedChanged(speed)
    }

    // 长按加速：只临时修改播放器与「生效倍速」，不改写用户选择的 playbackSpeed。
    // 原实现把 playbackSpeed 直接置 2f，加速期间若打开倍速菜单再选中任一档，
    // UI 高亮与恢复值都会错乱（松手恢复到被覆盖后的值）；这里让两者彻底分离。
    // 加速的起手倍率取偏好里的「长按加速倍速」，之后可被上下滑动改写，松手一律回到 playbackSpeed。
    fun setLongPressBoost(active: Boolean) {
        if (active) {
            if (!isLongPressBoost) {
                isLongPressBoost = true
                boostSpeed = longPressBoostSpeedRef.value
                runCatching { exoPlayer.setPlaybackSpeed(boostSpeed) }
            }
        } else {
            if (isLongPressBoost) {
                isLongPressBoost = false
                // 退出组合时播放器可能已经释放，恢复失败不应打断收尾流程
                runCatching { exoPlayer.setPlaybackSpeed(playbackSpeed) }
            }
        }
    }

    /**
     * 长按加速期间上下滑动调整倍率。
     *
     * 只在加速中生效：手势若在松手后才补发一次位移，不能把常驻倍速改掉。
     * 与 [setLongPressBoost] 一样不回写偏好 —— 加速是临时状态，用户松手就该回到自己选的档位。
     */
    fun updateLongPressBoostSpeed(speed: Float) {
        if (!isLongPressBoost) return
        val target = speed.coerceIn(
            PlayerGestureMath.MIN_BOOST_SPEED,
            PlayerGestureMath.MAX_BOOST_SPEED
        )
        // 倍率已量化到 0.1x，同一档位下每帧事件都会重复下发同一个值，跳过可省掉无谓的播放器调用
        if (abs(target - boostSpeed) < 0.05f) return
        boostSpeed = target
        runCatching { exoPlayer.setPlaybackSpeed(target) }
    }

    /** 界面上展示的倍速：加速期间显示当前加速倍率（随滑动变化），其余显示用户选择的档位。 */
    val effectiveSpeed: Float = if (isLongPressBoost) boostSpeed else playbackSpeed

    fun toggleFullscreen() {
        onFullscreenToggle(!isFullscreen)
    }

    fun currentLandscapeOrientation(): Int {
        val display = activity?.display
        return pinnedLandscapeOrientation(
            tilt = deviceTilt,
            displayRotation = display?.rotation ?: Surface.ROTATION_0,
            naturalLandscape = display?.mode?.let { it.physicalWidth > it.physicalHeight } == true
        )
    }

    fun toggleOrientationPin() {
        val next = if (pinnedOrientation != null) null else currentLandscapeOrientation()
        pinnedOrientation = next
        // 落盘的是「要不要钉住」而非某一侧：换视频后设备朝向可能不同，还原时按当时的朝向重新取边
        appPrefs.setOrientationPinned(next != null)
    }

    /**
     * 标记「本次触摸落在播放器控件上」。外层手势在抬起时读取：为真则跳过「切换控件显隐」，
     * 使点击控件只执行控件自身逻辑。隐藏途径由此收敛为点击空白或空闲超时两种。
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

    // 方向钉住是跨视频保留的用户选择：进入全屏时按当时的物理朝向还原，否则切集后每集都要重新点一次。
    // 只在设备确实横置时钉：竖持时无法判断用户会朝哪一侧看，此时按显示旋转换算是错的边。
    LaunchedEffect(isFullscreen, orientationPinned, deviceTilt) {
        if (!isFullscreen) return@LaunchedEffect
        if (!orientationPinned) {
            pinnedOrientation = null
            return@LaunchedEffect
        }
        val tilt = deviceTilt
        if (pinnedOrientation == null && tilt != null && tilt.isLandscape) {
            tilt.landscapeOrientation?.let { pinnedOrientation = it }
        }
    }

    // 锁定即收起全部控件（锁图标是唯一出口）；退出全屏必须自动解锁，否则回到竖屏播放器再也点不动
    LaunchedEffect(isLocked, isFullscreen) {
        if (!isFullscreen) {
            isLocked = false
            pinnedOrientation = null
            return@LaunchedEffect
        }
        if (isLocked) {
            isControlsVisible = false
            showSpeedMenu = false
            showQualityMenu = false
            showMoreMenu = false
            setLongPressBoost(false)
            gestureHint = context.getString(R.string.player_unlock_hint)
        }
    }

    // ── 全屏与旋屏 ───────────────────────────────────────────────────────────
    // 全屏：隐藏系统栏并把方向锁到横屏，退出时复位。方向优先用监听到的物理朝向（deviceTilt）锁到
    // 具体一侧，而非丢一个 SENSOR_LANDSCAPE 让系统猜——系统「自动旋转」关闭时其方向传感器停用，
    // 只给 SENSOR_LANDSCAPE 会先落到默认横屏侧再翻回来（表现为点全屏瞬间画面上下颠倒再转正）。
    // 只有朝向未确定或设备本就竖持时才回退 SENSOR_LANDSCAPE。
    //
    // 非全屏时页面钉在竖屏：不钉的话窗口会先随旋转重排成横屏，几百毫秒后才被播放器全屏覆盖，
    // 表现为「整页转横 → 播放器再全屏」的两段过渡。窗口当前已是横屏时不钉（退出全屏仍横持的场景），
    // 由下一次转回竖屏再接管。
    val portraitLock = autoFullscreenEnabled && autoRotateFullscreen && !isInPip &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    PlayerFullscreenEffect(
        isFullscreen = isFullscreen,
        activity = activity,
        deviceTilt = deviceTilt,
        initialOrientation = initialOrientation,
        initialBarsBehavior = initialBarsBehavior,
        pinnedOrientation = pinnedOrientation,
        portraitLock = portraitLock
    )

    // ── 横屏自动全屏 ─────────────────────────────────────────────────────────
    // 设备由竖转横时自动进入全屏，由横转竖时自动退出；平板分栏、或用户在设置里关掉
    // 「旋转自动全屏」时不做自动切换（播放器的全屏按钮仍可用）。
    // 用 ref 持有最新状态与回调，避免 Effect 闭包捕获到陈旧的 isFullscreen / 回调。
    PlayerOrientationWatcher(
        activity = activity,
        autoFullscreenEnabled = autoFullscreenEnabled && autoRotateFullscreen,
        isInPip = isInPip,
        isFullscreen = isFullscreen,
        orientationPinned = pinnedOrientation != null,
        onFullscreenToggle = onFullscreenToggle
    ) { deviceTilt = it }

    // ── 画中画 ───────────────────────────────────────────────────────────────
    // 全屏播放中按 Home/概览键自动进入画中画（API 26+）；退出 PiP 时复位 isInPip。
    // 必须观察 Activity 的生命周期而非 LocalLifecycleOwner：后者的 ON_PAUSE 会在任何应用内导航
    // 时派发（如连播切下一集），会被误判为「用户离开 App」而错误缩成画中画。
    // isFullscreen / isPlaying 一律经 ref 取最新值，否则观察者会固化成陈旧快照而误触发。
    PlayerPipEffect(activity, isFullscreen, isPlaying, buildPipParams = { buildPipParams() }) { isInPip = it }

    // ── 手势提示自动清除 ─────────────────────────────────────────────────────
    // 普通手势提示统一在 800ms 后自动清除（双击快进/快退、缩放、亮度、音量）；
    // 长按加速倍率提示常驻显示，松手才消失，不受此计时影响
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(800)
            gestureHint = null
            activeGesture = null
        }
    }

    // 长按加速的上手提示：每次加速开头亮 2.5s，之后只保留实时倍率
    LaunchedEffect(isLongPressBoost) {
        showBoostSlideHint = false
        if (isLongPressBoost) {
            showBoostSlideHint = true
            delay(2500L)
            showBoostSlideHint = false
        }
    }

    // 首次播放的入门提示：展示满 3.5s 才落盘已读，中途退出播放页则下次重新出现
    LaunchedEffect(Unit) {
        if (!showGestureIntro) return@LaunchedEffect
        delay(3_500L)
        Preferences.markPlayerGestureIntroShown()
        showGestureIntro = false
    }

    // ── 播放器容器与统一手势 ─────────────────────────────────────────────────
    Box(
        modifier = modifier
            .then(
                if (isFullscreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            .background(Color.Black)
            .clipToBounds()
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
            // 以指针数为唯一真相源，避免多检测器竞争与状态卡死
            .playerGestures(
                exoPlayer = exoPlayer,
                density = density,
                context = context,
                menuOpen = { menuOpenRef.value },
                isLocked = { isLocked },
    isControlsVisible = { isControlsVisible },
    onControlsVisibleChange = { isControlsVisible = it },
    controlsActivityRef = controlsActivityRef,
    longPressBoostSpeed = { longPressBoostSpeed },
    volumeState = volumeState,
                isInPip = { isInPip },
                playerWidth = { playerWidth },
                playerHeight = { playerHeight },
                            gestureScope = gestureScope,
                gestureHintState = gestureHintState,
                lastTapTimeState = lastTapTimeState,
                seekPreviewState = seekPreviewState,
                brightnessState = brightnessState,
                videoZoomState = videoZoomState,
                isControlTapState = isControlTapState,
                isMutedState = isMutedState,
                showSpeedMenuState = showSpeedMenuState,
                showQualityMenuState = showQualityMenuState,
                showMoreMenuState = showMoreMenuState,
                seekBackward = { seekBackward() },
                seekForward = { seekForward() },
                togglePlayPause = { togglePlayPause() },
                setBrightness = { setBrightness(it) },
                setLongPressBoost = { setLongPressBoost(it) },
                setLongPressBoostSpeed = { updateLongPressBoostSpeed(it) },
                doubleTapSeekEnabled = { doubleTapSeekEnabled }
            )
    ) {
        // 拉伸模式先在视图层把「适应后」的画面按容器比例放大；裁切交给 PlayerView 自己的 zoom，
        // 两套缩放不要叠加，否则裁切模式会被二次放大
        val (stretchX, stretchY) = if (
            playerResizeMode == PlayerResizeMode.STRETCH &&
            videoAspect > 0f && playerWidth > 0f && playerHeight > 0f
        ) {
            val containerAspect = playerWidth / playerHeight
            if (containerAspect > videoAspect) containerAspect / videoAspect to 1f
            else 1f to videoAspect / containerAspect
        } else {
            1f to 1f
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { view ->
                view.resizeMode = if (playerResizeMode == PlayerResizeMode.CROP) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                view.scaleX = videoZoom * stretchX
                view.scaleY = videoZoom * stretchY
            },
            modifier = if (isFullscreen && playerResizeMode == PlayerResizeMode.RATIO_16_9) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center)
            } else {
                Modifier.fillMaxSize()
            }
        )

        // 首帧海报占位：渲染前显示 poster，渲染后淡出，消除黑屏等待
        if (posterUrl.isNotEmpty()) {
            val posterAlpha by animateFloatAsState(
                targetValue = if (showPoster) 1f else 0f,
                animationSpec = tween(durationMillis = 250),
                label = "posterAlpha"
            )
            // 淡出完成后不再参与组合：alpha 恒为 0 的整屏位图无保留必要。
            // showPoster 只在 posterUrl 变化时置回 true，故不会出现「需要海报却已移除」。
            if (posterAlpha > 0f) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = posterAlpha }
                )
            }
        }

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

        // 播放失败：始终展示（不受控件自动隐藏影响），给出原因与重试入口
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
                            markControlTap()
                            retryPlayback()
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
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

        // ── 控件 UI（可见时）──────────────────────────────────────────────────
        if (isControlsVisible && !isInPip) {
            if (showNavButtons) {
                PlayerNavButtons(
                    onBackClick = onBackClick,
                    onHomeClick = onNavigateHome,
                    tint = Color.White,
                    onControlTap = { markControlTap() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                )
            }
            PlayerControls(
                exoPlayer = exoPlayer,
                isFullscreen = isFullscreen,
                isInPip = isInPip,
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChanged = onAutoPlayNextChanged,
                isLoopPlayback = isLoopPlayback,
                onLoopPlaybackChanged = onLoopPlaybackChanged,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isReady = isReady,
                playbackError = playbackError,
                isMuted = isMuted,
                volume = volume,
                effectiveSpeed = effectiveSpeed,
                currentResolution = currentResolution,
                currentSourceUrl = currentSourceUrl,
                sortedSources = sortedSources,
                playbackSpeeds = playbackSpeeds,
                showSpeedMenu = showSpeedMenu,
                showQualityMenu = showQualityMenu,
                showMoreMenu = showMoreMenu,
                speedBtnBounds = speedBtnBounds,
                qualityBtnBounds = qualityBtnBounds,
                playerPos = playerPos,
                activity = activity,
                onPipChange = { isInPip = it },
                onShowSpeedMenuChange = { showSpeedMenu = it },
                onShowQualityMenuChange = { showQualityMenu = it },
                onShowMoreMenuChange = { showMoreMenu = it },
                onSpeedBoundsChange = { speedBtnBounds = it },
                onQualityBoundsChange = { qualityBtnBounds = it },
                onMarkControlTap = { markControlTap() },
                onTogglePlayPause = { togglePlayPause() },
                onSeekBackward = { seekBackward() },
                onSeekForward = { seekForward() },
                onToggleMute = { toggleMute() },
                onSetPlaybackSpeed = { setPlaybackSpeed(it) },
                onSwitchQuality = { source, userInitiated -> switchQuality(source, userInitiated) },
                onToggleFullscreen = { toggleFullscreen() },
                sleepTimer = sleepTimer,
                resizeMode = playerResizeMode,
                onLock = { isLocked = true },
                isOrientationPinned = pinnedOrientation != null,
                onToggleOrientationPin = { toggleOrientationPin() },
                onSleepTimerChange = { sleepTimer = it },
                onResizeModeChange = { appPrefs.setPlayerResizeMode(it) },
                onBuildPipParams = { buildPipParams() }
            )
        }

        if (isLocked && isFullscreen && !isInPip) {
            PlayerLockBadge(
                onUnlock = {
                    // 与其它控件一致：标记这一击落在控件上，外层手势才会跳过「切换控件显隐」，
                    // 否则同一击会被当成空白点击，刚显示出来的控件立刻又被收回。
                    markControlTap()
                    isLocked = false
                    isControlsVisible = true
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        if (privacyMode && privacyMasked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        privacyMasked = false
                        isControlsVisible = true
                        if (resumeOnUnmask) {
                            resumeOnUnmask = false
                            runCatching { exoPlayer.play() }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = stringResource(R.string.privacy_player_hidden),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.privacy_player_tap_continue),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = stringResource(R.string.privacy_exit),
                        color = HanimePrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDisablePrivacyMode() }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun isSourceUnreadable(error: PlaybackException): Boolean =
    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

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
