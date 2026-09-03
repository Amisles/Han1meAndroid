package app.amisles.hanime.data.remote

import app.amisles.hanime.data.preferences.Preferences

object VideoAntiHotlink {

    /** 防盗链请求头名称。 */
    const val REFERER_HEADER = "Referer"

    /**
     * 当前应注入的 Referer 值（设置页官网网址，结尾带 "/"）。
     * 读取失败（如偏好未初始化）时回退到 [Preferences.DEFAULT_BASE_URL]。
     */
    val referer: String
        get() = runCatching { Preferences.baseUrl }
            .getOrDefault(Preferences.DEFAULT_BASE_URL)
            .let { if (it.endsWith("/")) it else "$it/" }
}
