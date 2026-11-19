package app.amisles.hanime.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * 站点与订阅设置。
 *
 * 与 [SessionRepository] 分开的理由见后者：站点基址会决定 Cookie 注入与防盗链 Referer 的域，
 * 但它本身不是凭据，登出时也不应被清除。
 */
interface SiteSettings {
    /** 站点基址，已完成规范化（https + host，去掉路径）。 */
    val baseUrl: String
    val baseUrlFlow: StateFlow<String>

    /**
     * 设置站点基址。留空表示恢复默认地址。
     *
     * @return true 表示输入被接受（可能已规范化）；false 表示非法，原设置不变。
     */
    fun setBaseUrl(url: String): Boolean

    /** 当前站点是否支持应用内登录（官方域名）。镜像站登录接口不可用，界面需提前拦截。 */
    val isLoginSupported: Boolean
    val loginSupportedFlow: StateFlow<Boolean>

    /** 订阅更新提醒总开关，默认关闭：定时抓页面会带来站点限流风险。 */
    val subscriptionReminder: Boolean
    val subscriptionReminderFlow: StateFlow<Boolean>
    fun setSubscriptionReminder(enabled: Boolean)

    /** 已通知过的订阅视频 id 快照，用于判断「哪些是新片」。 */
    val subscriptionSeenVideoIds: Set<String>
    fun setSubscriptionSeenVideoIds(ids: Set<String>)
}
