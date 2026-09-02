package app.amisles.hanime.feature.detail

import app.amisles.hanime.data.preferences.Preferences

/**
 * 视频反防盗链机制模块。
 *
 * 部分视频 CDN 对直链做 Referer 防盗链校验：仅接受来自官网域名的 Referer，
 * 否则返回 403 导致播放失败。本模块集中提供防盗链所需的 Referer 请求头，
 * 供播放器（[ExoPlayerFactory]）在请求视频直链时注入。
 *
 * Referer 内容取自设置页「官网网址」（[Preferences.baseUrl]），即用户当前选定的官方站点。
 * baseUrl 经 [Preferences] 清洗后仅含协议+域名、不含路径，这里补一个结尾 "/"
 * 以符合官网根路径 Referer 的常规形态（例如 https://www.hanime163.com/）。
 */
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
