package app.amisles.hanime.data.preferences

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.SessionCookie
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.SiteSettings
import app.amisles.hanime.domain.result.SessionSaveResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 登录会话的唯一所有者：Cookie、来源域名、用户标识与登出。
 *
 * 凭据只写加密存储，并通过 [SessionSaveResult] 如实返回「已落盘 / 仅内存 / 失败」；
 * 本类不再负责设置项，也不直接清理 OkHttp 或 WebView 的 Cookie —— 那属于会话协调器。
 */
@Singleton
class SessionStore @Inject constructor(
    private val storage: PreferenceStorage,
    private val site: SiteSettings
) : SessionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _loginStateFlow = MutableStateFlow(false)
    override val loginStateFlow: StateFlow<Boolean> = _loginStateFlow.asStateFlow()

    private val _loginCookieFlow = MutableStateFlow(SessionCookie(""))
    override val loginCookieFlow: StateFlow<SessionCookie> = _loginCookieFlow.asStateFlow()

    private val _cloudFlareCookieFlow = MutableStateFlow(SessionCookie(""))
    val cloudFlareCookieFlow: StateFlow<SessionCookie> = _cloudFlareCookieFlow.asStateFlow()

    @Volatile
    private var loginCookieDomainValue: String = ""

    @Volatile
    private var cloudFlareCookieDomainValue: String = ""

    private val _savedUserIdFlow = MutableStateFlow("")
    override val savedUserIdFlow: StateFlow<String> = _savedUserIdFlow.asStateFlow()

    private val _savedUserNameFlow = MutableStateFlow("")
    override val savedUserNameFlow: StateFlow<String> = _savedUserNameFlow.asStateFlow()

    override val activeLoginStateFlow: StateFlow<Boolean> = combine(
        _loginStateFlow,
        site.loginSupportedFlow
    ) { loggedIn, supported -> loggedIn && supported }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val isActiveLogin: Boolean get() = activeLoginStateFlow.value

    override val isLoggedIn: Boolean get() = _loginStateFlow.value

    override val loginCookie: SessionCookie get() = _loginCookieFlow.value

    override val cloudFlareCookie: SessionCookie get() = _cloudFlareCookieFlow.value

    override val loginCookieDomain: String get() = loginCookieDomainValue

    /**
     * 人机校验 Cookie 的来源域名。
     *
     * 历史版本把它借存在登录域名上（当时校验只能从登录页顺带产生），因此这里保留回退，
     * 让老用户的 `cf_clearance` 不至于因为改字段名而失效。
     */
    override val cloudFlareCookieDomain: String
        get() = cloudFlareCookieDomainValue.ifBlank { loginCookieDomainValue }

    override val savedUserId: String get() = _savedUserIdFlow.value

    override val savedUserName: String get() = _savedUserNameFlow.value

    init {
        readSessionValues()
        _savedUserIdFlow.value = storage.readString(PreferenceKeys.SAVED_USER_ID, "").orEmpty()
        _savedUserNameFlow.value = storage.readString(PreferenceKeys.SAVED_USER_NAME, "").orEmpty()
        storage.onSessionReady { readSessionValues() }
    }

    private fun readSessionValues() {
        _loginStateFlow.value = storage.readBoolean(PreferenceKeys.ALREADY_LOGIN, false)
        _loginCookieFlow.value = SessionCookie(storage.readString(PreferenceKeys.LOGIN_COOKIE, "").orEmpty())
        loginCookieDomainValue = storage.readString(PreferenceKeys.LOGIN_COOKIE_DOMAIN, "").orEmpty().lowercase()
        _cloudFlareCookieFlow.value =
            SessionCookie(storage.readString(PreferenceKeys.CF_COOKIE, "").orEmpty())
        cloudFlareCookieDomainValue =
            storage.readString(PreferenceKeys.CF_COOKIE_DOMAIN, "").orEmpty().lowercase()
        migrateLegacyLoginCookieDomain()
    }

    override fun saveLogin(
        cookieString: String,
        userId: String?,
        sourceDomain: String?
    ): SessionSaveResult {
        val safeCookie = cookieString.take(8192)
        val id = userId ?: extractUserId(safeCookie)
        // 会话必须与来源域名绑定：CookieJar 只会把它注入回这个 host。
        // 未显式指定时取当前 baseUrl 的 host（手填 Cookie / 透传场景），调用方无法提供时该值为空，
        // 此时宁可不注入，也不能把它发给用户后来改成的任意镜像站。
        val domain = sourceDomain?.trim()?.lowercase()?.ifBlank { null } ?: currentBaseHost()
        val sessionReady = storage.isSessionReady()
        val committed = storage.writeBlocking {
            putBoolean(PreferenceKeys.ALREADY_LOGIN, true)
            putString(PreferenceKeys.LOGIN_COOKIE, safeCookie)
            putString(PreferenceKeys.SAVED_USER_ID, id)
            putString(PreferenceKeys.LOGIN_COOKIE_DOMAIN, domain)
        }
        if (!committed) {
            AppLogger.logError(
                "Preferences",
                if (storage.hasSessionFailed()) {
                    "加密存储不可用，登录凭据仅保留在内存中（不落盘），重启后需重新登录"
                } else {
                    "登录凭据同步落盘失败，仅保留在内存中"
                }
            )
        }
        // 换账号后本地缓存的昵称属于上一个身份：不清掉，抽屉会一直显示别人的名字 ——
        // 而 ProfileViewModel.refreshAccountName() 看到昵称非空就不再拉取，错误的名字会一直留着。
        if (_savedUserNameFlow.value.isNotBlank()) {
            storage.write { remove(PreferenceKeys.SAVED_USER_NAME) }
            _savedUserNameFlow.value = ""
        }
        _loginStateFlow.value = true
        _loginCookieFlow.value = SessionCookie(safeCookie)
        loginCookieDomainValue = domain
        _savedUserIdFlow.value = id
        return when {
            committed -> SessionSaveResult.PERSISTED
            storage.hasSessionFailed() || !sessionReady -> SessionSaveResult.MEMORY_ONLY
            else -> SessionSaveResult.FAILED
        }
    }

    override fun saveUserId(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        // 纯数字用户标识不属于凭据，无论存储是否加密都落盘，避免界面在重启后丢失展示信息
        storage.write { putString(PreferenceKeys.SAVED_USER_ID, clean) }
        _savedUserIdFlow.value = clean
    }

    override fun saveUserName(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        storage.write { putString(PreferenceKeys.SAVED_USER_NAME, clean) }
        _savedUserNameFlow.value = clean
    }

    override fun saveCloudFlareCookie(cookieString: String, sourceDomain: String?): SessionSaveResult {
        val safe = cookieString.take(8192)
        // 域名只认这次校验实际发生的站点：改了 baseUrl 换镜像后，旧 Cookie 不应被发到新域名
        val domain = sourceDomain?.trim()?.lowercase()?.ifBlank { null }
        val sessionReady = storage.isSessionReady()
        val committed = storage.writeBlocking {
            putString(PreferenceKeys.CF_COOKIE, safe)
            if (domain != null) putString(PreferenceKeys.CF_COOKIE_DOMAIN, domain)
        }
        if (!committed) {
            AppLogger.logError(
                "Preferences",
                if (storage.hasSessionFailed()) {
                    "加密存储不可用，Cloudflare Cookie 仅保留在内存中（不落盘）"
                } else {
                    "Cloudflare Cookie 同步落盘失败，仅保留在内存中"
                }
            )
        }
        if (domain != null) cloudFlareCookieDomainValue = domain
        _cloudFlareCookieFlow.value = SessionCookie(safe)
        return when {
            committed -> SessionSaveResult.PERSISTED
            storage.hasSessionFailed() || !sessionReady -> SessionSaveResult.MEMORY_ONLY
            else -> SessionSaveResult.FAILED
        }
    }

    override fun clearSession(): Boolean {
        // 必须 commit 而非 apply：apply 是异步落盘，进程在写盘前被杀就会出现
        // 「内存已登出、磁盘仍留会话」，重启后表现为已登出却仍带着旧会话。
        val cleared = storage.writeBlocking {
            remove(PreferenceKeys.ALREADY_LOGIN)
            remove(PreferenceKeys.LOGIN_COOKIE)
            remove(PreferenceKeys.CF_COOKIE)
            remove(PreferenceKeys.CF_COOKIE_DOMAIN)
            remove(PreferenceKeys.LOGIN_COOKIE_DOMAIN)
            remove(PreferenceKeys.SAVED_USER_ID)
            remove(PreferenceKeys.SAVED_USER_NAME)
            remove(PreferenceKeys.PLAYLIST_CSRF_TOKEN)
            remove(PreferenceKeys.PLAYLIST_MIGRATION_DONE)
            remove(PreferenceKeys.SUBSCRIPTION_SEEN_IDS)
        }
        _loginStateFlow.value = false
        _loginCookieFlow.value = SessionCookie("")
        _cloudFlareCookieFlow.value = SessionCookie("")
        cloudFlareCookieDomainValue = ""
        loginCookieDomainValue = ""
        _savedUserIdFlow.value = ""
        _savedUserNameFlow.value = ""
        return cleared
    }

    override fun extractDisplayEmail(): String = extractDisplayEmail(loginCookie.raw, savedUserId)

    private fun extractDisplayEmail(cookieString: String, userId: String): String {
        if (cookieString.isBlank()) return ""
        for (segment in cookieString.split(';')) {
            val value = segment.substringAfter('=').trim()
            if (value.contains('@')) return value.substringBefore('@').take(12)
        }
        return userId.ifBlank { "H" }.take(8)
    }

    private fun currentBaseHost(): String =
        runCatching { android.net.Uri.parse(site.baseUrl).host?.lowercase() }.getOrNull().orEmpty()

    private fun migrateLegacyLoginCookieDomain() {
        if (loginCookieDomainValue.isNotBlank() || loginCookie.raw.isBlank()) return
        val inferred = currentBaseHost()
        if (inferred.isBlank()) return
        loginCookieDomainValue = inferred
        storage.write { putString(PreferenceKeys.LOGIN_COOKIE_DOMAIN, inferred) }
    }

    private fun extractUserId(cookieString: String): String {
        if (cookieString.isBlank()) return ""
        for (segment in cookieString.split(';')) {
            val name = segment.substringBefore('=').trim()
            val value = segment.substringAfter('=').trim()
            if (name.contains("user_id", ignoreCase = true) ||
                name.contains("uid", ignoreCase = true)
            ) {
                return value
            }
        }
        return ""
    }
}
