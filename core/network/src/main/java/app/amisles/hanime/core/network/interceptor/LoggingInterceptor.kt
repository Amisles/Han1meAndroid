package app.amisles.hanime.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 日志拦截器，受 isDebug 控制
 */
class LoggingInterceptor(
    private val isDebug: Boolean = true
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        if (isDebug) {
            android.util.Log.d("OkHttp", "--> ${request.method} ${request.url}")
            request.headers.forEach { (name, value) ->
                android.util.Log.d("OkHttp", "  $name: $value")
            }
        }

        val startTime = System.nanoTime()
        val response = chain.proceed(request)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        if (isDebug) {
            android.util.Log.d("OkHttp", "<-- ${response.code} ${request.url} (${elapsedMs}ms)")
        }

        return response
    }
}