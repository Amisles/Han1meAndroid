package app.amisles.hanime.core.common.extension

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 解包出宿主 Activity。
 *
 * Compose 的 LocalContext 可能是 ContextThemeWrapper 之类的包装对象，直接强转会拿到 null，
 * 表现为「点击后什么也没发生」。
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
