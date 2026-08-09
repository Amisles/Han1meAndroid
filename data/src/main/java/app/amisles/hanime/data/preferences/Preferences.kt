package app.amisles.hanime.data.preferences

import android.content.Context
import android.webkit.CookieManager
import androidx.core.content.edit
import app.amisles.hanime.data.cookie.CookieString
import app.amisles.hanime.data.cookie.HCookieJar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Preferences {

    private const val NAME = "hanime_app_prefs"

    private const val SP_ALREADY_LOGIN = "already_login"
    private const val SP_LOGIN_COOKIE = "login_cookie"
    private const val SP_CF_COOKIE = "cloudflare_cookie"
    private const val SP_SAVED_USER_ID = "saved_user_id"
    private const val SP_MAX_DOWNLOAD_CONCURRENT = "max_download_concurrent"
    private const val SP_BASE_URL = "base_url"
    private const val SP_APP_LANGUAGE = "app_language"
    private const val SP_THEME_MODE = "theme_mode"

    // 默认官网地址
    const val DEFAULT_BASE_URL = "https://hanime1.me"

    // 支持的语言代码
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

    fun init(context: Context) {
        // 在 attachBaseContext 阶段 applicationContext 为 null，直接使用传入的 context
        sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
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
    }

    val isAlreadyLogin: Boolean get() = _loginStateFlow.value

    val loginCookie: CookieString get() = _loginCookieFlow.value

    val cloudFlareCookie: CookieString get() = _cloudFlareCookieFlow.value

    val savedUserId: String get() = _savedUserIdFlow.value

    val maxDownloadConcurrent: Int get() = _maxDownloadConcurrentFlow.value

    val baseUrl: String get() = _baseUrlFlow.value

    val appLanguage: String get() = _appLanguageFlow.value

    val themeMode: ThemeMode get() = _themeModeFlow.value

    fun setAppLanguage(lang: String) {
        sp.edit { putString(SP_APP_LANGUAGE, lang) }
        _appLanguageFlow.value = lang
    }

    fun setThemeMode(mode: ThemeMode) {
        sp.edit { putString(SP_THEME_MODE, mode.name) }
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