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
    private const val SP_VIDEO_LANGUAGE = "video_language"
    private const val SP_MAX_DOWNLOAD_CONCURRENT = "max_download_concurrent"

    private lateinit var sp: android.content.SharedPreferences

    private val _loginStateFlow = MutableStateFlow(false)
    val loginStateFlow: StateFlow<Boolean> = _loginStateFlow.asStateFlow()

    private val _loginCookieFlow = MutableStateFlow(CookieString(""))
    val loginCookieFlow: StateFlow<CookieString> = _loginCookieFlow.asStateFlow()

    private val _cloudFlareCookieFlow = MutableStateFlow(CookieString(""))
    val cloudFlareCookieFlow: StateFlow<CookieString> = _cloudFlareCookieFlow.asStateFlow()

    private val _savedUserIdFlow = MutableStateFlow("")
    val savedUserIdFlow: StateFlow<String> = _savedUserIdFlow.asStateFlow()

    private val _videoLanguageFlow = MutableStateFlow("zhs")
    val videoLanguageFlow: StateFlow<String> = _videoLanguageFlow.asStateFlow()

    private val _maxDownloadConcurrentFlow = MutableStateFlow(3)
    val maxDownloadConcurrentFlow: StateFlow<Int> = _maxDownloadConcurrentFlow.asStateFlow()

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        _loginStateFlow.value = sp.getBoolean(SP_ALREADY_LOGIN, false)
        _loginCookieFlow.value = CookieString(sp.getString(SP_LOGIN_COOKIE, "").orEmpty())
        _cloudFlareCookieFlow.value = CookieString(sp.getString(SP_CF_COOKIE, "").orEmpty())
        _savedUserIdFlow.value = sp.getString(SP_SAVED_USER_ID, "").orEmpty()
        _videoLanguageFlow.value = sp.getString(SP_VIDEO_LANGUAGE, "zhs").orEmpty()
        _maxDownloadConcurrentFlow.value = sp.getInt(SP_MAX_DOWNLOAD_CONCURRENT, 3)
    }

    val isAlreadyLogin: Boolean get() = _loginStateFlow.value

    val loginCookie: CookieString get() = _loginCookieFlow.value

    val cloudFlareCookie: CookieString get() = _cloudFlareCookieFlow.value

    val savedUserId: String get() = _savedUserIdFlow.value

    val videoLanguage: String get() = _videoLanguageFlow.value

    val maxDownloadConcurrent: Int get() = _maxDownloadConcurrentFlow.value

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

    fun setVideoLanguage(lang: String) {
        sp.edit { putString(SP_VIDEO_LANGUAGE, lang) }
        _videoLanguageFlow.value = lang
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