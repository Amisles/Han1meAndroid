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

internal const val FLIP_DEBOUNCE_MS = 600L

/** 是否为「锁定横屏」方向常量；全屏是本应用唯一锁方向处，快照到这些值不能作为退出全屏的恢复目标。 */
internal val Int.isLandscapeOrientation: Boolean
    get() = this == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE

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
