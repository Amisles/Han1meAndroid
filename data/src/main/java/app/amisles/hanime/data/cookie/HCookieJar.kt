package app.amisles.hanime.data.cookie

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.core.common.util.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局 Cookie 管理器（object 单例）
 * 特性：
 * - 从 Set-Cookie 响应头解析并存储 Cookie
 * - 为请求自动注入已存储的 Cookie + Preferences 中持久化的登录态/Cloudflare Cookie
 * - 过期 Cookie 在请求时自动清理
 * - 线程安全：使用 ConcurrentHashMap + 同步块保护读写
 */
object HCookieJar : CookieJar {

    // 使用 ConcurrentHashMap 替代 HashMap，避免并发读写抛出 ConcurrentModificationException
    private val cookieMap: ConcurrentHashMap<String, MutableList<Cookie>> = ConcurrentHashMap()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val out = mutableListOf<Cookie>()

        // 从内存存储读取并清理过期 Cookie
        val storedCookies = cookieMap[host]
        if (storedCookies != null) {
            synchronized(storedCookies) {
                val now = System.currentTimeMillis()
                val validCookies = storedCookies.filter { it.expiresAt > now }
                // 如果有过期 Cookie，更新存储（移除已过期的条目）
                if (validCookies.size != storedCookies.size) {
                    if (validCookies.isEmpty()) {
                        cookieMap.remove(host)
                    } else {
                        cookieMap[host] = validCookies.toMutableList()
                    }
                }
                out.addAll(validCookies)
            }
        }

        // 仅向 baseUrl 所属域名注入登录态 Cookie，避免会话被带向跨域主机造成泄漏
        if (isBaseUrlHost(host)) {
            out += Preferences.loginCookie.toLoginCookieList(host)
            out += Preferences.cloudFlareCookie.toLoginCookieList(host)
        }

        AppLogger.d("HCookieJar", "loadForRequest host=$host cookies=${out.size}")
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host

        // 使用 putIfAbsent 保证并发下创建列表的原子性，避免 getOrPut 的竞态条件
        val existing = cookieMap[host]
        val list = if (existing != null) {
            existing
        } else {
            val newList = mutableListOf<Cookie>()
            val prev = cookieMap.putIfAbsent(host, newList)
            prev ?: newList
        }

        synchronized(list) {
            list.clear()
            list.addAll(cookies)
            // 仅对 baseUrl 域名追加持久化登录 Cookie，避免存储到跨域主机下
            if (isBaseUrlHost(host)) {
                list.addAll(Preferences.loginCookie.toLoginCookieList(host))
            }
        }
        AppLogger.d("HCookieJar", "saveFromResponse host=$host setCookies=${cookies.size}")
    }

    fun clearHost(host: String) {
        cookieMap.remove(host)
    }

    fun clearAll() {
        cookieMap.clear()
    }

    /**
     * 判断请求 host 是否属于当前 baseUrl 域名（含子域），用于限定登录态 Cookie 的注入范围。
     */
    private fun isBaseUrlHost(host: String): Boolean {
        val baseHost = runCatching {
            android.net.Uri.parse(Preferences.baseUrl).host?.lowercase()
        }.getOrNull() ?: return false
        return host == baseHost || host.endsWith(".$baseHost")
    }
}