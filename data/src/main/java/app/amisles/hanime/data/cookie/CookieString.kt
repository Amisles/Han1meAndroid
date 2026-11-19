package app.amisles.hanime.data.cookie

import app.amisles.hanime.domain.model.SessionCookie
import okhttp3.Cookie

fun String.filterPrintableAscii(): String =
    filter { it.code in 0x20..0x7E }

private const val DEFAULT_USER_LANG = "zhs"

fun SessionCookie.toLoginCookieList(domain: String): List<Cookie> {
    val list = mutableListOf<Cookie>()

    list += Cookie.Builder()
        .domain(domain)
        .name("user_lang")
        .value(DEFAULT_USER_LANG)
        .path("/")
        .secure()
        .build()

    if (raw.isBlank()) return list

    raw.split(';').forEach { segment ->
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
                .secure()
                .build()
        }
    }
    return list
}