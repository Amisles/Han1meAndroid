package app.amisles.hanime.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 浏览器伪装拦截器，添加 UA/Referer/Origin Header
 */
class BrowserInterceptor : Interceptor {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        val isHanimeRequest = url.contains("hanime1.me") ||
                               url.contains("hanimeone.me") ||
                               url.contains("hanime1.")

        val newRequest = originalRequest.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", ACCEPT)
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .apply {
                if (isHanimeRequest) {
                    header("Referer", "https://hanimeone.me/")
                    header("Origin", "https://hanimeone.me")
                }
            }
            .build()

        return chain.proceed(newRequest)
    }
}
