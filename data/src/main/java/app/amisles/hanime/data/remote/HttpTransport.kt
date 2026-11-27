package app.amisles.hanime.data.remote

import android.content.Context
import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.data.cookie.HCookieJar
import app.amisles.hanime.data.cookie.USER_LANG_COOKIE
import app.amisles.hanime.data.cookie.filterPrintableAscii
import app.amisles.hanime.data.cookie.userLangCookie
import app.amisles.hanime.domain.model.SubtitleLang
import app.amisles.hanime.domain.remote.CloudflareChallenge
import app.amisles.hanime.domain.remote.CloudflareChallengeException
import app.amisles.hanime.domain.result.AppException
import app.amisles.hanime.domain.result.DomainError
import app.amisles.hanime.domain.repository.SiteSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class HttpTransport @Inject constructor(
    @ApplicationContext context: Context,
    private val site: SiteSettings,
    private val cookieJar: HCookieJar
) {
    private val entryInterceptor = EntryInterceptor()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .cookieJar(cookieJar)
        .addInterceptor(entryInterceptor)
        .build()

    init {
        // 历史版本的磁盘 HTTP 缓存可能残留含登录态页面的明文响应，随缓存移除一并清理
        runCatching { context.cacheDir.resolve("http_cache").deleteRecursively() }
    }

    private val noRedirectClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * 登录表单提交专用链路：不注入持久化的登录 Cookie。
     *
     * 旧会话与本次 GET /login 换到的游客会话同名，两条同名 Cookie 一起下发时服务端只认最后一条，
     * 表单里的 CSRF Token 就对不上它所属的会话，表现为「密码正确却一直登录不成功」。
     */
    private val loginClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> =
                    cookieJar.observedCookies(url)

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
                    cookieJar.saveFromResponse(url, cookies)
            })
            .build()
    }

    /**
     * 登录校验专用链路：沿用同一套超时/UA，但摘掉 [HCookieJar]。
     *
     * 校验发生在「会话还没写进偏好存储」的时刻，而 HCookieJar 只会注入偏好里已保存的
     * 登录 Cookie（且按来源域名过滤）；带着它会让校验请求携带旧会话，测出来的结果与
     * WebView 刚拿到的那份会话无关。
     */
    private val verificationClient by lazy {
        client.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .build()
    }

    val baseUrl: String get() = site.baseUrl

    private val uaString get() = BrowserUserAgent.VALUE

    private fun buildRequest(url: String): Request {
        // 脱敏：query 可能含用户搜索词或带签名的直链参数，不得落盘
        AppLogger.log("HttpTransport", "Building request for URL: ${url.redactUrlForLog()}")
        return Request.Builder()
            .url(url)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", baseUrl + "/")
            .get()
            .build()
    }

    /**
     * 指定整页渲染语种的请求链路：Cookie 只在本条链路上覆盖，不影响其它请求。
     *
     * 简繁字幕是两份带各自签名的视频文件，只有让站点按目标语种重渲染页面才能拿到对应直链。
     */
    private fun clientForUserLang(lang: String): OkHttpClient = client.newBuilder()
        .cookieJar(object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookieJar.loadForRequest(url).filterNot { it.name == USER_LANG_COOKIE } +
                    userLangCookie(url.host, lang)

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
                cookieJar.saveFromResponse(url, cookies)
        })
        .build()

    private val userLangClients: Map<String, OkHttpClient> by lazy {
        mapOf(
            SubtitleLang.SIMPLIFIED to clientForUserLang(SubtitleLang.SIMPLIFIED),
            SubtitleLang.TRADITIONAL to clientForUserLang(SubtitleLang.TRADITIONAL)
        )
    }

    private suspend fun executeRequest(
        request: Request,
        target: OkHttpClient = client
    ): String = withContext(Dispatchers.IO) {
        target.newCall(request).execute().use { response ->
            AppLogger.log("HttpTransport", "Response code: ${response.code}")
            if (!response.isSuccessful) {
                val body = response.body.string()
                val host = request.url.host
                if (isCloudflareChallenge(response.code, response.header("cf-mitigated"), body)) {
                    AppLogger.w("HttpTransport", "命中 Cloudflare 人机校验: host=$host code=${response.code}")
                    CloudflareChallenge.requestOnFailure(host)
                    throw CloudflareChallengeException(host)
                }
                throw IOException("Request failed with code ${response.code}")
            }
            val body = response.body.string()
            AppLogger.log("HttpTransport", "Response body length: ${body.length}")
            if (body.isEmpty()) throw IOException("Empty response body")
            body
        }
    }

    /**
     * 是否为 Cloudflare 的人机校验响应（而不是普通的 403 / 地区限制）。
     *
     * 判据只看响应本身：挑战页由 Cloudflare 注入，必然带 `_cf_chl_opt` 等特征串，
     * 或直接给出 `cf-mitigated: challenge` 头；站点自己的 403 页面不含这些标记。
     */
    private fun isCloudflareChallenge(code: Int, mitigated: String?, body: String): Boolean {
        if (code != 403 && code != 503) return false
        if (mitigated.equals("challenge", ignoreCase = true)) return true
        return body.contains("_cf_chl_opt", ignoreCase = true) ||
            body.contains("cf-browser-verification", ignoreCase = true) ||
            body.contains("challenge-platform", ignoreCase = true) ||
            body.contains("cf_chl_", ignoreCase = true)
    }

    suspend fun get(url: String, userLang: String = ""): String =
        executeRequest(buildRequest(url), userLangClients[userLang] ?: client)

    suspend fun fetchLoginPage(): FetchResult = withContext(Dispatchers.IO) {
        AppLogger.log("HttpTransport", "fetchLoginPage called")
        val base = baseUrl
        val url = "$base/login"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        val (code, html) = client.newCall(req).execute().use { resp ->
            val c = resp.code
            val h = if (c in 200..399) resp.body.string() else ""
            c to h
        }
        AppLogger.log("HttpTransport", "fetchLoginPage code=$code url=$base")
        if (code in 200..399) {
            FetchResult(html, base)
        } else {
            throw IOException("login page HTTP $code")
        }
    }

    suspend fun postForm(
        path: String,
        form: FormBody,
        csrfToken: String,
        label: String,
        errorCode: DomainError
    ): String = withContext(Dispatchers.IO) {
        val base = baseUrl
        val request = Request.Builder()
            .url(base + path)
            .post(form)
            .header("User-Agent", uaString)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Referer", base + "/")
            .header("Origin", base)
            .header("X-CSRF-TOKEN", csrfToken)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        AppLogger.log("HttpTransport", label + " -> " + base + path)
        client.newCall(request).execute().use { response ->
            val code = response.code
            AppLogger.log("HttpTransport", label + " response code: " + code)
            val body = response.body.string()
            if (!response.isSuccessful) throw AppException(errorCode)
            if (body.isEmpty()) throw AppException(errorCode)
            body
        }
    }

    suspend fun postLoginForm(
        csrfToken: String,
        email: String,
        password: String
    ): LoginFormResult = withContext(Dispatchers.IO) {
        AppLogger.log("HttpTransport", "postLoginForm called (email length=${email.length})")
        val base = baseUrl
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("email", email)
            .add("password", password)
            .build()
        val req = Request.Builder()
            .url("$base/login")
            .post(form)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "$base/login")
            .header("Origin", base)
            .header("X-CSRF-TOKEN", csrfToken)
            .build()
        loginClient.newCall(req).execute().use { resp ->
            val code = resp.code
            val cookies = resp.headers("Set-Cookie")
            var body: String? = null
            if (code in 200..299) {
                body = resp.body.string()
            }
            val location = resp.header("Location")
            AppLogger.log(
                "HttpTransport",
                "postLoginForm code=$code setCookies=${cookies.size} location=${location?.redactUrlForLog()} base=$base"
            )
            LoginFormResult(code, cookies, body, location)
        }
    }

    suspend fun fetchHomePageWithCookie(cookie: String, domain: String): String =
        withContext(Dispatchers.IO) {
            val url = "https://$domain/"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", uaString)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Cookie", cookie.filterPrintableAscii())
                .header("Referer", url)
                .get()
                .build()
            verificationClient.newCall(req).execute().use { resp ->
                AppLogger.log("HttpTransport", "verify session host=$domain code=${resp.code}")
                if (!resp.isSuccessful) throw IOException("verify failed with code ${resp.code}")
                resp.body.string()
            }
        }

    suspend fun updateAccountProfile(
        userId: String,
        csrfToken: String,
        name: String,
        email: String
    ): Int = withContext(Dispatchers.IO) {
        AppLogger.log("HttpTransport", "updateAccountProfile called, userId: $userId")
        val base = baseUrl
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("_method", "patch")
            .add("type", "profile")
            .add("name", name)
            .add("email", email)
            .build()
        val req = Request.Builder()
            .url("$base/user/$userId")
            .post(form)
            .header("User-Agent", uaString)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Referer", "$base/user/$userId/edit")
            .header("Origin", base)
            .header("X-CSRF-TOKEN", csrfToken)
            .build()
        noRedirectClient.newCall(req).execute().use { resp -> resp.code }
    }
}

data class FetchResult(val html: String, val baseUrl: String)

data class LoginFormResult(
    val code: Int,
    val setCookies: List<String>,
    val body: String?,
    val location: String?
)
