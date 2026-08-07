package app.amisles.hanime.data.remote

import app.amisles.hanime.core.common.util.AppLogger
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 入口拦截器
 *
 * 镜像站（hanime1.me / hanimeone.me 之外的域名）的根路径 `/` 是一个门页，
 * 永远返回 500 + “继续访问”按钮页；真正的首页内容位于 `/enter`。
 * `/search`、`/watch`、`/login` 等其他路径则正常可访问。
 *
 * 因此对非官方域名的根路径请求，直接重写为 `/enter`，无需探测、无需 cookie。
 * 官方域名保持原样。
 */
class EntryInterceptor(
    private val officialDomains: List<String> = DEFAULT_OFFICIAL_DOMAINS
) : Interceptor {

    companion object {
        val DEFAULT_OFFICIAL_DOMAINS = listOf("hanime1.me", "hanimeone.me")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // 官方域名：根路径即首页，无需重写
        if (isOfficialDomain(host)) {
            return chain.proceed(request)
        }

        // 非官方域名：根路径 / 是门页（500），真正首页在 /enter
        if (request.url.encodedPath == "/") {
            val rewrittenUrl = request.url.newBuilder()
                .encodedPath("/enter")
                .build()
            AppLogger.log("EntryInterceptor", "Rewriting root path to /enter for host: $host")
            return chain.proceed(request.newBuilder().url(rewrittenUrl).build())
        }

        return chain.proceed(request)
    }

    private fun isOfficialDomain(host: String): Boolean =
        officialDomains.any { host == it || host.endsWith(".$it") }
}
