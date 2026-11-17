package app.amisles.hanime.feature.detail.components

import android.content.pm.ActivityInfo
import android.view.Surface

/** 设备物理朝向，用于全屏时锁到正确的横屏一侧（角度→方向常量勿写反，否则全屏持续上下颠倒）。 */
internal enum class DeviceTilt(val landscapeOrientation: Int?) {
    /** 竖持。 */
    PORTRAIT(null),

    /** 横置、顶部朝左。 */
    TOP_TO_LEFT(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),

    /** 横置、顶部朝右。 */
    TOP_TO_RIGHT(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

    val isLandscape: Boolean get() = landscapeOrientation != null
}

internal fun classifyDeviceTilt(orientation: Int): DeviceTilt? = when (orientation) {
    in 60..120 -> DeviceTilt.TOP_TO_RIGHT
    in 240..300 -> DeviceTilt.TOP_TO_LEFT
    in 0..20, in 340..359 -> DeviceTilt.PORTRAIT
    in 160..200 -> DeviceTilt.PORTRAIT
    else -> null
}

// 进入全屏的确认窗口。页面此时仍锁在竖屏，等待只表现为迟滞，故取短值。
internal const val ENTER_FULLSCREEN_DELAY_MS = 250L

// 退出全屏的确认窗口。左右横置互翻必然途经竖屏，窗口过短会被误判为「已转回竖屏」而反复退出。
internal const val EXIT_FULLSCREEN_DELAY_MS = 600L

/** 是否为「锁定横屏」方向常量；全屏时用它把画面钉在横屏某一侧。 */
internal val Int.isLandscapeOrientation: Boolean
    get() = this == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

/**
 * 是否为播放器自己下发的方向锁（全屏锁横屏一侧、非全屏锁竖屏）。
 *
 * 这类值不能当作「页面原本的方向」快照下来：换集时新旧播放器会短暂并存，新实例若把前一个
 * 实例留下的方向锁当成恢复目标，播放器离场后整个应用会一直钉在那个方向上。
 */
internal val Int.isPlayerOrientationLock: Boolean
    get() = isLandscapeOrientation || this == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

// 钉住必须是固定的一侧：物理朝向不可用时按显示旋转换算，回退成 SENSOR_LANDSCAPE 等于没钉住。
internal fun pinnedLandscapeOrientation(
    tilt: DeviceTilt?,
    displayRotation: Int,
    naturalLandscape: Boolean
): Int = tilt?.landscapeOrientation ?: when {
    naturalLandscape -> if (displayRotation == Surface.ROTATION_180) {
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    displayRotation == Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}
