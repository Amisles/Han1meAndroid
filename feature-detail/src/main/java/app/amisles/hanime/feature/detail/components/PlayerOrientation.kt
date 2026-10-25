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

/**
 * 把 [android.view.OrientationEventListener] 角度归类为物理朝向。
 *
 * 判定区间刻意收窄、缓冲带留宽：原来横屏区 ±35°、竖屏区 ±35°，手机只要稍微偏一点就跨过边界，
 * 表现为横屏播放时轻微竖起就退出全屏。现在横屏只在 ±30° 内成立，竖屏只在 ±20° 内成立，
 * 中间 20° 的缓冲区一律返回 null（维持原判定），给后续的时间防抖留出空间。
 */
internal fun classifyDeviceTilt(orientation: Int): DeviceTilt? = when (orientation) {
    in 60..120 -> DeviceTilt.TOP_TO_RIGHT
    in 240..300 -> DeviceTilt.TOP_TO_LEFT
    in 0..20, in 340..359 -> DeviceTilt.PORTRAIT
    in 160..200 -> DeviceTilt.PORTRAIT
    else -> null
}

/** 朝向翻转需持续稳定的时长；短于它视为手持抖动，不触发全屏进出。 */
internal const val FLIP_DEBOUNCE_MS = 600L

/** 是否为「锁定横屏」方向常量；全屏是本应用唯一锁方向处，快照到这些值不能作为退出全屏的恢复目标。 */
internal val Int.isLandscapeOrientation: Boolean
    get() = this == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        || this == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
