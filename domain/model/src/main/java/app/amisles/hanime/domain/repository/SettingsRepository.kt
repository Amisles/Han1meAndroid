package app.amisles.hanime.domain.repository

/**
 * 设置的总入口：按域拆成五个窄接口，这里是它们的组合。
 *
 * 界面与 ViewModel 优先注入自己用得到的那一个（[PlayerSettings] / [DownloadSettings] /
 * [SiteSettings] / [AppearanceSettings] / [ReadingSettings]）；只有设置页这种需要横跨全部域的
 * 地方才用本接口 —— 注入它等于一次拿到全部设置项，也等于放弃接口隔离。
 *
 * 会话凭据（登录 Cookie）不在本接口，见 [SessionRepository]：二者的生命周期与安全要求不同，
 * 设置项可以随手改，凭据必须与签发域名绑定且能在登出时整体清除。
 */
interface SettingsRepository :
    PlayerSettings,
    DownloadSettings,
    SiteSettings,
    AppearanceSettings,
    ReadingSettings {

    /** 已提示过的崩溃记录文件名；为空表示尚未提示过任何崩溃。 */
    val acknowledgedCrashFile: String
    fun setAcknowledgedCrashFile(name: String)

    companion object {
        const val DEFAULT_BASE_URL = "https://hanime1.me"

        /** 详情页缓存默认保留条数；设置页的选项都落在这个区间内。 */
        const val DEFAULT_VIDEO_DETAIL_CACHE_LIMIT = 1000
        const val MIN_VIDEO_DETAIL_CACHE_LIMIT = 100
        const val MAX_VIDEO_DETAIL_CACHE_LIMIT = 2000

        const val LANGUAGE_ZH_CN = "zh-CN"
        const val LANGUAGE_ZH_TW = "zh-TW"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_JA = "ja"
    }
}
