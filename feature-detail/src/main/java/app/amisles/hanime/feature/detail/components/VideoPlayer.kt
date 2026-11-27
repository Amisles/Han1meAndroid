package app.amisles.hanime.feature.detail.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import androidx.compose.ui.draw.clipToBounds
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.common.AppFeatures
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.LocalPrivacyMode
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.feature.detail.playback.PlaybackSessionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
    showNavButtons: Boolean = true,
    // 全屏右侧推荐列表的数据与点击回调；直接复用详情页推荐区的条目
    relatedVideos: List<HanimeVideo> = emptyList(),
    onRelatedVideoClick: (HanimeVideo) -> Unit = {},
    // 当前媒体标识。同一实例内换片（推荐列表直接换视频）时据此复位逐片状态
    mediaId: String = "",
    // 实时翻译模型就绪后才在「更多」菜单里提供开关；模型未下载时整项不出现
    realtimeTranslationAvailable: Boolean = false,
    // 当前直链的字幕语种；空串（站点没给这个视频简繁双字幕）时整项不出现
    subtitleLang: String = "",
    subtitleSwitching: Boolean = false,
    onSubtitleLangChange: (String) -> Unit = {},
    // 递增即要求换用新的 initialSourceUrl：换字幕语种后直链变了，进度留在原处
    sourceReloadToken: Int = 0
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 退出全屏时恢复的屏幕方向：播放器自己下发的方向锁（全屏锁横屏 / 页面锁竖屏）不能当快照，
    // 否则平板这类设备离场后会被钉在竖屏且不再跟随旋转
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation?.takeUnless { it.isPlayerOrientationLock }
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val initialBarsBehavior = remember(activity) {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).systemBarsBehavior
        } ?: WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    // ── 状态声明 ────────────────────────────────────────────────────────────
    var isControlsVisible by remember { mutableStateOf(true) }
    // 「上下滑动调速」上手提示：只在加速刚开始的几秒内出现，长时间按住不常驻遮挡画面
    var showBoostSlideHint by remember { mutableStateOf(false) }
    val controlsActivityRef = remember { mutableLongStateOf(0L) }

    // 旋转自动全屏：设置项是总开关，调用方仍可按布局单独关闭
    val appPrefs = LocalAppPreferences.current
    val autoRotateFullscreen by appPrefs.autoRotateFullscreenFlow.collectAsStateWithLifecycle()
    val orientationPinned by appPrefs.orientationPinnedFlow.collectAsStateWithLifecycle()

    val playerResizeMode by appPrefs.playerResizeModeFlow.collectAsStateWithLifecycle()
    val playerLayout by appPrefs.playerLayoutFlow.collectAsStateWithLifecycle()
    val realtimeTranslation by appPrefs.realtimeTranslationFlow.collectAsStateWithLifecycle()
    // 睡眠定时与播放锁都是会话级状态：不落盘，避免下次打开莫名停住或锁住
    var sleepTimer by remember { mutableStateOf(PlayerSleepTimer()) }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    // 手动钉住的全屏方向（SCREEN_ORIENTATION_* 常量）；null 表示跟随 deviceTilt 自动选择
    var pinnedOrientation by remember { mutableStateOf<Int?>(null) }

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
    val gestureScope = rememberCoroutineScope()
    val showSpeedMenuState = remember { mutableStateOf(false) }
    var showSpeedMenu by showSpeedMenuState
    val showQualityMenuState = remember { mutableStateOf(false) }
    var showQualityMenu by showQualityMenuState
    val showMoreMenuState = remember { mutableStateOf(false) }
    var showMoreMenu by showMoreMenuState
    // 编辑态一律渲染草稿：取消直接丢弃，完成才写回偏好
    var layoutEditing by remember { mutableStateOf(false) }
    var layoutDraft by remember { mutableStateOf(playerLayout) }
    // 必须是 State：手势的 pointerInput 只保留首次组合的 lambda，普通 val 会被固化成首帧布局，
    // 表现为「区域怎么改都还是默认三等分」
    val activeLayout by remember {
        derivedStateOf { if (layoutEditing) layoutDraft else playerLayout }
    }
    // 推荐面板用 0..1 的进度驱动：手指左滑时跟随手指，点击/松手时由动画补齐
    var relatedPanelOpen by remember { mutableStateOf(false) }
    var relatedDragProgress by remember { mutableStateOf<Float?>(null) }
    val relatedSettled by animateFloatAsState(
        targetValue = if (relatedPanelOpen) 1f else 0f,
        animationSpec = tween(260),
        label = "relatedPanelProgress"
    )
    val relatedProgress = relatedDragProgress ?: relatedSettled
    val relatedActive = relatedProgress > 0.02f
    var speedBtnBounds by remember { mutableStateOf(Rect.Zero) }
    var qualityBtnBounds by remember { mutableStateOf(Rect.Zero) }
    // 播放器 Box 在窗口根坐标下的位置，用于把按钮根坐标换算为弹框（播放器局部坐标）的偏移
    var playerPos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // 手势状态
    val gestureHintState = remember { mutableStateOf<String?>(null) }
    var gestureHint by gestureHintState
    var showGestureIntro by remember { mutableStateOf(AppFeatures.ONBOARDING_ENABLED && !appPrefs.hints.playerGestureIntroShown) }
    val lastTapTimeState = remember { mutableLongStateOf(0L) }
    var lastTapTime by lastTapTimeState
    // 进度预览的目标位置：null = 未在拖动。滑动手势与进度条共用这一个来源
    val seekPreviewState = remember { mutableStateOf<Long?>(null) }
    var seekPreview by seekPreviewState
    var previewFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val brightnessState = remember(activity) {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        )
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
    val onSourceErrorRef = rememberUpdatedState(onSourceError)
    val sourcesRef = rememberUpdatedState(sortedSources)
    val mediaTitleRef = rememberUpdatedState(mediaTitle)
    val mediaArtistRef = rememberUpdatedState(mediaArtist)
    val posterUrlRef = rememberUpdatedState(posterUrl)
    val onPlaybackEndedRef = rememberUpdatedState(onPlaybackEnded)
    val isLoopPlaybackRef = rememberUpdatedState(isLoopPlayback)
    val sleepAfterEpisodeRef = rememberUpdatedState(sleepTimer.afterEpisode)
    val initialPositionMsRef = rememberUpdatedState(initialPositionMs)
    val longPressBoostSpeedRef = rememberUpdatedState(longPressBoostSpeed)
    val doubleTapSeekEnabledRef = rememberUpdatedState(doubleTapSeekEnabled)
    val onQualityChangedRef = rememberUpdatedState(onQualityChanged)
    val onPlaybackSpeedChangedRef = rememberUpdatedState(onPlaybackSpeedChanged)
    val onInitialSeekAppliedRef = rememberUpdatedState(onInitialSeekApplied)
    val privacyModeRef = rememberUpdatedState(privacyMode)

    val playback = remember(exoPlayer) {
        PlayerPlaybackController(
            exoPlayer = exoPlayer,
            context = context,
            sources = { sourcesRef.value },
            mediaTitle = { mediaTitleRef.value },
            mediaArtist = { mediaArtistRef.value },
            posterUrl = { posterUrlRef.value },
            initialSourceUrl = initialSourceUrl,
            initialPositionMs = { initialPositionMsRef.value },
            isPrivacyMode = { privacyModeRef.value },
            autoQualityEnabled = { autoQualityEnabled },
            isLoopPlayback = { isLoopPlaybackRef.value },
            sleepAfterEpisode = { sleepAfterEpisodeRef.value },
            longPressBoostSpeed = { longPressBoostSpeedRef.value },
            onSourceError = { onSourceErrorRef.value() },
            onPlaybackEnded = { onPlaybackEndedRef.value() },
            onQualityChanged = { onQualityChangedRef.value(it) },
            onPlaybackSpeedChanged = { onPlaybackSpeedChangedRef.value(it) },
            onInitialSeekApplied = { onInitialSeekAppliedRef.value(it) },
            onSleepEpisodeFinished = {
                sleepTimer = PlayerSleepTimer()
                isControlsVisible = true
                gestureHint = context.getString(R.string.player_sleep_finished)
            },
            onGestureHint = { gestureHint = it },
            onCloseSpeedMenu = { showSpeedMenu = false },
            onCloseQualityMenu = { showQualityMenu = false },
            onServiceNeeded = { PlaybackSessionBridge.ensureService(context) }
        )
    }

    var isPlaying by playback.isPlaying
    var isBuffering by playback.isBuffering
    var isReady by playback.isReady
    var isLongPressBoost by playback.isLongPressBoost
    var boostSpeed by playback.boostSpeed
    var volume by playback.volume
    var isMuted by playback.isMuted
    var currentSourceUrl by playback.currentSourceUrl
    var showPoster by playback.showPoster
    var playbackError by playback.playbackError
    var videoAspect by playback.videoAspect

    val currentResolution = remember(currentSourceUrl, sortedSources) {
        sortedSources.firstOrNull { it.url == currentSourceUrl }?.resolution ?: ""
    }
    val effectiveSpeed = playback.effectiveSpeed
    // 浮层开关的实时快照：pointerInput 的闭包在首次组合时捕获，直接读状态会永远是旧值
    val menuOpenRef = rememberUpdatedState(
        showSpeedMenu || showQualityMenu || layoutEditing || relatedActive
    )

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
        playback.attach()
    }

    LaunchedEffect(privacyMode) {
        playback.refreshMetadata()
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

    // 换片后父级会把新视频的直链传进来：本源标记必须跟上，否则清晰度菜单、预览取帧都还指着上一部
    LaunchedEffect(initialSourceUrl) {
        playback.onSourceUrlChanged(initialSourceUrl)
    }

    LaunchedEffect(sourceReloadToken) {
        if (sourceReloadToken == 0) return@LaunchedEffect
        playback.switchToSource(initialSourceUrl)
    }

    // 同一个播放器换片：逐片状态必须回初值，否则上一部的错误态、手动锁画质、续播标记会被带进新视频
    LaunchedEffect(mediaId) {
        playback.onMediaChanged(initialSourceUrl)
        seekPreview = null
        previewFrame = null
        relatedDragProgress = null
        relatedPanelOpen = false
    }

    // 控件自动隐藏：播放中且未开菜单 / 画中画时，空闲 3.5s 后隐藏；手势 / 暂停 / 开菜单时重置计时
    LaunchedEffect(
        isControlsVisible,
        isPlaying,
        isInPip,
        showSpeedMenu,
        showQualityMenu,
        showMoreMenu,
        layoutEditing,
        relatedActive,
        controlsActivityRef.longValue
    ) {
        if (isControlsVisible && isPlaying && !isInPip && !showSpeedMenu && !showQualityMenu &&
            !showMoreMenu && !layoutEditing && !relatedActive
        ) {
            delay(3500L)
            if (isPlaying && !showSpeedMenu && !showQualityMenu && !showMoreMenu &&
                !layoutEditing && !relatedActive
            ) {
                isControlsVisible = false
            }
        }
    }

    BackHandler(enabled = layoutEditing) { layoutEditing = false }

    LaunchedEffect(relatedPanelOpen) {
        if (!relatedPanelOpen) return@LaunchedEffect
        isControlsVisible = false
        showSpeedMenu = false
        showQualityMenu = false
        showMoreMenu = false
    }

    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            relatedPanelOpen = false
            relatedDragProgress = null
        }
    }

    // 切换视频时重置海报占位显示状态（posterUrl 变化即新视频）
    LaunchedEffect(posterUrl) {
        showPoster = posterUrl.isNotEmpty()
    }

    DisposableEffect(Unit) {
        onDispose {
            // 先解绑播放服务（会话随之释放），调用方随后才释放播放器
            playback.detach()
            PlayerFramePreview.release()
        }
    }

    // 退出组合时复位系统栏、屏幕亮度与屏幕方向，避免残留全屏状态
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { w ->
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = initialBarsBehavior
                val attrs = w.attributes
                // 亮度覆盖挂在窗口上而不是播放器实例：离场一律交还系统亮度。改回「还原进场快照」会踩到
                // 详情页连跳 / 平板全屏切换时两个实例同时在场——后进场者把前者写下的覆盖值当成快照，退出后全应用停在手势亮度。
                if (attrs.screenBrightness >= 0f) {
                    attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    w.attributes = attrs
                }
            }
            if (activity?.requestedOrientation != initialOrientation) {
                try { activity?.requestedOrientation = initialOrientation } catch (_: Exception) {}
            }
        }
    }

    // ── 内部方法 ─────────────────────────────────────────────────────────────
    fun togglePlayPause() = playback.togglePlayPause()

    fun seekBackward() = playback.seekBackward()

    fun seekForward() = playback.seekForward()

    fun toggleMute() = playback.toggleMute()

    fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        val target = value.coerceIn(0f, 1f)
        val attrs = window.attributes
        // 与当前亮度差异过小则不写 window，避免手势拖动的每帧系统调用
        if (abs(attrs.screenBrightness - target) < 0.01f) return
        attrs.screenBrightness = target
        window.attributes = attrs
    }

    fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

    fun setLongPressBoost(active: Boolean) = playback.setLongPressBoost(active)

    fun updateLongPressBoostSpeed(speed: Float) = playback.updateLongPressBoostSpeed(speed)

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

    // 方向钉住是跨视频保留的用户选择：进入全屏时按当时朝向还原，否则切集后每集都要重新点一次。
    // 只在设备确实横置时钉：竖持时按显示旋转换算会选错边，与「记住上一次」的预期相反。
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
            // 手势中途上锁时不再有抬起事件，不清理会让预览图一直挂在画面中央
            seekPreview = null
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
    // 非全屏时页面钉在竖屏：不钉的话窗口会先随旋转重排成横屏，之后才被播放器全屏覆盖，
    // 表现为「整页转横 → 播放器再全屏」的两段过渡。窗口已是横屏时不钉（退出全屏仍横持的场景）。
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
        appPrefs.hints.markPlayerGestureIntroShown()
        showGestureIntro = false
    }

    // 进度预览取帧：拖动期间保留上一帧，避免每换一个位置都先闪成无图再补图
    LaunchedEffect(seekPreview, currentSourceUrl, activeLayout.showSeekPreview) {
        val target = seekPreview
        if (target == null) {
            previewFrame = null
            return@LaunchedEffect
        }
        if (!activeLayout.showSeekPreview) {
            previewFrame = null
            return@LaunchedEffect
        }
        delay(SEEK_PREVIEW_DEBOUNCE_MS)
        val bitmap = withContext(Dispatchers.IO) {
            PlayerFramePreview.frameAt(currentSourceUrl, target, appPrefs.antiHotlinkReferer)
        }
        previewFrame = bitmap?.asImageBitmap()
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
    longPressBoostSpeed = { longPressBoostSpeedRef.value },
    volumeState = playback.volume,
                syncVolume = { playback.syncVolume() },
                setVolume = { playback.setVolume(it) },
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
                showSpeedMenuState = showSpeedMenuState,
                showQualityMenuState = showQualityMenuState,
                showMoreMenuState = showMoreMenuState,
                seekBackward = { seekBackward() },
                seekForward = { seekForward() },
                togglePlayPause = { togglePlayPause() },
                setBrightness = { setBrightness(it) },
                setLongPressBoost = { setLongPressBoost(it) },
                setLongPressBoostSpeed = { updateLongPressBoostSpeed(it) },
                doubleTapSeekEnabled = { doubleTapSeekEnabledRef.value },
                playerLayout = { activeLayout }
            )
    ) {
        PlayerSurface(
            exoPlayer = exoPlayer,
            posterUrl = posterUrl,
            showPoster = showPoster,
            resizeMode = playerResizeMode,
            videoAspect = videoAspect,
            playerWidth = playerWidth,
            playerHeight = playerHeight,
            videoZoom = videoZoom,
            isFullscreen = isFullscreen
        )

        PlayerFeedbackLayer(
            exoPlayer = exoPlayer,
            isLongPressBoost = isLongPressBoost,
            boostSpeed = boostSpeed,
            showBoostSlideHint = showBoostSlideHint,
            gestureHint = gestureHint,
            showGestureIntro = showGestureIntro,
            isInPip = isInPip,
            sleepTimer = sleepTimer,
            isBuffering = isBuffering,
            playbackError = playbackError,
            isControlsVisible = isControlsVisible,
            relatedActive = relatedActive,
            activeLayout = activeLayout,
            layoutEditing = layoutEditing,
            videoZoom = videoZoom,
            onMarkControlTap = { markControlTap() },
            onRetry = { playback.retry() },
            onResetZoom = { videoZoom = 1f }
        )

        // ── 控件 UI（可见时）──────────────────────────────────────────────────
        if ((isControlsVisible || layoutEditing) && !isInPip && !relatedActive) {
            if (layoutEditing || (showNavButtons && activeLayout.showNavButtons)) {
                PlayerEditFrame(
                    editing = layoutEditing,
                    checked = activeLayout.showNavButtons,
                    label = stringResource(R.string.player_layout_show_nav_buttons),
                    onToggle = { layoutDraft = layoutDraft.withToggled(PlayerControlId.NAV_BUTTONS) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                ) {
                    PlayerNavButtons(
                        onBackClick = onBackClick,
                        onHomeClick = onNavigateHome,
                        tint = Color.White,
                        onControlTap = { markControlTap() }
                    )
                }
            }
            PlayerControls(
                exoPlayer = exoPlayer,
                layout = activeLayout,
                editing = layoutEditing,
                onToggleControl = { layoutDraft = layoutDraft.withToggled(it) },
                isFullscreen = isFullscreen,
                isInPip = isInPip,
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChanged = onAutoPlayNextChanged,
                isLoopPlayback = isLoopPlayback,
                onLoopPlaybackChanged = { enabled ->
                    onLoopPlaybackChanged(enabled)
                    if (enabled && sleepTimer.afterEpisode) sleepTimer = PlayerSleepTimer()
                },
                realtimeTranslationAvailable = realtimeTranslationAvailable,
                realtimeTranslation = realtimeTranslation,
                onRealtimeTranslationChanged = { appPrefs.setRealtimeTranslation(it) },
                subtitleLang = subtitleLang,
                subtitleSwitching = subtitleSwitching,
                onSubtitleLangChange = onSubtitleLangChange,
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
                onSeekPreview = { seekPreview = it },
                onTogglePlayPause = { togglePlayPause() },
                onSeekBackward = { seekBackward() },
                onSeekForward = { seekForward() },
                onToggleMute = { toggleMute() },
                onSetPlaybackSpeed = { setPlaybackSpeed(it) },
                onSwitchQuality = { source, userInitiated -> playback.switchQuality(source, userInitiated) },
                onToggleFullscreen = { toggleFullscreen() },
                sleepTimer = sleepTimer,
                resizeMode = playerResizeMode,
                onLock = { isLocked = true },
                isOrientationPinned = pinnedOrientation != null,
                onToggleOrientationPin = { toggleOrientationPin() },
                onSleepTimerChange = { timer ->
                    sleepTimer = timer
                    if (timer.afterEpisode && isLoopPlayback) onLoopPlaybackChanged(false)
                },
                onResizeModeChange = { appPrefs.setPlayerResizeMode(it) },
                onBuildPipParams = { buildPipParams() },
                onEditLayout = {
                    markControlTap()
                    layoutDraft = playerLayout
                    layoutEditing = true
                    isLocked = false
                    isControlsVisible = true
                    relatedPanelOpen = false
                    // 编辑面板需要横向空间与完整的控件带：竖屏播放器只有一条窄带，进去也没法点
                    if (!isFullscreen) onFullscreenToggle(true)
                }
            )
        }

        val previewPosition = seekPreview
        if (previewPosition != null && !isInPip && !relatedActive && activeLayout.showSeekPreview) {
            SeekPreviewOverlay(
                frame = previewFrame,
                text = "${formatTime(previewPosition)} / ${formatTime(exoPlayer.duration)}",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isLocked && isFullscreen && !isInPip && !relatedActive) {
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

        if (isFullscreen && !isInPip && !layoutEditing && relatedVideos.isNotEmpty() &&
            activeLayout.showRelatedList
        ) {
            if (relatedProgress > 0.02f) {
                // 空白处点击收回：遮罩铺满整块画面，卡片与收起按钮自行消费点击
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = relatedProgress }
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { relatedPanelOpen = false }
                )
            }
            RelatedPanel(
                videos = relatedVideos,
                progress = relatedProgress,
                onCollapse = { relatedPanelOpen = false },
                onVideoClick = {
                    relatedPanelOpen = false
                    onRelatedVideoClick(it)
                }
            )
            // 与其他控件同显隐：控件收起时拉出条一并隐藏，避免它常驻画面
            if (isControlsVisible) {
                RelatedPullPane(
                    video = relatedVideos.first(),
                    progress = relatedProgress,
                    playerWidth = { playerWidth },
                    onOpen = { relatedPanelOpen = true },
                    onDragBy = { delta ->
                        relatedDragProgress = ((relatedDragProgress ?: relatedSettled) + delta)
                            .coerceIn(0f, 1f)
                    },
                    onDragFinished = {
                        val target = relatedDragProgress ?: 0f
                        relatedDragProgress = null
                        relatedPanelOpen = target >= RELATED_OPEN_THRESHOLD
                    }
                )
            }
        }

        if (layoutEditing) {
            PlayerLayoutEditTitle(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            )
            PlayerLayoutEditPanel(
                layout = layoutDraft,
                onToggle = { layoutDraft = layoutDraft.withToggled(it) },
                onCycleTapAction = { layoutDraft = layoutDraft.withNextTapAction(it) },
                onLeftZonePercentChange = {
                    layoutDraft = PlayerLayout.sanitize(layoutDraft.copy(leftZonePercent = it))
                },
                onRightZonePercentChange = {
                    layoutDraft = PlayerLayout.sanitize(layoutDraft.copy(rightZonePercent = it))
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-12).dp)
                    .padding(horizontal = 12.dp)
            )
            PlayerLayoutEditBar(
                onReset = { layoutDraft = PlayerLayout.DEFAULT },
                onCancel = { layoutEditing = false },
                onFinish = {
                    appPrefs.setPlayerLayout(layoutDraft)
                    layoutEditing = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
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
