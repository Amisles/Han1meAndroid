package app.amisles.hanime.feature.profile

import java.net.URI

/**
 * 登录 WebView 的导航策略。
 *
 * 独立成纯 Kotlin 对象（不用 android.net.Uri）是为了能在 JVM 单测里直接验证：
 * 这份白名单决定了「凭据存放在本 WebView 中的页面可以跳到哪里」，改错的代价是钓鱼面扩大或
 * 登录流程中断，必须有回归测试兜住。
 */
object WebLoginPolicy {

    /** 站点提供登录的官方域名；其余镜像站的登录接口会返回「站点维护中」。 */
    val OFFICIAL_HOSTS = listOf("hanime1.me", "hanimeone.me")

    val LOGIN_URLS = OFFICIAL_HOSTS.map { "https://$it/login" }

    /**
     * 第三方 OAuth 提供方（Google 登录）。
     *
     * 登录按钮指向站内的 /auth/google，服务端再 302 到 accounts.google.com 完成授权。
     * 这条链路里的页面必须在本 WebView 内加载：会话 Cookie 只会写回本 WebView 的
     * CookieManager，跳到系统浏览器登录成功也拿不回来。
     */
    private val OAUTH_HOSTS = listOf(
        "google.com",
        "gstatic.com",
        "googleusercontent.com",
        "googleapis.com",
        "recaptcha.net",
        "g.co",
        "withgoogle.com",
        // Google 单点登录的 YouTube 跳板：授权成功后 Google 会先跳
        // accounts.youtube.com/accounts/SetSID 建立 YouTube 的 SSO Cookie，
        // 再带着 sidt 回到 accounts.google.com，最后才回落到站点的回调地址。
        // 这一跳缺了，code 永远回不到站点 —— 用户看到的就是「卡在这里」。
        "youtube.com",
        "ytimg.com"
    )

    /**
     * 各国/地区的 Google 域名（`google.com.hk`、`google.co.jp`、`google.de` …）。
     *
     * Google 会按账号地区把部分页面跳到本地域名；这些域名不在 [OAUTH_HOSTS] 里，
     * 漏掉就会在授权中途把用户甩进系统浏览器 —— 而中途的页面都绑定了本次会话，出去只剩报错。
     */
    private val GOOGLE_REGIONAL_HOST = Regex("""(^|\.)google\.(com|cat|[a-z]{2})(\.[a-z]{2})?$""")

    /** Cloudflare 人机校验（Turnstile / 挑战页）需在其域名下加载与跳转。 */
    private val CHALLENGE_HOSTS = listOf("cloudflare.com", "cloudflarechallenge.com")

    private val OAUTH_CALLBACK_PATH = Regex("""^/auth/[^/]+/callback/?$""")
    private val GOOGLE_REJECT_PATH = Regex("""(^|/)rejected/?$""")

    /** 解析 host；无法解析时返回 null（不要用「包含某子串」判定域名，查询参数可以被伪造）。 */
    fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun pathOf(url: String): String =
        runCatching { URI(url).path.orEmpty() }.getOrDefault("")

    private fun matchesHost(host: String?, domains: List<String>): Boolean =
        host != null && domains.any { host == it || host.endsWith(".$it") }

    /** 是否为官方域名（含子域）。 */
    fun isOfficialHost(url: String): Boolean = matchesHost(hostOf(url), OFFICIAL_HOSTS)

    /** 是否为第三方登录（OAuth）域名（含子域，含各国 Google 域名）。 */
    fun isOAuthHost(url: String): Boolean {
        val host = hostOf(url) ?: return false
        // containsMatchIn 而非 matches：模式末尾锚定 $，前缀允许子域（accounts.google.com.hk）
        return matchesHost(host, OAUTH_HOSTS) || GOOGLE_REGIONAL_HOST.containsMatchIn(host)
    }

    /** URL 的 scheme（小写）；解析不出时为空串。用于识别 intent:// / market:// 这类非网页跳转。 */
    fun schemeOf(url: String): String =
        runCatching { URI(url).scheme.orEmpty().lowercase() }.getOrDefault("")

    /**
     * 是否允许在登录 WebView 内加载。
     *
     * 只放行官方域名、OAuth 提供方与人机校验域名：其余站点一律交给系统浏览器，
     * 避免「持有登录 Cookie 的 WebView」去渲染任意第三方页面。
     */
    fun isAllowedInWebView(url: String): Boolean =
        matchesHost(hostOf(url), OFFICIAL_HOSTS + CHALLENGE_HOSTS) || isOAuthHost(url)

    fun isLoginPage(url: String): Boolean = isOfficialHost(url) && pathOf(url).contains("/login")

    fun isLogoutUrl(url: String): Boolean = pathOf(url).contains("/logout")

    /**
     * 是否为站内第三方登录入口（/auth/{provider}）。
     *
     * 这类页面只做 302 跳转，落地时会话尚未建立：在这里校验只会得到「仍是游客」的假失败。
     */
    fun isOAuthEntry(url: String): Boolean =
        isOfficialHost(url) && pathOf(url).startsWith("/auth/")

    /**
     * 是否为第三方登录回调（/auth/{provider}/callback）。
     *
     * 服务端在这里用 code 换完 token 并建立会话，因此它出现即代表「本轮 OAuth 走完了」，
     * 是比「Cookie 内容变了」可靠得多的完成信号。
     */
    fun isOAuthCallback(url: String): Boolean =
        isOfficialHost(url) && OAUTH_CALLBACK_PATH.matches(pathOf(url))

    /**
     * Google 是否拒绝了内置浏览器（disallowed_useragent）。
     *
     * Google 不允许在嵌入 WebView 中完成 OAuth 时会跳到 .../signin/rejected，
     * 此时继续等待没有意义，应直接提示用户改用其它登录方式。
     */
    fun isOAuthRejectedPage(url: String): Boolean =
        isOAuthHost(url) && GOOGLE_REJECT_PATH.containsMatchIn(pathOf(url))

    /**
     * Cookie 串里是否出现了会话字段。
     *
     * 注意这只说明「站点给了会话」，游客同样会拿到 laravel_session，
     * 因此不能据此判定登录成功，还须由服务端页面确认身份（见 WebLoginVerification）。
     */
    fun hasSessionCookie(cookie: String?): Boolean =
        cookie != null && (cookie.contains("laravel_session", ignoreCase = true) ||
            cookie.contains("session", ignoreCase = true))
}
