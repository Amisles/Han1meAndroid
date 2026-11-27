package app.amisles.hanime.data.cookie

import app.amisles.hanime.domain.model.SessionCookie
import app.amisles.hanime.domain.model.SubtitleLang
import okhttp3.Cookie

fun String.filterPrintableAscii(): String =
    filter { it.code in 0x20..0x7E }

const val USER_LANG_COOKIE = "user_lang"

private const val DEFAULT_USER_LANG = SubtitleLang.SIMPLIFIED

/** 站点按这个 Cookie 决定整页用简体还是繁体渲染，简繁字幕是两份不同的视频文件。 */
fun userLangCookie(host: String, lang: String): Cookie = Cookie.Builder()
    .domain(host)
    .name(USER_LANG_COOKIE)
    .value(lang)
    .path("/")
    .secure()
    .build()

fun SessionCookie.toLoginCookieList(domain: String): List<Cookie> {
    val list = mutableListOf<Cookie>()

    list += userLangCookie(domain, DEFAULT_USER_LANG)

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