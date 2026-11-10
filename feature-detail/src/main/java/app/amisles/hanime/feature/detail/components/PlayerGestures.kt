package app.amisles.hanime.feature.detail.components

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import app.amisles.hanime.core.ui.R
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

/** 长按触发加速的时长：短于它只会当作点击或滑动，避免误触把倍速改掉。 */
private const val LONG_PRESS_BOOST_DELAY_MS = 1000L

internal fun Modifier.playerGestures(
    onControlsVisibleChange: (Boolean) -> Unit,
    isControlsVisible: () -> Boolean,
    controlsActivityRef: MutableLongState,
    longPressBoostSpeed: () -> Float,
    volumeState: MutableFloatState,
    exoPlayer: ExoPlayer,
    density: Density,
    context: android.content.Context,
    menuOpen: () -> Boolean,
    isInPip: () -> Boolean,
    playerWidth: () -> Float,
    playerHeight: () -> Float,
    gestureScope: CoroutineScope,
    gestureHintState: MutableState<String?>,
    lastTapTimeState: MutableLongState,
    seekPreviewState: MutableLongState,
    brightnessState: MutableFloatState,
    videoZoomState: MutableFloatState,
    isControlTapState: MutableState<Boolean>,
    isMutedState: MutableState<Boolean>,
    showSpeedMenuState: MutableState<Boolean>,
    showQualityMenuState: MutableState<Boolean>,
    showMoreMenuState: MutableState<Boolean>,
    seekBackward: () -> Unit,
    seekForward: () -> Unit,
    togglePlayPause: () -> Unit,
    setBrightness: (Float) -> Unit,
    setLongPressBoost: (Boolean) -> Unit,
    setLongPressBoostSpeed: (Float) -> Unit,
    doubleTapSeekEnabled: () -> Boolean
): Modifier = this
    .pointerInput(Unit) {
        // 阈值按屏幕密度换算成像素：固定 8px 在高密度屏上只有约 3dp，远低于系统 touch slop，
        // 轻微抖动即可决定方向，表现为上下滑却触发快进。
        val gestureMath = PlayerGestureMath(with(density) { PlayerGestureMath.SLOP_DP.dp.toPx() })
        while (true) {
            awaitPointerEventScope {
                var pointerCount = 0
                var isZoom = false
                var dragMode: DragMode? = null
                var startX = 0f
                var lastX = 0f
                var lastY = 0f
                var seekStartPos = 0L
                var lastPinch = 0f
                var downX = 0f
                var downY = 0f
                var moved = false
                var initialized = false
                // 长按加速是否已触发：一旦触发，本段手势的滑动只调倍率，直到手指抬起
                var longPressFired = false
                // 长按加速定时器：按下启动 1s 延时协程，松手/缩放/成段拖动即取消，避免点击误触发加速
                var longPressJob: Job? = null
                while (true) {
                    val event = awaitPointerEvent()
                    val presses = event.changes.filter { it.pressed }
                    pointerCount = presses.size
                    if (pointerCount == 0) {
                        // 所有手指抬起：结束本次手势
                        if (longPressFired) {
                            // 长按加速结束：只有全部手指抬起才恢复常驻倍速 —— 加速期间滑动只调倍率，
                            // 不会因为「手滑了一点」提前退出，也不触发点击切换 / 快进退
                            longPressJob?.cancel()
                            setLongPressBoost(false)
                            longPressFired = false
                            gestureHintState.value = null
                            break
                        }
                        // 普通抬起：取消尚未到期的定时器，确保快速点击绝不误加速
                        longPressJob?.cancel()
                        // 浮层打开期间不处理点击：滑动浮层时父级未判定拖动，moved 仍为 false，
                        // 松手会被当成空白点击而隐藏控件，浮层随之关闭
                        if (!moved && !isZoom && !menuOpen()) {
                            // 视为一次点击
                            val now = System.currentTimeMillis()
                            if (now - lastTapTimeState.value < 300L) {
                                lastTapTimeState.value = 0L
                                if (isControlTapState.value) {
                                    // 第二击落在控件上：控件已自行处理，手势不再叠加分区动作
                                    isControlTapState.value = false
                                } else if (!isInPip()) {
                                    // 双击分区：播放器三等分，左快退 / 右快进 / 中间播放暂停
                                    val width = playerWidth()
                                    when {
                                        downX < width / 3f -> {
                                            // 关闭双击快进时：既不跳转也不切换控制栏，否则连点会让显隐抖动
                                            if (doubleTapSeekEnabled()) {
                                                seekBackward()
                                                gestureHintState.value = context.getString(R.string.player_rewind_15s)
                                            }
                                        }
                                        downX > width * 2f / 3f -> {
                                            if (doubleTapSeekEnabled()) {
                                                seekForward()
                                                gestureHintState.value = context.getString(R.string.player_forward_15s)
                                            }
                                        }
                                        else -> togglePlayPause()
                                    }
                                }
                            } else {
                                lastTapTimeState.value = now
                                if (isControlTapState.value) {
                                    // 单击落在控件上：交由控件自身处理，手势不切换控制栏显隐、不关菜单
                                    isControlTapState.value = false
                                    lastTapTimeState.value = 0L
                                } else {
                                    onControlsVisibleChange(!isControlsVisible())
                                    showSpeedMenuState.value = false
                                    showQualityMenuState.value = false
                                    showMoreMenuState.value = false
                                }
                            }
                        }
                        if (dragMode == DragMode.SEEK) {
                            exoPlayer.seekTo(seekPreviewState.value)
                        }
                        if (dragMode != null || isZoom) {
                            gestureHintState.value = null
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
                            showSpeedMenuState.value = false
                            showQualityMenuState.value = false
                        }
                        if (lastPinch > 0f && dist > 0f) {
                            val factor = dist / lastPinch
                            videoZoomState.value = (videoZoomState.value * factor).coerceIn(0.5f, 2.0f)
                            gestureHintState.value = context.getString(
                                R.string.player_zoom,
                                String.format(Locale.getDefault(), "%.1f", videoZoomState.value)
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
                            // 兜底复位：上一段手势若被系统接管（返回手势、权限弹窗）而没收到抬起事件，
                            // 加速会一直挂着。正常按下时该调用是空操作。
                            setLongPressBoost(false)
                            // 每段手势开始必须清零：gestureMath 跨手势复用，
                            // 残留的累计位移会让本次方向判定沿用上一次手势的结果
                            gestureMath.reset()
                            // 每次手势开始都清空「落在控件上」标记：控件 onClick 要到抬起才置位，
                            // 若上次手势残留该标记会吞掉下一次空白点击
                            isControlTapState.value = false
                            // 手势开始即重置自动隐藏倒计时
                            controlsActivityRef.value = controlsActivityRef.value + 1
                            // 启动长按加速定时器：单指按住满 1s 触发加速，松手 / 缩放 / 成段拖动即取消。
                            // 触发后进入「加速拖动」态：后续滑动只调倍率，不再切到进度 / 亮度 / 音量，
                            // 直到手指抬起 —— 见下方 dragMode 为 null 的分支。
                            longPressJob = gestureScope.launch {
                                delay(LONG_PRESS_BOOST_DELAY_MS)
                                if (!menuOpen() && !longPressFired && !isZoom && dragMode == null) {
                                    longPressFired = true
                                    setLongPressBoost(true)
                                    showSpeedMenuState.value = false
                                    showQualityMenuState.value = false
                                    // 不写 gestureHint：它 800ms 后会自动清除，而加速要持续到松手，
                                    // 倍率改由播放器依据 isLongPressBoost 常驻显示（见 VideoPlayer 的提示层）
                                }
                            }
                            if (!menuOpen()) {
                                event.changes.forEach { it.consume() }
                            }
                            continue
                        }
                        if (menuOpen()) {
                            // 浮层打开期间不判定拖动：滑动应由浮层自身消费
                            lastX = x
                            lastY = y
                            continue
                        }
                        if (dragMode == null) {
                            if (longPressFired) {
                                // 长按加速中：上下滑动直接换算目标倍率。必须在这里拦下 ——
                                // 交给方向判定会把轻微滑动判成亮度 / 音量，加速随之被切走，
                                // 与「松手才退出加速」的预期相反。
                                setLongPressBoostSpeed(
                                    gestureMath.boostSpeed(
                                        baseSpeed = longPressBoostSpeed(),
                                        downY = downY,
                                        currentY = y,
                                        playerHeight = playerHeight()
                                    )
                                )
                            } else {
                                val mode = gestureMath.onMove(
                                    x = x, y = y, lastX = lastX, lastY = lastY,
                                    downX = downX, playerWidth = playerWidth()
                                )
                                if (mode != null) {
                                    moved = true
                                    // 长按加速定时器未到期时，轻微横移不判成拖动进度：留给定时器裁决，
                                    // 避免长按时手指稍稍左右漂移就把加速误切成进度调整
                                    val boostPendingDrift = mode == DragMode.SEEK &&
                                        longPressJob?.isActive == true &&
                                        gestureMath.isSlightSeekDrift(currentX = x, downX = downX)
                                    if (!boostPendingDrift) {
                                        longPressJob?.cancel()
                                        startX = x
                                        showSpeedMenuState.value = false
                                        showQualityMenuState.value = false
                                        seekStartPos = exoPlayer.currentPosition
                                        seekPreviewState.value = seekStartPos
                                        dragMode = mode
                                    }
                                }
                            }
                        } else {
                            when (dragMode) {
                                DragMode.SEEK -> {
                                    val dur = exoPlayer.duration
                                    seekPreviewState.value = gestureMath.seekTarget(
                                        startPosition = seekStartPos,
                                        startX = startX,
                                        currentX = x,
                                        playerWidth = playerWidth(),
                                        duration = dur
                                    )
                                    if (dur > 0) {
                                        gestureHintState.value = "${formatTime(seekPreviewState.value)} / ${formatTime(dur)}"
                                    }
                                }
                                DragMode.BRIGHTNESS -> {
                                    if (playerHeight() > 0f) {
                                        val delta = gestureMath.verticalDelta(lastY = lastY, y = y, playerHeight = playerHeight())
                                        brightnessState.value = (brightnessState.value + delta).coerceIn(0f, 1f)
                                        setBrightness(brightnessState.value)
                                        gestureHintState.value = context.getString(R.string.player_brightness, (brightnessState.value * 100).toInt())
                                    }
                                }
                                DragMode.VOLUME -> {
                                    if (playerHeight() > 0f) {
                                        val delta = gestureMath.verticalDelta(lastY = lastY, y = y, playerHeight = playerHeight())
                                        volumeState.value = (volumeState.value + delta).coerceIn(0f, 1f)
                                        exoPlayer.volume = volumeState.value
                                        isMutedState.value = volumeState.value == 0f
                                        gestureHintState.value = context.getString(R.string.player_volume, (volumeState.value * 100).toInt())
                                    }
                                }
                            }
                        }
                        lastX = x
                        lastY = y
                    }

                    // 浮层打开时不接管手势：弹层是本 Box 的子节点，父级先消费会让
                    // 子级的垂直滚动收不到未消费事件，表现为浮层无法上下滑动。
                    if (!menuOpen()) {
                        // 消费事件，避免父级（如页面/列表）误响应本播放器手势
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
