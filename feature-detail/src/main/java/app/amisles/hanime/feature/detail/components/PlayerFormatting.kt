package app.amisles.hanime.feature.detail.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 从包装过的 Context 里取出宿主 Activity；取不到时返回 null（如 Application context）。 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 倍速去掉多余的小数位：3.0 → "3"，3.5 → "3.5"。 */
internal fun trimSpeedText(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

/** 毫秒转 mm:ss / hh:mm:ss；按 Locale.getDefault() 格式化以保持各语言的数字形状。 */
internal fun formatTime(ms: Long): String {
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
