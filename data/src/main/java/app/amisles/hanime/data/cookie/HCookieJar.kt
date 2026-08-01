package app.amisles.hanime.data.cookie

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.core.common.util.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

object HCookieJar : CookieJar {

    private val cookieMap: MutableMap<String, MutableList<Cookie>> = HashMap()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val out = mutableListOf<Cookie>()

        cookieMap[host]?.let { temp ->
            synchronized(temp) { out.addAll(temp) }
        }

        out += Preferences.loginCookie.toLoginCookieList(host)
        out += Preferences.cloudFlareCookie.toLoginCookieList(host)

        AppLogger.d("HCookieJar", "loadForRequest host=$host cookies=${out.size}")
        return out
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = cookieMap.getOrPut(host) { mutableListOf() }
        synchronized(list) {
            list.clear()
            list.addAll(cookies)
            list.addAll(Preferences.loginCookie.toLoginCookieList(host))
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