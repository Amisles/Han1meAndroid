package app.amisles.hanime.feature.detail.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
internal fun PlayerFullscreenEffect(
    isFullscreen: Boolean,
    activity: Activity?,
    deviceTilt: DeviceTilt?,
    initialOrientation: Int,
    initialBarsBehavior: Int
) {
    LaunchedEffect(isFullscreen, activity, deviceTilt) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                val target = deviceTilt?.landscapeOrientation
                if (target != null) {
                    try { activity.requestedOrientation = target } catch (_: Exception) {}
                } else if (!activity.requestedOrientation.isLandscapeOrientation) {
                    // 朝向未知或设备竖持（不存在「正确的一侧」）：交给系统的横屏传感器选一个
                    try {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } catch (_: Exception) {}
                }
                // 其余情况：已锁横屏却判到竖屏，多半是左右横置翻转途经竖屏，保持方向不动，待去抖后退出全屏
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = initialBarsBehavior
                try { activity.requestedOrientation = initialOrientation } catch (_: Exception) {}
            }
        }
    }
}

@Composable
internal fun PlayerOrientationWatcher(
    activity: Activity?,
    autoFullscreenEnabled: Boolean,
    isInPip: Boolean,
    isFullscreen: Boolean,
    onFullscreenToggle: (Boolean) -> Unit,
    onDeviceTiltChange: (DeviceTilt) -> Unit
) {
    val orientationFlipRef = rememberUpdatedState { landscape: Boolean ->
        if (autoFullscreenEnabled && !isInPip) {
            if (landscape && !isFullscreen) onFullscreenToggle(true)
            else if (!landscape && isFullscreen) onFullscreenToggle(false)
        }
    }

    // 监听器始终注册（不随 autoFullscreenEnabled 走）：平板分栏或用户在设置里关掉「旋转自动全屏」
    // 时不自动进出全屏，但手动点全屏同样需要 deviceTilt 才能锁到正确一侧，
    // 是否触发自动全屏由上面的 ref 内部判断。
    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose {}
        // 上一次已生效的方向（null = 尚未登记），仅在真正翻转时上报
        var lastLandscape: Boolean? = null
        var candidateLandscape: Boolean? = null
        var candidateSince = 0L
        // 竖屏待确认次数：左右横置互翻必经竖屏，而全屏时 SENSOR_LANDSCAPE 不会让画面经过竖屏，
        // 若一次判到竖屏就退出会「退出全屏又立刻重进」，故竖屏需连续两次判定才生效
        var portraitStreak = 0
        // 上一次真正落地判定时的物理朝向。OrientationEventListener 在一般设备上会以
        // 传感器采样率持续回调（数十次/秒），原实现每次回调都写 deviceTilt 这个 Compose State，
        // 即使值不变也会走一次快照写入与失效检查。这里先与已生效值比较，只有真正变化才写入。
        var appliedTilt: DeviceTilt? = null
        val listener = object : OrientationEventListener(act) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                // 临界区返回 null：维持原判定，等角度稳定后再判
                val tilt = classifyDeviceTilt(orientation) ?: return
                // 物理朝向始终同步（左右翻转也要更新，供全屏锁方向用）。
                // 先比对已生效值：朝向未变时不写 State，避免高频回调反复触发重组检查。
                if (tilt != appliedTilt) {
                    appliedTilt = tilt
                    onDeviceTiltChange(tilt)
                }
                val landscape = tilt.isLandscape
                val previous = lastLandscape
                if (previous == landscape) {
                    portraitStreak = 0
                    candidateLandscape = null
                    return
                }
                val now = System.currentTimeMillis()
                if (candidateLandscape != landscape) {
                    candidateLandscape = landscape
                    candidateSince = now
                    return
                }
                if (now - candidateSince < FLIP_DEBOUNCE_MS) return
                candidateLandscape = null
                if (!landscape && previous != null) {
                    portraitStreak++
                    if (portraitStreak < 2) return
                }
                portraitStreak = 0
                lastLandscape = landscape
                // 首次回调只用于登记当前朝向：起始竖屏无需动作，但起始横屏（横持手机进入详情页）仍应自动全屏
                if (previous == null && !landscape) return
                orientationFlipRef.value(landscape)
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
}

@Composable
internal fun PlayerPipEffect(
    activity: Activity?,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    buildPipParams: () -> PictureInPictureParams,
    onPipChange: (Boolean) -> Unit
) {
    val isFullscreenRef = rememberUpdatedState(isFullscreen)
    val isPlayingRef = rememberUpdatedState(isPlaying)
    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose {}
        // Activity 本身没有 lifecycle；LifecycleOwner 由 ComponentActivity 实现，安全向下转型后即可观察
        val owner = act as? LifecycleOwner ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (isFullscreenRef.value && isPlayingRef.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && act.isInPictureInPictureMode != true) {
                        act.enterPictureInPictureMode(buildPipParams())
                        onPipChange(true)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    onPipChange(false)
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
