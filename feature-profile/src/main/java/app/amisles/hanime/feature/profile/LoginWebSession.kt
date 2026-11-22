package app.amisles.hanime.feature.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R

/** 两次会话校验的最小间隔：站内来回点几下不该反复打校验请求。 */
private const val VERIFY_MIN_INTERVAL_MS = 6_000L

/**
 * 登录页底部提示。
 *
 * [canReload] 用于「流程被打断」的场景（外跳被拦、Google 拒绝内置浏览器）：
 * 此时 WebView 可能停在半路的页面，给一个直接重开登录页的出口，免得用户只能退出重进。
 */
internal data class LoginNotice(
    val message: String,
    val canReload: Boolean = false,
    /**
     * 授权途中的非白名单跳转：白名单不可能穷举 Google 的每一次跳转，拦死会让用户卡死，
     * 因此给一个「仍要打开」的出口。URL 由用户点选，未点选前不会加载。
     */
    val openAnywayUrl: String? = null
)

/** 一次外部跳转被拦下时的提示。带上目标 host，出问题时用户能直接说出是哪一跳。 */
internal fun blockedNavigationNotice(context: Context, url: String): String {
    val target = WebLoginPolicy.hostOf(url) ?: url.redactUrlForLog()
    return context.getString(R.string.login_webview_blocked_navigation, target)
}

/**
 * 一轮 WebView 登录的会话状态：Cookie 起点、OAuth 进度、页面加载与校验节流。
 * UI 只渲染这里的状态，平台回调只通过本类改变状态，避免 WebViewClient 闭包散落可变字段。
 */
internal class LoginWebSession(
    private val cookieAtStart: String,
    private val onLoginVerified: (cookies: String, host: String?) -> Unit
) {
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf(false)
    var notice by mutableStateOf<LoginNotice?>(null)
    var oauthInProgress by mutableStateOf(false)
    var oauthRoundTrip by mutableStateOf(false)
    var loginAttempted by mutableStateOf(false)

    private var loginPageSeen = false
    private var loginLandingHandled = false
    private var lastVerifyAt = 0L

    fun showError(message: String) {
        notice = if (oauthRoundTrip || loginAttempted) LoginNotice(message, canReload = true) else null
    }

    // 刻意不做「Cookie 里有 session 就判定成功」：游客也有 laravel_session；必须等官方落地页
    // 且本轮会话确实变化后再向站点确认身份。
    fun verifySessionIfReady(url: String): Boolean {
        val ready =
            WebLoginPolicy.isOfficialHost(url) &&
                !WebLoginPolicy.isLoginPage(url) &&
                !WebLoginPolicy.isLogoutUrl(url) &&
                !WebLoginPolicy.isOAuthEntry(url)
        val cookies = if (ready) CookieManager.getInstance().getCookie(url) else null
        val sessionReplaced = oauthRoundTrip || cookies != cookieAtStart
        val now = SystemClock.elapsedRealtime()
        if (!ready ||
            !WebLoginPolicy.hasSessionCookie(cookies) ||
            !sessionReplaced ||
            now - lastVerifyAt < VERIFY_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastVerifyAt = now
        AppLogger.d(
            "LoginScreen",
            "会话已变化，向站点校验登录状态: host=${WebLoginPolicy.hostOf(url)}"
        )
        onLoginVerified(cookies.orEmpty(), WebLoginPolicy.hostOf(url))
        return true
    }

    fun onPageStarted(url: String) {
        if (WebLoginPolicy.isOAuthEntry(url) || WebLoginPolicy.isOAuthHost(url)) {
            oauthInProgress = true
        }
        if (WebLoginPolicy.isOAuthCallback(url)) oauthRoundTrip = true
        onLoginLanding(url)
    }

    fun onVisitedHistory(url: String) {
        if (WebLoginPolicy.isOAuthHost(url)) oauthInProgress = true
        if (!oauthRoundTrip && WebLoginPolicy.isOAuthCallback(url)) {
            oauthRoundTrip = true
            AppLogger.d("LoginScreen", "检测到第三方登录回调，等待落地页后校验会话")
        }
        onLoginLanding(url)
    }

    fun onPageFinished(url: String) {
        loading = false
        if (verifySessionIfReady(url) && loginPageSeen) loginAttempted = true
    }

    fun onMainFrameError() {
        loading = false
        loadError = true
    }

    fun reloadLogin() {
        notice = null
        oauthInProgress = false
        oauthRoundTrip = false
        loadError = false
        loading = true
    }

    // 表单登录会 302 到落地页：导航开始即校验，不必等首页缩略图全部加载完才关登录页。
    private fun onLoginLanding(url: String) {
        if (WebLoginPolicy.isLoginPage(url)) {
            loginPageSeen = true
            loginLandingHandled = false
            lastVerifyAt = 0L
            return
        }
        if (!loginPageSeen || loginLandingHandled) return
        val started = verifySessionIfReady(url)
        loginLandingHandled = started
        if (started) loginAttempted = true
    }
}

internal class LoginWebViewClient(
    private val context: Context,
    private val session: LoginWebSession
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        // intent:// / market:// 等地址的 host 仍可能是官方域名，必须先判 scheme，否则会被交给系统踢出 App
        val scheme = WebLoginPolicy.schemeOf(url)
        if (scheme != "http" && scheme != "https") {
            AppLogger.w("LoginScreen", "拦截非 http(s) 跳转: ${url.redactUrlForLog()}")
            session.notice = if (WebLoginPolicy.isOAuthHost(url)) {
                LoginNotice(context.getString(R.string.login_webview_oauth_blocked), canReload = true)
            } else {
                LoginNotice(blockedNavigationNotice(context, url), canReload = true)
            }
            return true
        }
        // 官方域名与 Google 授权链路必须留在本 WebView：会话只写回这里的 CookieManager。
        if (WebLoginPolicy.isAllowedInWebView(url)) return false
        // 授权途中的外跳会得到错误页，给用户「仍要打开」的显式出口。
        if (session.oauthInProgress) {
            AppLogger.w("LoginScreen", "授权进行中，拦截外跳: ${url.redactUrlForLog()}")
            session.notice = LoginNotice(
                message = blockedNavigationNotice(context, url),
                canReload = true,
                openAnywayUrl = url
            )
            return true
        }
        // 其余域名不留在持有登录 Cookie 且开着 JS 的 WebView 内，改用系统浏览器但不阻断用户。
        AppLogger.w(
            "LoginScreen",
            "拦截非白名单导航，改用系统浏览器: ${url.redactUrlForLog()}"
        )
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, request.url).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { e ->
            AppLogger.w("LoginScreen", "打开系统浏览器失败: ${e.message}")
        }
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        val currentUrl = url ?: return
        session.onPageStarted(currentUrl)
        if (WebLoginPolicy.isOAuthRejectedPage(currentUrl)) {
            session.notice = LoginNotice(context.getString(R.string.login_webview_oauth_blocked), canReload = true)
        }
    }

    /**
     * 服务端 302 不一定触发 onPageStarted，这里再记一次：
     * 「走过回调」是判断这一轮登录是否真的完成的最可靠信号。
     */
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val currentUrl = url ?: return
        session.onVisitedHistory(currentUrl)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        // 服务端 302 不一定触发 onPageStarted / 历史回调，这里补一次落地校验
        session.onPageFinished(url ?: return)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (request?.isForMainFrame == true) session.onMainFrameError()
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        // 任何证书错误都直接拒绝，避免默认实现被误改为 proceed。
        handler?.cancel()
    }
}
