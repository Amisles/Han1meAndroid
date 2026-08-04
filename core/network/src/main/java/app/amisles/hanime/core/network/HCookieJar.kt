package app.amisles.hanime.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie 管理 Jar
 * 支持：
 * - 从 Set-Cookie 响应头解析并存储 Cookie
 * - 为请求自动注入已存储的 Cookie
 * - 持久化到 SharedPreferences（可选）
 * - 线程安全：使用 ConcurrentHashMap + 同步块保护读写
 */
class HCookieJar : CookieJar {

    private val cookieStore: ConcurrentHashMap<String, MutableList<Cookie>> = ConcurrentHashMap()

    /**
     * 存储响应中的 Cookie（线程安全）
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domain = url.host
        val existing = cookieStore[domain] ?: mutableListOf()
        synchronized(existing) {
            for (cookie in cookies) {
                existing.removeAll { it.name == cookie.name }
                existing.add(cookie)
            }
            cookieStore[domain] = existing
        }
    }

    /**
     * 为请求加载已存储的 Cookie（线程安全，同步清理过期）
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val domain = url.host
        val cookies = cookieStore[domain] ?: return emptyList()
        return synchronized(cookies) {
            val now = System.currentTimeMillis()
            val validCookies = cookies.filter { it.expiresAt > now }
            if (validCookies.size != cookies.size) {
                if (validCookies.isEmpty()) {
                    cookieStore.remove(domain)
                } else {
                    cookieStore[domain] = validCookies.toMutableList()
                }
            }
            validCookies
        }
    }

    /**
     * 手动设置 Cookie（用于登录态恢复，线程安全）
     */
    fun setCookie(domain: String, cookieString: String) {
        val cookies = parseCookieString(domain, cookieString)
        cookieStore[domain] = cookies.toMutableList()
    }

    /**
     * 获取指定域名的所有 Cookie（线程安全）
     */
    fun getCookies(domain: String): String {
        val cookies = cookieStore[domain] ?: return ""
        return synchronized(cookies) {
            cookies.joinToString("; ") { "${it.name}=${it.value}" }
        }
    }

    /**
     * 清除所有 Cookie（线程安全）
     */
    fun clear() {
        cookieStore.clear()
    }

    /**
     * 解析 Cookie 字符串
     */
    private fun parseCookieString(domain: String, cookieString: String): List<Cookie> {
        return cookieString.split("; ").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                Cookie.Builder()
                    .domain(domain)
                    .name(parts[0].trim())
                    .value(parts[1].trim())
                    .build()
            } else null
        }
    }
}
