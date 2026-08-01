package app.amisles.hanime.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Cookie 管理 Jar
 * 支持：
 * - 从 Set-Cookie 响应头解析并存储 Cookie
 * - 为请求自动注入已存储的 Cookie
 * - 持久化到 SharedPreferences（可选）
 */
class HCookieJar : CookieJar {

    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    /**
     * 存储响应中的 Cookie
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domain = url.host
        val existing = cookieStore[domain] ?: mutableListOf()
        for (cookie in cookies) {
            // 替换同名 Cookie
            existing.removeAll { it.name == cookie.name }
            existing.add(cookie)
        }
        cookieStore[domain] = existing
    }

    /**
     * 为请求加载已存储的 Cookie
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val domain = url.host
        val cookies = cookieStore[domain] ?: return emptyList()
        // 移除已过期 Cookie
        val validCookies = cookies.filter { it.expiresAt > System.currentTimeMillis() }
        return validCookies
    }

    /**
     * 手动设置 Cookie（用于登录态恢复）
     */
    fun setCookie(domain: String, cookieString: String) {
        val cookies = parseCookieString(domain, cookieString)
        cookieStore[domain] = cookies.toMutableList()
    }

    /**
     * 获取指定域名的所有 Cookie
     */
    fun getCookies(domain: String): String {
        return cookieStore[domain]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
    }

    /**
     * 清除所有 Cookie
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