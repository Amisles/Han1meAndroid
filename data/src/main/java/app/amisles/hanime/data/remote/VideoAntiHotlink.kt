package app.amisles.hanime.data.remote

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.remote.VideoAntiHotlink as DomainVideoAntiHotlink

/**
 * 防盗链 Referer 的配置读取。
 *
 * 常量与格式转换已下沉到 domain（[DomainVideoAntiHotlink]），这里只负责
 * 「当前该注入哪个值」—— 它依赖用户的站点设置，属于数据层职责。
 */
object VideoAntiHotlink {

    /** 防盗链请求头名称（转发 domain 常量，避免两处定义漂移）。 */
    const val REFERER_HEADER: String = DomainVideoAntiHotlink.HEADER_NAME

    /**
     * 当前应注入的 Referer 值（设置页官网网址，结尾带 "/"）。
     * 读取失败时回退到 [Preferences.DEFAULT_BASE_URL]。
     */
    val referer: String
        get() = DomainVideoAntiHotlink.refererOf(
            runCatching { Preferences.baseUrl }.getOrDefault(Preferences.DEFAULT_BASE_URL)
        )
}
