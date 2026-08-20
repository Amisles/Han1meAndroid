package app.amisles.hanime.data.preferences

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
import java.io.File

object Preferences {

    // 与 Application 同生命周期的作用域，避免直接使用被标记为 delicate 的 GlobalScope
    private val preferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val NAME = "hanime_app_prefs"

    private const val SP_ALREADY_LOGIN = "already_login"
    private const val SP_LOGIN_COOKIE = "login_cookie"
    private const val SP_CF_COOKIE = "cloudflare_cookie"
    private const val SP_SAVED_USER_ID = "saved_user_id"
    private const val SP_MAX_DOWNLOAD_CONCURRENT = "max_download_concurrent"
    private const val SP_BASE_URL = "base_url"
    private const val SP_APP_LANGUAGE = "app_language"
    private const val SP_THEME_MODE = "theme_mode"
    private const val SP_PLAYBACK_SPEED = "playback_speed"
    private const val SP_PREFERRED_QUALITY = "preferred_quality"
    private const val SP_AUTO_PLAY_NEXT = "auto_play_next"
    private const val SP_DOWNLOAD_STORAGE_PATH = "download_storage_path"

    const val DEFAULT_BASE_URL = "https://hanime1.me"

    // 支持登录的官方域名（其余镜像站登录接口返回“站点维护中”）
    private val LOGIN_SUPPORTED_DOMAINS = setOf("hanime1.me", "hanimeone.me")

    const val LANGUAGE_ZH_CN = "zh-CN"
    const val LANGUAGE_ZH_TW = "zh-TW"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_JA = "ja"
    val SUPPORTED_LANGUAGES = listOf(LANGUAGE_ZH_CN, LANGUAGE_ZH_TW, LANGUAGE_EN, LANGUAGE_JA)

    private lateinit var sp: android.content.SharedPreferences

    private val _loginStateFlow = MutableStateFlow(false)
    val loginStateFlow: StateFlow<Boolean> = _loginStateFlow.asStateFlow()

    private val _loginCookieFlow = MutableStateFlow(CookieString(""))
    val loginCookieFlow: StateFlow<CookieString> = _loginCookieFlow.asStateFlow()

    private val _cloudFlareCookieFlow = MutableStateFlow(CookieString(""))
    val cloudFlareCookieFlow: StateFlow<CookieString> = _cloudFlareCookieFlow.asStateFlow()

    private val _savedUserIdFlow = MutableStateFlow("")
    val savedUserIdFlow: StateFlow<String> = _savedUserIdFlow.asStateFlow()

    private val _maxDownloadConcurrentFlow = MutableStateFlow(3)
    val maxDownloadConcurrentFlow: StateFlow<Int> = _maxDownloadConcurrentFlow.asStateFlow()

    private val _baseUrlFlow = MutableStateFlow(DEFAULT_BASE_URL)
    val baseUrlFlow: StateFlow<String> = _baseUrlFlow.asStateFlow()

    private val _appLanguageFlow = MutableStateFlow(LANGUAGE_ZH_CN)
    val appLanguageFlow: StateFlow<String> = _appLanguageFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _playbackSpeedFlow = MutableStateFlow(1f)
    val playbackSpeedFlow: StateFlow<Float> = _playbackSpeedFlow.asStateFlow()

    private val _preferredQualityFlow = MutableStateFlow("")
    val preferredQualityFlow: StateFlow<String> = _preferredQualityFlow.asStateFlow()

    private val _autoPlayNextFlow = MutableStateFlow(true)
    val autoPlayNextFlow: StateFlow<Boolean> = _autoPlayNextFlow.asStateFlow()

    // 下载存储路径：空串表示使用默认目录（应用外部存储 /Downloads，不可用时回退内部存储）
    private val _downloadStoragePathFlow = MutableStateFlow("")
    val downloadStoragePathFlow: StateFlow<String> = _downloadStoragePathFlow.asStateFlow()

    fun init(context: Context) {
        // 在 attachBaseContext 阶段 applicationContext 为 null，直接使用传入的 context
        sp = provideSecurePreferences(context)
        _loginStateFlow.value = sp.getBoolean(SP_ALREADY_LOGIN, false)
        _loginCookieFlow.value = CookieString(sp.getString(SP_LOGIN_COOKIE, "").orEmpty())
        _cloudFlareCookieFlow.value = CookieString(sp.getString(SP_CF_COOKIE, "").orEmpty())
        _savedUserIdFlow.value = sp.getString(SP_SAVED_USER_ID, "").orEmpty()
        _maxDownloadConcurrentFlow.value = sp.getInt(SP_MAX_DOWNLOAD_CONCURRENT, 3)
        // 清洗历史存储的 baseUrl（可能含 /enter 等路径），回写以保证后续拼接正确
        val rawBaseUrl = sp.getString(SP_BASE_URL, DEFAULT_BASE_URL)?.ifBlank { DEFAULT_BASE_URL } ?: DEFAULT_BASE_URL
        val safeBaseUrl = sanitizeBaseUrl(rawBaseUrl)
        if (safeBaseUrl != rawBaseUrl) {
            sp.edit { putString(SP_BASE_URL, safeBaseUrl) }
        }
        _baseUrlFlow.value = safeBaseUrl
        _appLanguageFlow.value = sp.getString(SP_APP_LANGUAGE, LANGUAGE_ZH_CN) ?: LANGUAGE_ZH_CN
        _themeModeFlow.value = ThemeMode.fromName(sp.getString(SP_THEME_MODE, null))
        _playbackSpeedFlow.value = sp.getFloat(SP_PLAYBACK_SPEED, 1f)
        _preferredQualityFlow.value = sp.getString(SP_PREFERRED_QUALITY, "").orEmpty()
        _autoPlayNextFlow.value = sp.getBoolean(SP_AUTO_PLAY_NEXT, true)
        _downloadStoragePathFlow.value = sp.getString(SP_DOWNLOAD_STORAGE_PATH, "").orEmpty()
    }

    /**
     * 提供加密的 SharedPreferences 实例（AndroidX Security）。
     * - 首次从明文旧文件迁移：读取旧值 → 删除旧文件 → 写入加密文件，避免明文会话残留。
     * - 若 Android Keystore 不可用（极端设备），回退到明文存储并告警，保证可用性优先。
     */
    private fun provideSecurePreferences(context: Context): SharedPreferences {
        val appCtx = context.applicationContext
        val masterKey = runCatching {
            MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }.getOrNull() ?: return fallbackPlain(appCtx)

        val legacyFile = File(appCtx.filesDir.parentFile, "shared_prefs/$NAME.xml")
        // 仅当旧文件确为明文格式（含可读明文键）才迁移；否则直接走加密存储，避免每次启动误读加密文件为 null 并覆盖已保存数据。
        if (legacyFile.exists() && isLegacyPlaintextPrefs(appCtx)) {
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
            val enc = createEncrypted(appCtx, masterKey) ?: return fallbackPlain(appCtx)
            enc.edit {
                putBoolean(SP_ALREADY_LOGIN, alreadyLogin)
                putString(SP_LOGIN_COOKIE, loginCookie)
                putString(SP_CF_COOKIE, cfCookie)
                putString(SP_SAVED_USER_ID, savedUserId)
                putInt(SP_MAX_DOWNLOAD_CONCURRENT, maxConcurrent)
                putString(SP_BASE_URL, baseUrl)
                putString(SP_APP_LANGUAGE, appLang)
                putString(SP_THEME_MODE, themeMode)
            }
            AppLogger.log("Preferences", "已将明文偏好迁移至 EncryptedSharedPreferences")
            return enc
        }
        return createEncrypted(appCtx, masterKey) ?: fallbackPlain(appCtx)
    }

    /**
     * 判断 shared_prefs/$NAME.xml 是否为升级前的“明文旧格式”偏好文件。
     * 加密存储的键是密文，用明文 SharedPreferences 读取时无法命中已知键，因此以此区分
     * “待迁移的旧明文文件”与“已加密的文件”，避免迁移逻辑每次启动都误触发并覆盖已保存数据。
     */
    private fun isLegacyPlaintextPrefs(context: Context): Boolean {
        val legacy = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return legacy.contains(SP_APP_LANGUAGE)
            || legacy.contains(SP_THEME_MODE)
            || legacy.contains(SP_ALREADY_LOGIN)
            || legacy.contains(SP_BASE_URL)
    }

    private fun createEncrypted(context: Context, masterKey: MasterKey): SharedPreferences? = runCatching {
        EncryptedSharedPreferences.create(
            context,
            NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private fun fallbackPlain(context: Context): SharedPreferences {
        AppLogger.logError(
            "Preferences",
            "EncryptedSharedPreferences 不可用，回退明文存储（登录 Cookie 将以明文保存，存在泄露风险）"
        )
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    val isAlreadyLogin: Boolean get() = _loginStateFlow.value

    val loginCookie: CookieString get() = _loginCookieFlow.value

    val cloudFlareCookie: CookieString get() = _cloudFlareCookieFlow.value

    val savedUserId: String get() = _savedUserIdFlow.value

    val maxDownloadConcurrent: Int get() = _maxDownloadConcurrentFlow.value

    val baseUrl: String get() = _baseUrlFlow.value

    /**
     * 当前 baseUrl 是否为支持登录的官方域名（hanime1.me / hanimeone.me）。
     * 镜像站登录接口会返回“站点维护中”，需在 UI 层提前拦截并提示用户。
     */
    val isLoginSupported: Boolean
        get() = isLoginSupportedHost(baseUrl)

    /**
     * 登录支持状态的反应式 Flow，baseUrl 变化时自动更新。
     */
    val loginSupportedFlow: StateFlow<Boolean> = _baseUrlFlow
        .map { isLoginSupportedHost(it) }
        .stateIn(preferencesScope, SharingStarted.Eagerly, isLoginSupportedHost(_baseUrlFlow.value))

    private fun isLoginSupportedHost(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host in LOGIN_SUPPORTED_DOMAINS
    }

    val appLanguage: String get() = _appLanguageFlow.value

    val themeMode: ThemeMode get() = _themeModeFlow.value

    val playbackSpeed: Float get() = _playbackSpeedFlow.value
    val preferredQuality: String get() = _preferredQualityFlow.value
    val autoPlayNext: Boolean get() = _autoPlayNextFlow.value

    fun setAppLanguage(lang: String) {
        sp.edit { putString(SP_APP_LANGUAGE, lang) }
        _appLanguageFlow.value = lang
    }

    fun setThemeMode(mode: ThemeMode) {
        // commit=true：主题选择是用户关键偏好，需同步落盘，避免进程被立即杀死时 apply() 异步写未落地而丢失。
        sp.edit(commit = true) { putString(SP_THEME_MODE, mode.name) }
        _themeModeFlow.value = mode
    }

    fun setBaseUrl(url: String) {
        val safeUrl = sanitizeBaseUrl(url)
        sp.edit { putString(SP_BASE_URL, safeUrl) }
        _baseUrlFlow.value = safeUrl
    }

    /**
     * 只保留协议+域名，去除路径（如镜像站的 /enter 入口）。
     * 确保后续拼接 /search、/watch 等路径时不会产生 /enter/search 这类无效 URL。
     */
    private fun sanitizeBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        val hostOnly = Regex("^(https?://[^/]+)").find(trimmed)?.value
        return if (hostOnly.isNullOrEmpty()) DEFAULT_BASE_URL else hostOnly
    }

    fun saveLogin(cookieString: String, userId: String? = null) {
        val safeCookie = cookieString.take(8192)
        val id = userId ?: extractUserId(safeCookie)
        sp.edit {
            putBoolean(SP_ALREADY_LOGIN, true)
            putString(SP_LOGIN_COOKIE, safeCookie)
            putString(SP_SAVED_USER_ID, id)
        }
        _loginStateFlow.value = true
        _loginCookieFlow.value = CookieString(safeCookie)
        _savedUserIdFlow.value = id
    }

    /**
     * 保存当前登录用户的数字 ID（来自详情页解析），供评论/点赞等需要用户标识的接口使用。
     * 该值与登录态一并持久化，重启后由 init() 读回。
     */
    fun saveUserId(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        sp.edit { putString(SP_SAVED_USER_ID, clean) }
        _savedUserIdFlow.value = clean
    }

    fun saveCloudFlareCookie(cookieString: String) {
        val safe = cookieString.take(8192)
        sp.edit { putString(SP_CF_COOKIE, safe) }
        _cloudFlareCookieFlow.value = CookieString(safe)
    }

    fun setMaxDownloadConcurrent(max: Int) {
        val safeMax = max.coerceIn(1, 5)
        sp.edit { putInt(SP_MAX_DOWNLOAD_CONCURRENT, safeMax) }
        _maxDownloadConcurrentFlow.value = safeMax
    }

    /**
     * 播放倍速偏好（0.25x–2x）。进入播放器时自动应用，切换时写回。
     */
    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 2f)
        sp.edit { putFloat(SP_PLAYBACK_SPEED, safe) }
        _playbackSpeedFlow.value = safe
    }

    /**
     * 画质偏好（分辨率字符串，如 "1080p"；空串表示跟随默认/最高）。
     * 进入播放器时优先选用该画质对应的视频源。
     */
    fun setPreferredQuality(resolution: String) {
        sp.edit { putString(SP_PREFERRED_QUALITY, resolution) }
        _preferredQualityFlow.value = resolution
    }

    /**
     * 连播（下一集自动播放）开关。
     */
    fun setAutoPlayNext(enabled: Boolean) {
        sp.edit { putBoolean(SP_AUTO_PLAY_NEXT, enabled) }
        _autoPlayNextFlow.value = enabled
    }

    /**
     * 下载存储路径。空串表示「默认目录」（由 DownloadManager 解析为应用外部存储 /Downloads，
     * 不可用时回退内部存储）；非空时为用户指定的目录绝对路径。
     */
    var downloadStoragePath: String
        get() = sp.getString(SP_DOWNLOAD_STORAGE_PATH, "").orEmpty()
        set(value) {
            val safe = value.trim()
            sp.edit { putString(SP_DOWNLOAD_STORAGE_PATH, safe) }
            _downloadStoragePathFlow.value = safe
        }

    fun logout() {
        HCookieJar.clearAll()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        sp.edit {
            remove(SP_ALREADY_LOGIN)
            remove(SP_LOGIN_COOKIE)
            remove(SP_SAVED_USER_ID)
        }
        _loginStateFlow.value = false
        _loginCookieFlow.value = CookieString("")
        _savedUserIdFlow.value = ""
    }

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

    fun extractDisplayEmail(): String {
        val cookie = loginCookie.cookie
        if (cookie.isBlank()) return ""
        val segments = cookie.split(';')
        for (seg in segments) {
            val value = seg.substringAfter('=').trim()
            if (value.contains('@')) return value.substringBefore('@').take(12)
        }
        val id = savedUserId.ifBlank { "H" }
        return id.take(8)
    }
}