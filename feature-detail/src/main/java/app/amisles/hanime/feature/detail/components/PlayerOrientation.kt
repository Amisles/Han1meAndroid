package app.amisles.hanime.feature.detail.components

import android.content.pm.ActivityInfo

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

/** 把 [android.view.OrientationEventListener] 角度归类为物理朝向；临界区（近乎平放/斜持）返回 null 待后续判定，避免抖动。 */
internal fun classifyDeviceTilt(orientation: Int): DeviceTilt? = when (orientation) {
    in 55..125 -> DeviceTilt.TOP_TO_RIGHT
    in 235..305 -> DeviceTilt.TOP_TO_LEFT
    in 0..35, in 325..359 -> DeviceTilt.PORTRAIT
    in 145..215 -> DeviceTilt.PORTRAIT
    else -> null
}

/** 是否为「锁定横屏」方向常量；全屏是本应用唯一锁方向处，快照到这些值不能作为退出全屏的恢复目标。 */
internal val Int.isLandscapeOrientation: Boolean
    get() = this == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
