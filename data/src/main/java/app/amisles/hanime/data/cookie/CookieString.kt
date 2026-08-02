package app.amisles.hanime.data.cookie

import okhttp3.Cookie

@JvmInline
value class CookieString(val cookie: String)

fun String.filterPrintableAscii(): String =
    filter { it.code in 0x20..0x7E }

// 默认请求语言标识，随登录 Cookie 一并下发以保持服务端内容语言一致
private const val DEFAULT_USER_LANG = "zhs"

fun CookieString.toLoginCookieList(domain: String): List<Cookie> {
    val list = mutableListOf<Cookie>()

    list += Cookie.Builder()
        .domain(domain)
        .name("user_lang")
        .value(DEFAULT_USER_LANG)
        .path("/")
        .build()

    if (cookie.isBlank()) return list

    cookie.split(';').forEach { segment ->
        if (!segment.contains('=')) return@forEach
        val name = segment.substringBefore('=').trim().filterPrintableAscii()
        val value = segment.substringAfter('=').trim().filterPrintableAscii()
        if (name.isEmpty()) return@forEach
        runCatching {
            list += Cookie.Builder()
                .domain(domain)
                .name(name)
                .value(value)
                .path("/")
                .build()
        }
    }
    return list
}