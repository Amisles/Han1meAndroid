package app.amisles.hanime.data.cookie

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.core.common.util.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局 Cookie 管理器（object 单例）。
 * - 从 Set-Cookie 响应头解析并存储 Cookie
 * - 为请求自动注入已存储的 Cookie + Preferences 中持久化的登录态/Cloudflare Cookie
 * - 过期 Cookie 在请求时自动清理
 * - 线程安全：ConcurrentHashMap + 同步块保护读写
 */
object HCookieJar : CookieJar {

    // 用 ConcurrentHashMap 替代 HashMap，避免并发读写抛出 ConcurrentModificationException
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
                // 有过期 Cookie 时更新存储（移除已过期条目）
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

        // 仅向「会话来源域名」注入登录态 Cookie。
        // 刻意不用当前 baseUrl 判定：baseUrl 是用户可随意修改的镜像站地址，若据此注入，
        // 用户把官网地址改成第三方镜像后，主站会话（laravel_session / remember_web_*）会立刻
        // 被发往镜像站。会话只认签发它的 host，改站点不改变这一点。
        val sessionHost = Preferences.loginCookieDomain
        if (sessionHost.isBlank()) {
            // 未知来源：宁可不注入，也不能把凭据发往不确定的主机
            AppLogger.d("HCookieJar", "登录 Cookie 缺少来源域名，已跳过注入以避免跨域泄漏")
        } else if (host.equals(sessionHost, ignoreCase = true)) {
            out += Preferences.loginCookie.toLoginCookieList(host)
        }

        // 人机校验 Cookie 与登录态无关：未登录用户也可能需要它，因此按自己的域名单独判定
        val challengeHost = Preferences.cloudFlareCookieDomain
        if (challengeHost.isNotBlank() && host.equals(challengeHost, ignoreCase = true)) {
            val challengeCookies = Preferences.cloudFlareCookie.toLoginCookieList(host)
            // 登录 Cookie 里可能残留同一时刻的旧 cf_clearance，两条同名 Cookie 一起下发时
            // 服务端通常只认第一条（旧值），刚校验到的新值就永远不起作用
            val names = challengeCookies.mapTo(HashSet()) { it.name }
            out.removeAll { it.name in names }
            out += challengeCookies
        }

        AppLogger.d("HCookieJar", "loadForRequest host=$host cookies=${out.size}")
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host

        // putIfAbsent 保证并发下创建列表的原子性，避免 getOrPut 的竞态条件
        val existing = cookieMap[host]
        val list = if (existing != null) {
            existing
        } else {
            val newList = mutableListOf<Cookie>()
            val prev = cookieMap.putIfAbsent(host, newList)
            prev ?: newList
        }

        synchronized(list) {
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name && it.path == cookie.path }
                list.add(cookie)
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

}