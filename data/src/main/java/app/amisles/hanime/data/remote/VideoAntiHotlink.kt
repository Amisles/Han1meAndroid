package app.amisles.hanime.data.remote

import app.amisles.hanime.domain.remote.VideoAntiHotlink as DomainVideoAntiHotlink
import app.amisles.hanime.domain.repository.SiteSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 防盗链 Referer 的配置读取。
 *
 * 常量与格式转换已下沉到 domain（[DomainVideoAntiHotlink]），这里只负责
 * 「当前该注入哪个值」—— 它依赖用户的站点设置，属于数据层职责。
 */
@Singleton
class VideoAntiHotlinkProvider @Inject constructor(
    private val site: SiteSettings
) {

    /** 当前应注入的 Referer 值（设置页官网网址，结尾带 "/"）。 */
    val referer: String
        get() = DomainVideoAntiHotlink.refererOf(site.baseUrl)
}
