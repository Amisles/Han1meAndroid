package app.amisles.hanime.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局 HTTP 客户端配置
 */
object HttpClient {

    /**
     * 默认客户端：带重试和通用 Header
     */
    fun createDefault(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 无重定向客户端：用于登录请求，不自动跟随 302
     */
    fun createNoRedirect(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }
}