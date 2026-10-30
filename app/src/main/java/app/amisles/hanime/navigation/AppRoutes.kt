package app.amisles.hanime.navigation

import android.net.Uri
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.feature.search.sortOptions

internal fun String.baseRoute(): String = substringBefore('?')

internal fun moreUrlSortToken(moreUrl: String): String? {
    val at = moreUrl.indexOf("/sort/")
    if (at < 0) return null
    return moreUrl.substring(at + "/sort/".length)
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .takeIf { it.isNotEmpty() }
        ?.let(::percentDecode)
}

internal fun sortApiValueOf(section: HomeSection): String? =
    moreUrlSortToken(section.moreUrl)
        ?: sortOptions.firstOrNull { it.label == section.title }?.apiValue

internal fun moreUrlGenreToken(moreUrl: String): String? {
    val rest = listOf("/genre/", "/genres/")
        .firstNotNullOfOrNull { marker ->
            val at = moreUrl.indexOf(marker)
            if (at < 0) null else moreUrl.substring(at + marker.length)
        }
        ?: return null
    return rest
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .takeIf { it.isNotEmpty() }
        ?.let(::percentDecode)
}

internal fun genreApiValueOf(section: HomeSection): String? {
    val token = moreUrlGenreToken(section.moreUrl)
    if (token != null) {
        return categories.firstOrNull { it.label == token || it.apiValue == token }?.apiValue
    }
    return categories.firstOrNull { it.label == section.title }?.apiValue
}

/** 构造搜索页路由；ts 让每次点按都成为一次可区分的新意图，不受 restoreState 复用影响。 */
internal fun searchRoute(keyword: String? = null, genre: String? = null, sort: String? = null): String {
    val params = buildList {
        if (!keyword.isNullOrBlank()) add("keyword=" + Uri.encode(keyword))
        if (!genre.isNullOrBlank()) add("genre=" + Uri.encode(genre))
        if (!sort.isNullOrBlank()) add("sort=" + Uri.encode(sort))
        add("ts=" + System.currentTimeMillis())
    }
    return "search?" + params.joinToString("&")
}

internal fun moreUrlPageRoute(moreUrl: String): String? {
    val path = moreUrl.substringAfter("://", moreUrl)
        .substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    val head = segments.firstOrNull() ?: return null
    val tail = segments.getOrNull(1)
    val encoded = Uri.encode(moreUrl)
    return when (head) {
        "tag", "tags" -> tail?.let { "search?keyword=${Uri.encode(percentDecode(it))}" }
        "author", "authors" -> "author?authorPageUrl=$encoded"
        "playlist", "playlists" -> "playlistDetail?url=$encoded"
        "sort", "search", "genre", "genres" -> "videoListPage?url=$encoded"
        else -> null
    }
}

internal fun comicDirectoryRoute(kind: ComicDirectoryKind, name: String, url: String): String =
    "comicDirectory?kind=" + kind.name + "&name=" + Uri.encode(name) + "&url=" + Uri.encode(url)

internal fun percentDecode(raw: String): String {
    if ('%' !in raw) return raw
    val bytes = java.io.ByteArrayOutputStream()
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%' && i + 2 < raw.length) {
            val hex = raw.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes.write(hex)
                i += 3
                continue
            }
        }
        val encoded = c.toString().toByteArray(Charsets.UTF_8)
        bytes.write(encoded, 0, encoded.size)
        i++
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}
