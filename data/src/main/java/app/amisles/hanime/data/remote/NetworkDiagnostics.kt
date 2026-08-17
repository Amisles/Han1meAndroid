package app.amisles.hanime.data.remote

import app.amisles.hanime.data.preferences.Preferences
import java.io.IOException
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.security.cert.X509Certificate

enum class DiagnosticType {
    DNS,
    CONNECTIVITY,
    SSL_CERTIFICATE,
    MIRROR_STATUS,
    LOGIN_STATUS
}

enum class DiagnosticStatus {
    /** 检测通过 */
    OK,
    /** 检测失败 */
    FAIL,
    /** 检测中 */
    RUNNING,
    /** 未检测（如未登录时跳过登录检测） */
    SKIPPED
}

data class DiagnosticResult(
    val type: DiagnosticType,
    val status: DiagnosticStatus,
    val title: String,
    val detail: String,
    /** 请求耗时（毫秒），仅 OK/FAIL 状态有意义 */
    val latencyMs: Long? = null,
    val suggestion: String? = null
)

/**
 * 站点可用性诊断器：检测 DNS、超时、证书、镜像 GATE、登录 cookie 有效性等常见问题。
 *
 * 所有检测均在 IO 线程执行，返回 [DiagnosticResult] 列表供 UI 展示。
 * 检测顺序：DNS → 连接 → SSL → 镜像状态 → 登录状态（未登录则跳过）。
 */
class NetworkDiagnostics {

    private val uaString =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    /**
     * 运行全部诊断项，返回按检测顺序排列的结果列表。
     */
    suspend fun runAll(): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticResult>()
        val baseUrl = Preferences.baseUrl
        val host = runCatching { URL(baseUrl).host }.getOrNull() ?: ""

        // 1. DNS 解析
        val dnsResult = checkDns(host)
        results.add(dnsResult)

        // DNS 失败则后续连接类检测无法进行
        if (dnsResult.status != DiagnosticStatus.OK) {
            results.add(skipConnectivity(baseUrl))
            results.add(skipSsl(baseUrl))
            results.add(skipMirror(baseUrl))
            results.add(skipLogin())
            return@withContext results
        }

        // 2. TCP 连接 + HTTP 可达性
        val connResult = checkConnectivity(baseUrl)
        results.add(connResult)

        // 3. SSL 证书（仅 HTTPS）
        val sslResult = checkSslCertificate(baseUrl)
        results.add(sslResult)

        // 4. 镜像 GATE 状态
        val mirrorResult = checkMirrorStatus(baseUrl)
        results.add(mirrorResult)

        // 5. 登录状态（未登录则跳过）
        val loginResult = checkLoginStatus()
        results.add(loginResult)

        results
    }

    /**
     * DNS 解析检测：尝试解析域名，失败说明 DNS 被污染或域名错误。
     */
    private fun checkDns(host: String): DiagnosticResult {
        if (host.isBlank()) {
            return DiagnosticResult(
                type = DiagnosticType.DNS,
                status = DiagnosticStatus.FAIL,
                title = "DNS 解析",
                detail = "官网地址格式无效，无法提取域名",
                suggestion = "请在设置中检查官网网址是否正确"
            )
        }
        val start = System.currentTimeMillis()
        return try {
            val addresses = InetAddress.getAllByName(host)
            val latency = System.currentTimeMillis() - start
            val ipList = addresses.take(3).joinToString(", ") { it.hostAddress }
            DiagnosticResult(
                type = DiagnosticType.DNS,
                status = DiagnosticStatus.OK,
                title = "DNS 解析",
                detail = "域名 $host 解析成功：$ipList",
                latencyMs = latency
            )
        } catch (e: java.net.UnknownHostException) {
            DiagnosticResult(
                type = DiagnosticType.DNS,
                status = DiagnosticStatus.FAIL,
                title = "DNS 解析",
                detail = "无法解析域名 $host：${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "DNS 可能被污染，请尝试切换网络环境、使用 VPN，或在设置中切换其他镜像站地址"
            )
        } catch (e: SecurityException) {
            DiagnosticResult(
                type = DiagnosticType.DNS,
                status = DiagnosticStatus.FAIL,
                title = "DNS 解析",
                detail = "DNS 查询异常：${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络连接后重试"
            )
        }
    }

    /**
     * 连接可达性检测：发送 HTTP HEAD 请求，测量响应时间与状态码。
     */
    private fun checkConnectivity(baseUrl: String): DiagnosticResult {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(baseUrl)
                .head()
                .header("User-Agent", uaString)
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                DiagnosticResult(
                    type = DiagnosticType.CONNECTIVITY,
                    status = DiagnosticStatus.OK,
                    title = "连接可达性",
                    detail = "HTTP ${response.code}，耗时 ${latency}ms",
                    latencyMs = latency
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            DiagnosticResult(
                type = DiagnosticType.CONNECTIVITY,
                status = DiagnosticStatus.FAIL,
                title = "连接可达性",
                detail = "连接超时：${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "服务器响应过慢或网络不通，请检查网络后重试"
            )
        } catch (e: java.net.ConnectException) {
            DiagnosticResult(
                type = DiagnosticType.CONNECTIVITY,
                status = DiagnosticStatus.FAIL,
                title = "连接可达性",
                detail = "连接被拒绝：${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "服务器可能离线，请尝试切换镜像站"
            )
        } catch (e: IOException) {
            DiagnosticResult(
                type = DiagnosticType.CONNECTIVITY,
                status = DiagnosticStatus.FAIL,
                title = "连接可达性",
                detail = "连接异常：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络连接"
            )
        } catch (e: IllegalArgumentException) {
            DiagnosticResult(
                type = DiagnosticType.CONNECTIVITY,
                status = DiagnosticStatus.FAIL,
                title = "连接可达性",
                detail = "连接异常：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络连接"
            )
        }
    }

    /**
     * SSL 证书检测：仅 HTTPS 地址才检测，验证证书链是否有效。
     */
    private fun checkSslCertificate(baseUrl: String): DiagnosticResult {
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            return DiagnosticResult(
                type = DiagnosticType.SSL_CERTIFICATE,
                status = DiagnosticStatus.SKIPPED,
                title = "SSL 证书",
                detail = "当前地址非 HTTPS，跳过证书检测"
            )
        }
        val start = System.currentTimeMillis()
        return try {
            val url = URL(baseUrl)
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", uaString)
            }
            conn.connect()
            val latency = System.currentTimeMillis() - start
            val certs = conn.serverCertificates
            val certInfo = if (certs != null && certs.isNotEmpty()) {
                val cert = certs[0] as X509Certificate
                "颁发者：${cert.issuerX500Principal.name.take(40)}"
            } else {
                "证书信息不可用"
            }
            conn.disconnect()
            DiagnosticResult(
                type = DiagnosticType.SSL_CERTIFICATE,
                status = DiagnosticStatus.OK,
                title = "SSL 证书",
                detail = "证书有效，$certInfo",
                latencyMs = latency
            )
        } catch (e: SSLException) {
            DiagnosticResult(
                type = DiagnosticType.SSL_CERTIFICATE,
                status = DiagnosticStatus.FAIL,
                title = "SSL 证书",
                detail = "证书验证失败：${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "证书可能已过期或不被信任，请勿在该站点输入账号密码，尝试切换镜像站"
            )
        } catch (e: IOException) {
            DiagnosticResult(
                type = DiagnosticType.SSL_CERTIFICATE,
                status = DiagnosticStatus.FAIL,
                title = "SSL 证书",
                detail = "SSL 检测异常：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络或切换镜像站"
            )
        } catch (e: ClassCastException) {
            DiagnosticResult(
                type = DiagnosticType.SSL_CERTIFICATE,
                status = DiagnosticStatus.FAIL,
                title = "SSL 证书",
                detail = "SSL 检测异常：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络或切换镜像站"
            )
        }
    }

    /**
     * 镜像 GATE 状态检测：访问 /enter 路径，验证镜像站是否可用。
     * 镜像站根路径 / 永远返回 500，真实内容在 /enter。
     */
    private fun checkMirrorStatus(baseUrl: String): DiagnosticResult {
        val enterUrl = baseUrl.trimEnd('/') + "/enter"
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(enterUrl)
                .get()
                .header("User-Agent", uaString)
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                when (response.code) {
                    in 200..299 -> DiagnosticResult(
                        type = DiagnosticType.MIRROR_STATUS,
                        status = DiagnosticStatus.OK,
                        title = "镜像站状态",
                        detail = "/enter 返回 HTTP ${response.code}，镜像站可用",
                        latencyMs = latency
                    )
                    500 -> DiagnosticResult(
                        type = DiagnosticType.MIRROR_STATUS,
                        status = DiagnosticStatus.FAIL,
                        title = "镜像站状态",
                        detail = "/enter 返回 HTTP 500，镜像站 GATE 异常",
                        latencyMs = latency,
                        suggestion = "该镜像站可能已失效，请在设置中切换其他镜像站或官方域名"
                    )
                    else -> DiagnosticResult(
                        type = DiagnosticType.MIRROR_STATUS,
                        status = DiagnosticStatus.FAIL,
                        title = "镜像站状态",
                        detail = "/enter 返回 HTTP ${response.code}，镜像站异常",
                        latencyMs = latency,
                        suggestion = "镜像站状态异常，请尝试切换其他镜像站"
                    )
                }
            }
        } catch (e: IOException) {
            DiagnosticResult(
                type = DiagnosticType.MIRROR_STATUS,
                status = DiagnosticStatus.FAIL,
                title = "镜像站状态",
                detail = "镜像站检测失败：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络或切换镜像站"
            )
        } catch (e: IllegalArgumentException) {
            DiagnosticResult(
                type = DiagnosticType.MIRROR_STATUS,
                status = DiagnosticStatus.FAIL,
                title = "镜像站状态",
                detail = "镜像站检测失败：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络或切换镜像站"
            )
        }
    }

    /**
     * 登录状态检测：使用已保存的 cookie 请求 /login 页面，
     * 如果被重定向到非 /login 页面说明 cookie 有效；如果停留在 /login 说明登录已失效。
     * 未登录时跳过此检测。
     */
    private fun checkLoginStatus(): DiagnosticResult {
        if (!Preferences.isAlreadyLogin) {
            return DiagnosticResult(
                type = DiagnosticType.LOGIN_STATUS,
                status = DiagnosticStatus.SKIPPED,
                title = "登录状态",
                detail = "当前未登录，跳过登录检测"
            )
        }
        val baseUrl = Preferences.baseUrl
        val loginUrl = baseUrl.trimEnd('/') + "/login"
        // 使用不跟随重定向的 client，以便判断是否被重定向
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(loginUrl)
                .get()
                .header("User-Agent", uaString)
                .header("Cookie", Preferences.loginCookie.cookie)
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                when {
                    response.code in 300..399 -> DiagnosticResult(
                        type = DiagnosticType.LOGIN_STATUS,
                        status = DiagnosticStatus.OK,
                        title = "登录状态",
                        detail = "登录 cookie 有效（被重定向，HTTP ${response.code}）",
                        latencyMs = latency
                    )
                    response.code == 200 -> DiagnosticResult(
                        type = DiagnosticType.LOGIN_STATUS,
                        status = DiagnosticStatus.FAIL,
                        title = "登录状态",
                        detail = "登录 cookie 可能已失效（仍停留在 /login 页面）",
                        latencyMs = latency,
                        suggestion = "请在我的页面重新登录"
                    )
                    else -> DiagnosticResult(
                        type = DiagnosticType.LOGIN_STATUS,
                        status = DiagnosticStatus.FAIL,
                        title = "登录状态",
                        detail = "登录检测异常：HTTP ${response.code}",
                        latencyMs = latency,
                        suggestion = "请尝试重新登录"
                    )
                }
            }
        } catch (e: IOException) {
            DiagnosticResult(
                type = DiagnosticType.LOGIN_STATUS,
                status = DiagnosticStatus.FAIL,
                title = "登录状态",
                detail = "登录检测失败：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络后重试"
            )
        } catch (e: IllegalArgumentException) {
            DiagnosticResult(
                type = DiagnosticType.LOGIN_STATUS,
                status = DiagnosticStatus.FAIL,
                title = "登录状态",
                detail = "登录检测失败：${e.javaClass.simpleName} - ${e.message}",
                latencyMs = System.currentTimeMillis() - start,
                suggestion = "请检查网络后重试"
            )
        }
    }

    // 跳过项工厂方法（DNS 失败时后续检测无意义）
    private fun skipConnectivity(baseUrl: String) = DiagnosticResult(
        type = DiagnosticType.CONNECTIVITY,
        status = DiagnosticStatus.SKIPPED,
        title = "连接可达性",
        detail = "DNS 解析失败，跳过连接检测"
    )

    private fun skipSsl(baseUrl: String) = DiagnosticResult(
        type = DiagnosticType.SSL_CERTIFICATE,
        status = DiagnosticStatus.SKIPPED,
        title = "SSL 证书",
        detail = "DNS 解析失败，跳过证书检测"
    )

    private fun skipMirror(baseUrl: String) = DiagnosticResult(
        type = DiagnosticType.MIRROR_STATUS,
        status = DiagnosticStatus.SKIPPED,
        title = "镜像站状态",
        detail = "DNS 解析失败，跳过镜像站检测"
    )

    private fun skipLogin() = DiagnosticResult(
        type = DiagnosticType.LOGIN_STATUS,
        status = DiagnosticStatus.SKIPPED,
        title = "登录状态",
        detail = "DNS 解析失败，跳过登录检测"
    )
}
