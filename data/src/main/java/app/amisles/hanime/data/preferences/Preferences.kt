package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.model.SessionCookie
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.preferences.ThemeMode
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager
import androidx.core.content.edit
import app.amisles.hanime.data.cookie.CookieString
import app.amisles.hanime.data.cookie.HCookieJar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.LocaleHelper
import java.io.File

object Preferences : SettingsRepository, SessionRepository {

    // 与 Application 同生命周期的作用域，避免直接使用被标记为 delicate 的 GlobalScope
    private val preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val NAME = "hanime_app_prefs"

    private const val SP_ALREADY_LOGIN = "already_login"
    private const val SP_LOGIN_COOKIE = "login_cookie"
    private const val SP_CF_COOKIE = "cloudflare_cookie"
    private const val SP_CF_COOKIE_DOMAIN = "cloudflare_cookie_domain"
    /** 登录态 Cookie 的来源域名（小写 host）。会话只能注入回该域名，见 [saveLogin]。 */
    private const val SP_LOGIN_COOKIE_DOMAIN = "login_cookie_domain"
    /** 明文→加密迁移已完成的标记。写进加密存储，避免每次启动都重新做明文探测。 */
    private const val SP_MIGRATED_TO_ENCRYPTED = "migrated_to_encrypted"
    private const val SP_SAVED_USER_ID = "saved_user_id"
    private const val SP_MAX_DOWNLOAD_CONCURRENT = "max_download_concurrent"
    private const val SP_BASE_URL = "base_url"
    private const val SP_APP_LANGUAGE = "app_language"
    private const val SP_THEME_MODE = "theme_mode"
    private const val SP_PLAYBACK_SPEED = "playback_speed"
    private const val SP_PREFERRED_QUALITY = "preferred_quality"
    private const val SP_AUTO_PLAY_NEXT = "auto_play_next"
    private const val SP_LOOP_PLAYBACK = "loop_playback"
    private const val SP_LONG_PRESS_SPEED = "long_press_boost_speed"
    private const val SP_DOUBLE_TAP_SEEK = "double_tap_seek"
    private const val SP_AUTO_QUALITY = "auto_quality"
    private const val SP_DOWNLOAD_STORAGE_PATH = "download_storage_path"
    private const val SP_DOWNLOAD_RATE_LIMIT = "download_rate_limit_mbps"
    private const val SP_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
    private const val SP_DOWNLOAD_CHARGING_ONLY = "download_charging_only"
    private const val SP_DOWNLOAD_NIGHT_ONLY = "download_night_only"
    private const val SP_DOWNLOAD_QUEUE_ORDER = "download_queue_order"
    private const val SP_ACKNOWLEDGED_CRASH = "acknowledged_crash_file"
    private const val SP_SUBSCRIPTION_REMINDER = "subscription_reminder_enabled"
    private const val SP_SUBSCRIPTION_SEEN_IDS = "subscription_seen_video_ids"
    private const val SP_WATCH_STATS_ENABLED = "watch_stats_enabled"
    private const val SP_SEARCH_LAYOUT_MODE = "search_layout_mode"
private const val SP_RELATED_LAYOUT_MODE = "related_layout_mode"
    /** 用户选择「不再提示」的版本号；空串表示没有跳过任何版本。 */
    private const val SP_SKIPPED_UPDATE_VERSION = "skipped_update_version"
    /** 是否允许启动时自动检查更新。 */
    private const val SP_AUTO_CHECK_UPDATE = "auto_check_update"

    const val DEFAULT_BASE_URL = "https://hanime1.me"

    // 播放器相关取值范围：倍速档位与长按加速共用同一上下界，避免出现 3x 档位却被写回 2x
    const val MIN_PLAYBACK_SPEED = 0.25f
    const val MAX_PLAYBACK_SPEED = 3f
    const val DEFAULT_LONG_PRESS_SPEED = 2f
    const val MIN_LONG_PRESS_SPEED = 1.5f
    const val MAX_LONG_PRESS_SPEED = 3f

    // 基址校验：只接受 https + 形如域名/IP 的 host（可带端口）。视频防盗链 Referer 与登录 Cookie
    // 都会发往该地址，放开 http 会让它们在网络中以明文传输。
    private val HTTPS_PREFIX = Regex("^https://([^/]+)", RegexOption.IGNORE_CASE)
    private val HOST_PATTERN = Regex(
        "^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*(:\\d{1,5})?$"
    )

    // 支持登录的官方域名（其余镜像站登录接口返回“站点维护中”）
    private val LOGIN_SUPPORTED_DOMAINS = setOf("hanime1.me", "hanimeone.me")
    const val LANGUAGE_ZH_CN = "zh-CN"
    const val LANGUAGE_ZH_TW = "zh-TW"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_JA = "ja"
    val SUPPORTED_LANGUAGES = listOf(LANGUAGE_ZH_CN, LANGUAGE_ZH_TW, LANGUAGE_EN, LANGUAGE_JA)

    private lateinit var sp: android.content.SharedPreferences

    /**
     * 加密存储是否可用。
     *
     * 不可用时回退明文存储，但**不再持久化任何会话凭据**（见 [saveLogin] / [saveCloudFlareCookie]）：
     * 明文落盘的登录 Cookie 可被同设备上任意应用或 root 读取，其代价高于「每次启动需重新登录」。
     */
    @Volatile
    private var secureStorageAvailable = true

    /** init 幂等标记：Application.attachBaseContext 与 onCreate 都会调用，只允许真正初始化一次。 */
    @Volatile
    private var initialized = false

    private val _loginStateFlow = MutableStateFlow(false)
    override val loginStateFlow: StateFlow<Boolean> = _loginStateFlow.asStateFlow()

    private val _loginCookieFlow = MutableStateFlow(SessionCookie(""))
    // 登录态 Cookie 的来源域名。历史版本只存 Cookie 不存域名，启动时按 baseUrl 推断一次。
    @Volatile
    private var _loginCookieDomain: String = ""
    override val loginCookieFlow: StateFlow<CookieString> = _loginCookieFlow.asStateFlow()

    private val _cloudFlareCookieFlow = MutableStateFlow(SessionCookie(""))
    val cloudFlareCookieFlow: StateFlow<CookieString> = _cloudFlareCookieFlow.asStateFlow()

    /** 人机校验 Cookie 自己的来源域名；与登录态解耦，未登录的用户也要能带上它。 */
    @Volatile
    private var _cloudFlareCookieDomain: String = ""

    private val _savedUserIdFlow = MutableStateFlow("")
    override val savedUserIdFlow: StateFlow<String> = _savedUserIdFlow.asStateFlow()

    private val _maxDownloadConcurrentFlow = MutableStateFlow(3)
    override val maxDownloadConcurrentFlow: StateFlow<Int> = _maxDownloadConcurrentFlow.asStateFlow()

    private val _downloadRateLimitFlow = MutableStateFlow(0)
    override val downloadRateLimitFlow: StateFlow<Int> = _downloadRateLimitFlow.asStateFlow()

    private val _downloadWifiOnlyFlow = MutableStateFlow(false)
    override val downloadWifiOnlyFlow: StateFlow<Boolean> = _downloadWifiOnlyFlow.asStateFlow()

    private val _downloadChargingOnlyFlow = MutableStateFlow(false)
    override val downloadChargingOnlyFlow: StateFlow<Boolean> = _downloadChargingOnlyFlow.asStateFlow()

    private val _downloadNightOnlyFlow = MutableStateFlow(false)
    override val downloadNightOnlyFlow: StateFlow<Boolean> = _downloadNightOnlyFlow.asStateFlow()

    private val _subscriptionReminderFlow = MutableStateFlow(false)
    override val subscriptionReminderFlow: StateFlow<Boolean> = _subscriptionReminderFlow.asStateFlow()

    private val _watchStatsEnabledFlow = MutableStateFlow(true)
    override val watchStatsEnabledFlow: StateFlow<Boolean> = _watchStatsEnabledFlow.asStateFlow()

    private val _baseUrlFlow = MutableStateFlow(DEFAULT_BASE_URL)
    override val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    private val _appLanguageFlow = MutableStateFlow(LANGUAGE_ZH_CN)
    override val appLanguageFlow: StateFlow<String> = _appLanguageFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _playbackSpeedFlow = MutableStateFlow(1f)
    override val playbackSpeedFlow: StateFlow<Float> = _playbackSpeedFlow.asStateFlow()

    private val _preferredQualityFlow = MutableStateFlow("")
    override val preferredQualityFlow: StateFlow<String> = _preferredQualityFlow.asStateFlow()

    private val _autoPlayNextFlow = MutableStateFlow(true)
    override val autoPlayNextFlow: StateFlow<Boolean> = _autoPlayNextFlow.asStateFlow()

    // 循环播放：开启后当前视频播完自动从头重播（由 player.repeatMode 实现）
    private val _loopPlaybackFlow = MutableStateFlow(false)
    override val loopPlaybackFlow: StateFlow<Boolean> = _loopPlaybackFlow.asStateFlow()

    // 长按临时加速倍率：只影响按住期间，不覆盖用户选择的常驻倍速
    private val _longPressBoostSpeedFlow = MutableStateFlow(DEFAULT_LONG_PRESS_SPEED)
    override val longPressBoostSpeedFlow: StateFlow<Float> = _longPressBoostSpeedFlow.asStateFlow()

    private val _doubleTapSeekFlow = MutableStateFlow(true)
    override val doubleTapSeekFlow: StateFlow<Boolean> = _doubleTapSeekFlow.asStateFlow()

    private val _autoQualityFlow = MutableStateFlow(true)
    override val autoQualityFlow: StateFlow<Boolean> = _autoQualityFlow.asStateFlow()


    // 下载存储路径：空串表示使用默认目录（应用外部存储 /Downloads，不可用时回退内部存储）
    private val _downloadStoragePathFlow = MutableStateFlow("")
    override val downloadStoragePathFlow: StateFlow<String> = _downloadStoragePathFlow.asStateFlow()

    // 搜索页布局模式：null 表示用户从未显式选择过，由界面按窗口宽度档位取默认值
    private val _searchLayoutModeFlow = MutableStateFlow<SearchLayoutMode?>(null)
    override val searchLayoutModeFlow: StateFlow<SearchLayoutMode?> = _searchLayoutModeFlow.asStateFlow()

    // 详情页推荐布局：null 表示未选择，由推荐卡片形态决定默认值
    private val _relatedLayoutModeFlow = MutableStateFlow<RelatedLayoutMode?>(null)
    override val relatedLayoutModeFlow: StateFlow<RelatedLayoutMode?> = _relatedLayoutModeFlow.asStateFlow()

    // ── 应用更新 ────────────────────────────────────────────────────────────
    // 「不再提示」记录的是版本号而非时间戳：跳过 1.6.0 后，1.7.0 发布时仍会重新提示。
    private val _skippedUpdateVersionFlow = MutableStateFlow("")
    val skippedUpdateVersionFlow: StateFlow<String> = _skippedUpdateVersionFlow.asStateFlow()

    private val _autoCheckUpdateFlow = MutableStateFlow(true)
    val autoCheckUpdateEnabledFlow: StateFlow<Boolean> = _autoCheckUpdateFlow.asStateFlow()

    /**
     * 初始化偏好存储。幂等，可安全重复调用。
     *
     * 调用点：`HanimeApplication.attachBaseContext`（最早，Application 级语言包装需要读到持久化语言）
     * 与 `HanimeApplication.onCreate`（兜底）。刻意不做异步化 —— Activity 的 attachBaseContext 需要
     * 同步拿到语言/主题，异步初始化会让首帧用错语言与主题。
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        // 先保证 sp 一定被赋值：任何异常都不能让后续访问落到 lateinit 未初始化崩溃上
        sp = runCatching { provideSecurePreferences(context) }.getOrElse { e ->
            AppLogger.logError("Preferences", "偏好存储初始化失败: ${e.message}", e)
            // 用传入的 context 兜底：attachBaseContext 阶段 applicationContext 尚为 null
            plainFallback(context)
        }
        runCatching { readPersistedValues() }.onFailure { e ->
            AppLogger.logError("Preferences", "读取已保存偏好失败，沿用默认值: ${e.message}", e)
        }
        initialized = true
    }

    /** 把持久化值读入各 StateFlow */
    private fun readPersistedValues() {
        _loginStateFlow.value = readBoolean(SP_ALREADY_LOGIN, false)
        _loginCookieFlow.value = SessionCookie(readString(SP_LOGIN_COOKIE, "").orEmpty())
        _loginCookieDomain = readString(SP_LOGIN_COOKIE_DOMAIN, "").orEmpty().lowercase()
        _cloudFlareCookieFlow.value = SessionCookie(readString(SP_CF_COOKIE, "").orEmpty())
        _cloudFlareCookieDomain = readString(SP_CF_COOKIE_DOMAIN, "").orEmpty().lowercase()
        _savedUserIdFlow.value = readString(SP_SAVED_USER_ID, "").orEmpty()
        _maxDownloadConcurrentFlow.value = readInt(SP_MAX_DOWNLOAD_CONCURRENT, 3)
        _downloadRateLimitFlow.value = readInt(SP_DOWNLOAD_RATE_LIMIT, 0)
        _downloadWifiOnlyFlow.value = readBoolean(SP_DOWNLOAD_WIFI_ONLY, false)
        _downloadChargingOnlyFlow.value = readBoolean(SP_DOWNLOAD_CHARGING_ONLY, false)
        _downloadNightOnlyFlow.value = readBoolean(SP_DOWNLOAD_NIGHT_ONLY, false)
        _subscriptionReminderFlow.value = readBoolean(SP_SUBSCRIPTION_REMINDER, false)
        _watchStatsEnabledFlow.value = readBoolean(SP_WATCH_STATS_ENABLED, true)
        // 清洗历史存储的 baseUrl（可能含 /enter 等路径或为 http），回写以保证后续拼接正确；
        // 清洗失败时回退默认地址
        val rawBaseUrl = readString(SP_BASE_URL, DEFAULT_BASE_URL)?.ifBlank { DEFAULT_BASE_URL } ?: DEFAULT_BASE_URL
        val safeBaseUrl = sanitizeBaseUrl(rawBaseUrl) ?: DEFAULT_BASE_URL
        if (safeBaseUrl != rawBaseUrl) {
            runCatching { sp.edit { putString(SP_BASE_URL, safeBaseUrl) } }
        }
        _baseUrlFlow.value = safeBaseUrl
        _appLanguageFlow.value = readString(SP_APP_LANGUAGE, LANGUAGE_ZH_CN) ?: LANGUAGE_ZH_CN
        _themeModeFlow.value = ThemeMode.fromName(readString(SP_THEME_MODE, null))
        _playbackSpeedFlow.value = readFloat(SP_PLAYBACK_SPEED, 1f)
        _preferredQualityFlow.value = readString(SP_PREFERRED_QUALITY, "").orEmpty()
        _autoPlayNextFlow.value = readBoolean(SP_AUTO_PLAY_NEXT, true)
        _loopPlaybackFlow.value = readBoolean(SP_LOOP_PLAYBACK, false)
        _longPressBoostSpeedFlow.value = readFloat(SP_LONG_PRESS_SPEED, DEFAULT_LONG_PRESS_SPEED)
            .coerceIn(MIN_LONG_PRESS_SPEED, MAX_LONG_PRESS_SPEED)
        _doubleTapSeekFlow.value = readBoolean(SP_DOUBLE_TAP_SEEK, true)
        _autoQualityFlow.value = readBoolean(SP_AUTO_QUALITY, true)
        _downloadStoragePathFlow.value = readString(SP_DOWNLOAD_STORAGE_PATH, "").orEmpty()
        // 未保存过 / 无法识别时保持 null，交由界面按宽度档位取默认值
        _searchLayoutModeFlow.value = SearchLayoutMode.fromNameOrNull(readString(SP_SEARCH_LAYOUT_MODE, null))
        _relatedLayoutModeFlow.value = RelatedLayoutMode.fromNameOrNull(readString(SP_RELATED_LAYOUT_MODE, null))
        _skippedUpdateVersionFlow.value = readString(SP_SKIPPED_UPDATE_VERSION, "").orEmpty()
        _autoCheckUpdateFlow.value = readBoolean(SP_AUTO_CHECK_UPDATE, true)
        migrateLegacyLoginCookieDomain()
    }

    private fun readBoolean(key: String, default: Boolean): Boolean =
        runCatching { sp.getBoolean(key, default) }.getOrDefault(default)

    private fun readInt(key: String, default: Int): Int =
        runCatching { sp.getInt(key, default) }.getOrDefault(default)

    private fun readFloat(key: String, default: Float): Float =
        runCatching { sp.getFloat(key, default) }.getOrDefault(default)

    private fun readString(key: String, default: String?): String? =
        runCatching { sp.getString(key, default) }.getOrDefault(default)

    /**
     * 提供加密的 SharedPreferences（AndroidX Security）。
     * - 首次从明文旧文件迁移：读取旧值 → 删除旧文件 → 写入加密文件，避免明文会话残留。
     * - Android Keystore 不可用（极端设备）时回退明文存储，但不再落盘会话凭据。
     * - 换机恢复后旧密文无法解密时清空重建，而不是把加密文件当明文读写。
     *
     * 局部抑制 DEPRECATION：EncryptedSharedPreferences 与 MasterKey 在
     * security-crypto 1.1.0 被标记弃用，但 AndroidX 没有给出替代 API（官方建议
     * 自行基于 Keystore 实现）。自行替换意味着重做密钥管理与既有密文的迁移路径，
     * 一旦出错会直接导致用户登录态与全部配置丢失，风险与收益不成比例。
     * 待上游给出迁移方案，或本模块整体更换存储方案时再处理。
     */
    @Suppress("DEPRECATION")
    private fun provideSecurePreferences(context: Context): SharedPreferences {
        // Application.attachBaseContext 阶段 applicationContext 仍为 null，直接使用传入的 context
        val appCtx = context.applicationContext ?: context
        val masterKey = runCatching {
            MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }.getOrNull() ?: return plainFallback(context)

        // 加密存储只创建一次：既用于读取迁移标记，也直接作为返回值。
        // 早期实现是先创建一次判断标记、再创建一次返回，每次启动多付一次 Keystore/文件解析成本。
        val encrypted = createEncrypted(appCtx, masterKey)
        val legacyFile = File(appCtx.filesDir.parentFile, "shared_prefs/$NAME.xml")
        // 仅当旧文件确为明文格式才迁移，否则会误读加密文件为 null 并覆盖已保存数据。
        // 迁移标记写在加密存储里，已迁移的用户不再触发 isLegacyPlaintextPrefs（它会额外打开
        // 一次同名 SharedPreferences 并解析整份 XML）。
        val alreadyMigrated = runCatching {
            encrypted?.getBoolean(SP_MIGRATED_TO_ENCRYPTED, false) == true
        }.getOrDefault(false)
        // encrypted == null 时跳过迁移，直接落到下方的「清空重建 / 保持原状」判定
        if (encrypted != null && !alreadyMigrated &&
            legacyFile.exists() && isLegacyPlaintextPrefs(appCtx)
        ) {
            // 读取明文旧值（此时旧文件尚在），随后删除再创建加密文件
            val legacy = appCtx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            val alreadyLogin = legacy.getBoolean(SP_ALREADY_LOGIN, false)
            val loginCookie = legacy.getString(SP_LOGIN_COOKIE, "").orEmpty()
            val cfCookie = legacy.getString(SP_CF_COOKIE, "").orEmpty()
            val savedUserId = legacy.getString(SP_SAVED_USER_ID, "").orEmpty()
            val maxConcurrent = legacy.getInt(SP_MAX_DOWNLOAD_CONCURRENT, 3)
            val baseUrl = legacy.getString(SP_BASE_URL, DEFAULT_BASE_URL).orEmpty()
            val appLang = legacy.getString(SP_APP_LANGUAGE, LANGUAGE_ZH_CN).orEmpty()
            val themeMode = legacy.getString(SP_THEME_MODE, ThemeMode.SYSTEM.name).orEmpty()
            appCtx.deleteSharedPreferences(NAME)
            // 删除明文文件后需要重建：旧实例仍指向已删除的文件，复用会读到空数据
            val enc = createEncrypted(appCtx, masterKey) ?: return plainFallback(context)
            enc.edit {
                putBoolean(SP_ALREADY_LOGIN, alreadyLogin)
                putString(SP_LOGIN_COOKIE, loginCookie)
                putString(
                    SP_LOGIN_COOKIE_DOMAIN,
                    (runCatching { android.net.Uri.parse(baseUrl).host?.lowercase() }.getOrNull() ?: "")
                )
                putString(SP_CF_COOKIE, cfCookie)
                putString(SP_SAVED_USER_ID, savedUserId)
                putInt(SP_MAX_DOWNLOAD_CONCURRENT, maxConcurrent)
                putString(SP_BASE_URL, baseUrl)
                putString(SP_APP_LANGUAGE, appLang)
                putString(SP_THEME_MODE, themeMode)
            }
            AppLogger.log("Preferences", "已将明文偏好迁移至 EncryptedSharedPreferences")
            enc.edit { putBoolean(SP_MIGRATED_TO_ENCRYPTED, true) }
            return enc
        }

        encrypted?.let { enc ->
            val size = runCatching { enc.all.size }.getOrDefault(-1)
            AppLogger.log("Preferences", "加密存储已就绪，条目数=$size")
            // 首次确认没有可迁移的明文文件后落标记，后续启动无需再打开明文 SharedPreferences
            if (!alreadyMigrated) {
                runCatching { enc.edit { putBoolean(SP_MIGRATED_TO_ENCRYPTED, true) } }
            }
            return enc
        }

        // 创建失败有两种截然不同的原因，必须区分：
        // 1) 换机恢复后 Keystore 主密钥不匹配 —— 旧密文确实读不出来，清空重建是正确恢复；
        // 2) 传入的 Context 尚不可用（如 Application.attachBaseContext 阶段）—— 数据本身完好，
        //    此时清空重建会把用户的全部配置抹成默认值，且每次启动重复发生。
        // 因此先验证能否访问偏好文件：访问不了说明是原因 2，绝不删除。
        val prefsAccessible = runCatching {
            appCtx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            true
        }.getOrDefault(false)
        if (!prefsAccessible) {
            AppLogger.logError(
                "Preferences",
                "加密存储创建失败且无法访问偏好文件（多为 Context 尚未就绪），为避免丢失配置，本次不清空重建"
            )
            return plainFallback(context)
        }

        AppLogger.logError(
            "Preferences",
            "加密存储创建失败（常见于换机恢复后 Keystore 主密钥不匹配），清空旧偏好后重建，需重新登录"
        )
        appCtx.deleteSharedPreferences(NAME)
        return createEncrypted(appCtx, masterKey) ?: plainFallback(context)
    }

    @Suppress("DEPRECATION")
    private fun createEncrypted(context: Context, masterKey: MasterKey): SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            context,
            NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    /**
     * 回退明文存储（Android Keystore 不可用，或加密存储反复创建失败）。
     *
     * 置位 [secureStorageAvailable] = false，此后 [saveLogin] / [saveCloudFlareCookie] 不再把
     * 会话凭据落盘，只在内存中保留，避免明文存储造成凭据泄露。
     */
    private fun plainFallback(context: Context): SharedPreferences {
        secureStorageAvailable = false
        AppLogger.logError(
            "Preferences",
            "加密存储不可用，回退明文存储；登录态将仅保留在内存中，不会落盘"
        )
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    /**
     * 判断 shared_prefs/$NAME.xml 是否为升级前的明文旧格式：加密存储的键是密文，用明文
     * SharedPreferences 读取时无法命中已知键，据此区分「待迁移的旧明文文件」与「已加密的文件」，
     * 避免迁移逻辑每次启动都误触发并覆盖已保存数据。
     */
    private fun isLegacyPlaintextPrefs(context: Context): Boolean {
        val legacy = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return legacy.contains(SP_APP_LANGUAGE)
            || legacy.contains(SP_THEME_MODE)
            || legacy.contains(SP_ALREADY_LOGIN)
            || legacy.contains(SP_BASE_URL)
    }

    /** 保留旧名，供尚未迁移的调用点使用。 */
    val isAlreadyLogin: Boolean get() = _loginStateFlow.value

    override val isLoggedIn: Boolean get() = _loginStateFlow.value

    override val loginCookie: SessionCookie get() = _loginCookieFlow.value

    /** 登录态 Cookie 的来源域名（小写）；为空表示未知，此时不会向任何主机注入。 */
    override val loginCookieDomain: String get() = _loginCookieDomain

    /**
     * 历史数据迁移：升级前的版本只持久化了 Cookie 文本，没有记录来源域名。
     * 当时 Cookie 只可能由「当前 baseUrl 站点」的登录流程写入，故用当前 baseUrl 的 host 作为来源。
     * 迁移后立刻回写；若用户随后又把 baseUrl 改成镜像站，会话依然不会跨域注入。
     */
    private fun migrateLegacyLoginCookieDomain() {
        if (_loginCookieDomain.isNotBlank()) return
        val cookie = _loginCookieFlow.value.raw
        if (cookie.isBlank()) return
        val host = runCatching { android.net.Uri.parse(_baseUrlFlow.value).host?.lowercase() }
            .getOrNull() ?: return
        _loginCookieDomain = host
        runCatching { sp.edit { putString(SP_LOGIN_COOKIE_DOMAIN, host) } }
    }

    override val cloudFlareCookie: SessionCookie get() = _cloudFlareCookieFlow.value

    /**
     * 注入人机校验 Cookie 的目标域名。
     *
     * 历史版本把它借存在登录域名上（当时校验只能从登录页顺带产生），因此这里保留回退，
     * 让老用户的 `cf_clearance` 不至于因为改字段名而失效。
     */
    override val cloudFlareCookieDomain: String
        get() = _cloudFlareCookieDomain.ifBlank { _loginCookieDomain }

    override val savedUserId: String get() = _savedUserIdFlow.value

    override val maxDownloadConcurrent: Int get() = _maxDownloadConcurrentFlow.value

    override val downloadRateLimit: Int get() = _downloadRateLimitFlow.value

    override val subscriptionReminder: Boolean get() = _subscriptionReminderFlow.value

    override val watchStatsEnabled: Boolean get() = _watchStatsEnabledFlow.value

    override val baseUrl: String get() = _baseUrlFlow.value

    /**
     * 当前 baseUrl 是否为支持登录的官方域名（hanime1.me / hanimeone.me）。
     * 镜像站登录接口会返回“站点维护中”，需在 UI 层提前拦截。
     */
    override val isLoginSupported: Boolean
        get() = isLoginSupportedHost(baseUrl)

    /** 登录支持状态的反应式 Flow，baseUrl 变化时自动更新。 */
    override val loginSupportedFlow: StateFlow<Boolean> = _baseUrlFlow
        .map { isLoginSupportedHost(it) }
        .stateIn(preferencesScope, SharingStarted.Eagerly, isLoginSupportedHost(_baseUrlFlow.value))

    private fun isLoginSupportedHost(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host in LOGIN_SUPPORTED_DOMAINS
    }

    override val appLanguage: String get() = _appLanguageFlow.value

    override val themeMode: ThemeMode get() = _themeModeFlow.value

    override val playbackSpeed: Float get() = _playbackSpeedFlow.value
    override val preferredQuality: String get() = _preferredQualityFlow.value
    override val autoPlayNext: Boolean get() = _autoPlayNextFlow.value
    override val loopPlayback: Boolean get() = _loopPlaybackFlow.value
    override val longPressBoostSpeed: Float get() = _longPressBoostSpeedFlow.value
    override val doubleTapSeek: Boolean get() = _doubleTapSeekFlow.value
    override val autoQuality: Boolean get() = _autoQualityFlow.value
    override val searchLayoutMode: SearchLayoutMode? get() = _searchLayoutModeFlow.value
    override val relatedLayoutMode: RelatedLayoutMode? get() = _relatedLayoutModeFlow.value

    override fun setAppLanguage(lang: String) {
        sp.edit { putString(SP_APP_LANGUAGE, lang) }
        _appLanguageFlow.value = lang
        LocaleHelper.rememberLanguage(lang)
    }

    override fun setThemeMode(mode: ThemeMode) {
        sp.edit { putString(SP_THEME_MODE, mode.name) }
        _themeModeFlow.value = mode
    }

    /**
     * 设置站点基址。留空表示恢复默认地址。
     *
     * @return true 表示输入被接受（可能已规范化为「https:// + host」）；
     *   false 表示输入非法，此时保持原设置不变，由调用方提示用户。
     */
    override fun setBaseUrl(url: String): Boolean {
        val input = url.ifBlank { DEFAULT_BASE_URL }
        val safeUrl = sanitizeBaseUrl(input) ?: return false
        sp.edit { putString(SP_BASE_URL, safeUrl) }
        _baseUrlFlow.value = safeUrl
        return true
    }

    /**
     * 规范化为「https:// + host + 可选端口」，去掉路径（如镜像站的 /enter 入口），
     * 确保后续拼接 /search、/watch 等路径时不会产生 /enter/search 这类无效 URL。
     * 只接受 https：该地址会承载登录 Cookie 与视频防盗链 Referer。
     *
     * @return 规范化后的地址；输入不是合法 https 域名/IP 时返回 null。
     */
    private fun sanitizeBaseUrl(url: String): String? {
        val trimmed = url.trim().trimEnd('/')
        val host = HTTPS_PREFIX.find(trimmed)?.groupValues?.get(1) ?: return null
        if (!HOST_PATTERN.matches(host)) return null
        return "https://$host"
    }

    /**
     * 保存登录态。
     *
     * 加密存储不可用时**不落盘**：明文存储中的登录 Cookie 可被同设备上任意应用或 root 读取，
     * 代价高于「本次会话有效、重启后需重新登录」。此时登录态只在内存中保留。
     */
    override fun saveLogin(cookieString: String, userId: String?, sourceDomain: String?) {
        val safeCookie = cookieString.take(8192)
        val id = userId ?: extractUserId(safeCookie)
        // 会话必须与来源域名绑定：CookieJar 只会把它注入回这个 host。
        // 未显式指定时取当前 baseUrl 的 host（手填 Cookie / 透传场景），调用方无法提供时该值为空，
        // 此时宁可不注入，也不能把它发给用户后来改成的任意镜像站。
        val domain = sourceDomain?.trim()?.lowercase()
            ?.ifBlank { null }
            ?: currentBaseHost()
        if (secureStorageAvailable) {
            sp.edit {
                putBoolean(SP_ALREADY_LOGIN, true)
                putString(SP_LOGIN_COOKIE, safeCookie)
                putString(SP_SAVED_USER_ID, id)
                putString(SP_LOGIN_COOKIE_DOMAIN, domain)
            }
        } else {
            AppLogger.logError(
                "Preferences",
                "加密存储不可用，登录凭据仅保留在内存中（不落盘），重启后需重新登录"
            )
        }
        _loginStateFlow.value = true
        _loginCookieFlow.value = SessionCookie(safeCookie)
        _loginCookieDomain = domain
        _savedUserIdFlow.value = id
    }

    /**
     * 保存当前登录用户的数字 ID（来自详情页解析），供评论/点赞等需要用户标识的接口使用。
     * 该值与登录态一并持久化，重启后由 init() 读回。
     */
    override fun saveUserId(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        // 纯数字用户标识不属于凭据，无论存储是否加密都落盘，避免界面在重启后丢失展示信息
        sp.edit { putString(SP_SAVED_USER_ID, clean) }
        _savedUserIdFlow.value = clean
    }

    /** 保存 Cloudflare 校验 Cookie。加密存储不可用时同样不落盘（同 [saveLogin]）。 */
    override fun saveCloudFlareCookie(cookieString: String, sourceDomain: String?) {
        val safe = cookieString.take(8192)
        // 域名只认这次校验实际发生的站点：改了 baseUrl 换镜像后，旧 Cookie 不应被发到新域名
        val domain = sourceDomain?.trim()?.lowercase()?.ifBlank { null }
        if (secureStorageAvailable) {
            sp.edit {
                putString(SP_CF_COOKIE, safe)
                if (domain != null) putString(SP_CF_COOKIE_DOMAIN, domain)
            }
        } else {
            AppLogger.logError(
                "Preferences",
                "加密存储不可用，Cloudflare Cookie 仅保留在内存中（不落盘）"
            )
        }
        if (domain != null) _cloudFlareCookieDomain = domain
        _cloudFlareCookieFlow.value = SessionCookie(safe)
    }

    override fun setMaxDownloadConcurrent(max: Int) {
        val safeMax = max.coerceIn(1, 5)
        sp.edit { putInt(SP_MAX_DOWNLOAD_CONCURRENT, safeMax) }
        _maxDownloadConcurrentFlow.value = safeMax
    }

    override fun setDownloadRateLimit(megabytesPerSecond: Int) {
        val safe = megabytesPerSecond.coerceIn(0, 20)
        sp.edit { putInt(SP_DOWNLOAD_RATE_LIMIT, safe) }
        _downloadRateLimitFlow.value = safe
    }

    override val downloadWifiOnly: Boolean get() = _downloadWifiOnlyFlow.value
    override val downloadChargingOnly: Boolean get() = _downloadChargingOnlyFlow.value
    override val downloadNightOnly: Boolean get() = _downloadNightOnlyFlow.value

    override fun setDownloadWifiOnly(enabled: Boolean) {
        sp.edit { putBoolean(SP_DOWNLOAD_WIFI_ONLY, enabled) }
        _downloadWifiOnlyFlow.value = enabled
    }

    override fun setDownloadChargingOnly(enabled: Boolean) {
        sp.edit { putBoolean(SP_DOWNLOAD_CHARGING_ONLY, enabled) }
        _downloadChargingOnlyFlow.value = enabled
    }

    override fun setDownloadNightOnly(enabled: Boolean) {
        sp.edit { putBoolean(SP_DOWNLOAD_NIGHT_ONLY, enabled) }
        _downloadNightOnlyFlow.value = enabled
    }

    override val downloadQueueOrder: List<Int>
        get() = runCatching {
            readString(SP_DOWNLOAD_QUEUE_ORDER, "").orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
        }.getOrDefault(emptyList())

    override fun setDownloadQueueOrder(ids: List<Int>) {
        sp.edit { putString(SP_DOWNLOAD_QUEUE_ORDER, ids.joinToString(",")) }
    }

    override val acknowledgedCrashFile: String
        get() = readString(SP_ACKNOWLEDGED_CRASH, "").orEmpty()

    override fun setAcknowledgedCrashFile(name: String) {
        sp.edit { putString(SP_ACKNOWLEDGED_CRASH, name) }
    }

    override fun setSubscriptionReminder(enabled: Boolean) {
        sp.edit { putBoolean(SP_SUBSCRIPTION_REMINDER, enabled) }
        _subscriptionReminderFlow.value = enabled
    }

    override fun setWatchStatsEnabled(enabled: Boolean) {
        sp.edit { putBoolean(SP_WATCH_STATS_ENABLED, enabled) }
        _watchStatsEnabledFlow.value = enabled
    }

    override val subscriptionSeenVideoIds: Set<String>
        get() = readString(SP_SUBSCRIPTION_SEEN_IDS, "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toSet()

    override fun setSubscriptionSeenVideoIds(ids: Set<String>) {
        sp.edit { putString(SP_SUBSCRIPTION_SEEN_IDS, ids.joinToString("\n")) }
    }

    /** 播放倍速偏好（0.25x–3x）。进入播放器时自动应用，切换时写回。 */
    override fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        sp.edit { putFloat(SP_PLAYBACK_SPEED, safe) }
        _playbackSpeedFlow.value = safe
    }

    override fun setLongPressBoostSpeed(speed: Float) {
        val safe = speed.coerceIn(MIN_LONG_PRESS_SPEED, MAX_LONG_PRESS_SPEED)
        sp.edit { putFloat(SP_LONG_PRESS_SPEED, safe) }
        _longPressBoostSpeedFlow.value = safe
    }

    override fun setDoubleTapSeek(enabled: Boolean) {
        sp.edit { putBoolean(SP_DOUBLE_TAP_SEEK, enabled) }
        _doubleTapSeekFlow.value = enabled
    }

    override fun setAutoQuality(enabled: Boolean) {
        sp.edit { putBoolean(SP_AUTO_QUALITY, enabled) }
        _autoQualityFlow.value = enabled
    }

    /** 画质偏好（分辨率字符串，如 "1080p"；空串表示跟随默认/最高）。 */
    override fun setPreferredQuality(resolution: String) {
        sp.edit { putString(SP_PREFERRED_QUALITY, resolution) }
        _preferredQualityFlow.value = resolution
    }

    /** 连播（下一集自动播放）开关。 */
    override fun setAutoPlayNext(enabled: Boolean) {
        sp.edit { putBoolean(SP_AUTO_PLAY_NEXT, enabled) }
        _autoPlayNextFlow.value = enabled
    }

    override fun setLoopPlayback(enabled: Boolean) {
        sp.edit { putBoolean(SP_LOOP_PLAYBACK, enabled) }
        _loopPlaybackFlow.value = enabled
    }

    /**
     * 搜索页布局模式。写入后即视为「用户已显式选择」，此后不再回退到按宽度档位的默认值。
     */
    override fun setSearchLayoutMode(mode: SearchLayoutMode) {
        sp.edit { putString(SP_SEARCH_LAYOUT_MODE, mode.name) }
        _searchLayoutModeFlow.value = mode
    }

    override fun setRelatedLayoutMode(mode: RelatedLayoutMode) {
        sp.edit { putString(SP_RELATED_LAYOUT_MODE, mode.name) }
        _relatedLayoutModeFlow.value = mode
    }

    override fun clearRelatedLayoutMode() {
        sp.edit { remove(SP_RELATED_LAYOUT_MODE) }
        _relatedLayoutModeFlow.value = null
    }

    // ── 应用更新 ────────────────────────────────────────────────────────────

    /** 被用户选择「不再提示」的版本号；空串表示没有跳过任何版本。 */
    val skippedUpdateVersion: String get() = _skippedUpdateVersionFlow.value

    /** 记录「不再提示」的版本号；传空串即清除记录。 */
    fun setSkippedUpdateVersion(versionName: String) {
        val clean = versionName.trim()
        sp.edit { putString(SP_SKIPPED_UPDATE_VERSION, clean) }
        _skippedUpdateVersionFlow.value = clean
    }

    /** 是否允许启动时自动检查更新。 */
    val autoCheckUpdateEnabled: Boolean get() = _autoCheckUpdateFlow.value

    fun setAutoCheckUpdateEnabled(enabled: Boolean) {
        sp.edit { putBoolean(SP_AUTO_CHECK_UPDATE, enabled) }
        _autoCheckUpdateFlow.value = enabled
    }

    /**
     * 下载存储路径。空串表示「默认目录」（由 DownloadManager 解析为应用外部存储 /Downloads，
     * 不可用时回退内部存储）；非空时为用户指定的目录绝对路径。
     */
    override var downloadStoragePath: String
        // 读走内存快照而非 sp：本类是 object 且 sp 为 lateinit，直接访问 getter 会在
        // init() 之前抛 UninitializedPropertyAccessException；其余偏好项均由 Flow 支撑，
        // 这一项此前是唯一例外，与 DownloadManager.resolveDownloadDir() 的实时读取冲突。
        get() = _downloadStoragePathFlow.value
        set(value) {
            val safe = value.trim()
            sp.edit { putString(SP_DOWNLOAD_STORAGE_PATH, safe) }
            _downloadStoragePathFlow.value = safe
        }

    override fun logout() {
        HCookieJar.clearAll()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        // 必须 commit 而非 apply：apply 是异步落盘，进程在写盘前被杀就会出现
        // 「内存已登出、磁盘仍留会话」，重启后表现为已登出却仍带着旧会话。
        // 登出是低频操作，一次同步写盘换来确定的安全语义是值得的。
        runCatching {
            // 不能用 androidx 的 edit { } 扩展：它返回 Unit，拿不到 Editor 也就无法 commit()。
            @Suppress("ApplySharedPref")
            val editor = sp.edit()
            editor.remove(SP_ALREADY_LOGIN)
            editor.remove(SP_LOGIN_COOKIE)
            // Cloudflare 校验 Cookie 同属会话材料：漏删会在下次登录时被继续注入
            editor.remove(SP_CF_COOKIE)
            editor.remove(SP_CF_COOKIE_DOMAIN)
            editor.remove(SP_LOGIN_COOKIE_DOMAIN)
            editor.remove(SP_SAVED_USER_ID)
            editor.commit()
        }
        _loginStateFlow.value = false
        _loginCookieFlow.value = SessionCookie("")
        // 内存态同样要清：只删持久化会留下「本次进程仍带着 CF Cookie」的窗口
        _cloudFlareCookieFlow.value = SessionCookie("")
        _cloudFlareCookieDomain = ""
        _loginCookieDomain = ""
        _savedUserIdFlow.value = ""
    }

    /** 当前 baseUrl 的 host（小写）；解析失败时返回空串。 */
    private fun currentBaseHost(): String =
        runCatching { android.net.Uri.parse(_baseUrlFlow.value).host?.lowercase() }.getOrNull().orEmpty()

    private fun extractUserId(cookieString: String): String {
        if (cookieString.isBlank()) return ""
        val segments = cookieString.split(';')
        for (seg in segments) {
            val name = seg.substringBefore('=').trim()
            val value = seg.substringAfter('=').trim()
            if (name.contains("user_id", ignoreCase = true) ||
                name.contains("uid", ignoreCase = true)) return value
        }
        return ""
    }

    override fun extractDisplayEmail(): String = extractDisplayEmail(loginCookie.raw, savedUserId)

    fun extractDisplayEmail(cookieString: String, userId: String): String {
        if (cookieString.isBlank()) return ""
        for (seg in cookieString.split(';')) {
            val value = seg.substringAfter('=').trim()
            if (value.contains('@')) return value.substringBefore('@').take(12)
        }
        return userId.ifBlank { "H" }.take(8)
    }
}