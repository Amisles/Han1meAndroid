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

    /**
     * 存储代数：每次 clearAll（登出）自增。
     *
     * 登出与响应到达天然并发（页面请求在途时点登出）：在途响应携带的是旧会话的
     * Set-Cookie，若照常入库，刚被 clearAll 清掉的会话 Cookie 会被写回，
     * 登出在本进程内形同虚设。见 [saveFromResponse] 的丢弃逻辑。
     */
    @Volatile
    private var generation = 0L

    /**
     * 请求构建时所处的代数。OkHttp 保证同一次交换的 [loadForRequest] 与
     * [saveFromResponse] 在同一条拦截器链、同一线程上先后执行，因此用 ThreadLocal
     * 即可把响应的 Set-Cookie 关联到「发出请求时」的代数：两者不一致说明请求发出后
     * 发生过登出，携带的是旧会话 Cookie，必须丢弃。
     */
    private val requestGeneration = ThreadLocal<Long>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        requestGeneration.set(generation)
        val host = url.host
        val out = mutableListOf<Cookie>()

        // 从内存存储读取并清理过期 Cookie。
        // 必须原地修改而非整体替换 map 条目，且清理后不得把空列表从 map 摘除：
        // 并发请求可能已持有同一 list 引用，替换或摘除都会让它的追加落在孤儿列表上丢失。
        // 列表身份一经创建永不替换（clearHost/clearAll 的登出语义除外），空列表常驻的内存开销可忽略。
        val storedCookies = cookieMap[host]
        if (storedCookies != null) {
            synchronized(storedCookies) {
                val now = System.currentTimeMillis()
                val iterator = storedCookies.iterator()
                while (iterator.hasNext()) {
                    val cookie = iterator.next()
                    if (cookie.expiresAt > now) {
                        out.add(cookie)
                    } else {
                        iterator.remove()
                    }
                }
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
        // 请求发出后发生过登出：这批 Set-Cookie 属于旧会话，一律丢弃。
        // 正常路径 loadForRequest 必然先于本方法在同线程执行；缺记录时按当前代数放行
        // （防御性兜底：宁可多收一次，不误杀登出之后新发出的请求回传的新会话）。
        val sentGeneration = requestGeneration.get()
        if (sentGeneration != null && sentGeneration != generation) {
            AppLogger.d(
                "HCookieJar",
                "丢弃跨越登出的在途响应 Cookie: host=${url.host} count=${cookies.size}"
            )
            return
        }
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
        // 先推进代数再清空：在途请求的响应此后到达时，其记录的代数已落后，整批丢弃
        generation++
        cookieMap.clear()
    }

}