package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.preferences.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用设置与站点配置。
 *
 * 原先这些成员散落在 `data.preferences.Preferences` 这个 47 个成员的 object 上，
 * 上层（feature-* 与 app）直接 import 了具体实现。拆出来后：
 * - 界面只依赖本接口，SettingsScreen 的预览与 MockData 不再需要真实的加密存储；
 * - 「站点基址」这种会影响 Cookie 注入与防盗链 Referer 的敏感项有了明确的归属。
 *
 * 会话凭据（登录 Cookie）**不在本接口**，见 [SessionRepository]：二者的生命周期与
 * 安全要求不同 —— 设置项可以随手改，会话凭据必须与来源域名绑定、且可在登出时整体清除。
 */
interface SettingsRepository {
    // ── 语言与主题 ──────────────────────────────────────────────────────────
    val appLanguage: String
    val appLanguageFlow: StateFlow<String>
    fun setAppLanguage(lang: String)

    val themeMode: ThemeMode
    val themeModeFlow: StateFlow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)

    // ── 站点 ────────────────────────────────────────────────────────────────
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

    // ── 播放 ────────────────────────────────────────────────────────────────
    val playbackSpeed: Float
    val playbackSpeedFlow: StateFlow<Float>
    fun setPlaybackSpeed(speed: Float)

    val preferredQuality: String
    val preferredQualityFlow: StateFlow<String>
    fun setPreferredQuality(resolution: String)

    val autoPlayNext: Boolean
    val autoPlayNextFlow: StateFlow<Boolean>
    fun setAutoPlayNext(enabled: Boolean)

    val loopPlayback: Boolean
    val loopPlaybackFlow: StateFlow<Boolean>
    fun setLoopPlayback(enabled: Boolean)

    /** 长按临时加速的目标倍速（1.5–3.0）。只影响按住期间，不写回 [playbackSpeed]。 */
    val longPressBoostSpeed: Float
    val longPressBoostSpeedFlow: StateFlow<Float>
    fun setLongPressBoostSpeed(speed: Float)

    /** 双击播放器左右区域快退 / 快进。 */
    val doubleTapSeek: Boolean
    val doubleTapSeekFlow: StateFlow<Boolean>
    fun setDoubleTapSeek(enabled: Boolean)

    /** 卡顿时自动降档、稳定后自动回升。关闭后手动选择的画质不会被自动切换覆盖。 */
    val autoQuality: Boolean
    val autoQualityFlow: StateFlow<Boolean>
    fun setAutoQuality(enabled: Boolean)

    // ── 下载 ────────────────────────────────────────────────────────────────
    val maxDownloadConcurrent: Int
    val maxDownloadConcurrentFlow: StateFlow<Int>
    fun setMaxDownloadConcurrent(max: Int)

    /** 全局下载限速（MB/s），0 表示不限速。所有任务共享同一份带宽预算。 */
    val downloadRateLimit: Int
    val downloadRateLimitFlow: StateFlow<Int>
    fun setDownloadRateLimit(megabytesPerSecond: Int)

    /** 订阅更新提醒总开关，默认关闭：定时抓页面会带来站点限流风险。 */
    val subscriptionReminder: Boolean
    val subscriptionReminderFlow: StateFlow<Boolean>
    fun setSubscriptionReminder(enabled: Boolean)

    /** 已通知过的订阅视频 id 快照，用于判断「哪些是新片」。 */
    val subscriptionSeenVideoIds: Set<String>
    fun setSubscriptionSeenVideoIds(ids: Set<String>)

    /** 空串表示默认目录，由下载子系统解析。 */
    var downloadStoragePath: String
    val downloadStoragePathFlow: StateFlow<String>

    // ── 搜索 ────────────────────────────────────────────────────────────────
    /** null 表示用户从未显式选择过，界面按窗口宽度档位取默认值。 */
    val searchLayoutMode: SearchLayoutMode?
    val searchLayoutModeFlow: StateFlow<SearchLayoutMode?>
    fun setSearchLayoutMode(mode: SearchLayoutMode)

    // ── 统计 ────────────────────────────────────────────────────────────────
    /** 观看统计开关。关闭后不展示入口，也不做任何统计计算。 */
    val watchStatsEnabled: Boolean
    val watchStatsEnabledFlow: StateFlow<Boolean>
    fun setWatchStatsEnabled(enabled: Boolean)

    companion object {
        const val DEFAULT_BASE_URL = "https://hanime1.me"

        const val LANGUAGE_ZH_CN = "zh-CN"
        const val LANGUAGE_ZH_TW = "zh-TW"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_JA = "ja"
    }
}
