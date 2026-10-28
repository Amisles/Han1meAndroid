package app.amisles.hanime.data.remote

import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.ComicSort

internal val ComicDirectoryKind.pathSegment: String
    get() = when (this) {
        ComicDirectoryKind.TAG -> "tags"
        ComicDirectoryKind.ARTIST -> "artists"
    }

internal val ComicSort.pathSegment: String
    get() = when (this) {
        ComicSort.LATEST -> ""
        ComicSort.POPULAR_TODAY -> "popular-today"
        ComicSort.POPULAR_WEEK -> "popular-week"
        ComicSort.POPULAR -> "popular"
    }

internal object ComicUrls {

    fun directoryPath(kind: ComicDirectoryKind): String = "/comics/" + kind.pathSegment

    /** 目录条目地址 + 排序后缀；[page] 大于 1 时再挂 page 查询参数，与站点分页链接一致。 */
    fun directoryResult(baseUrl: String, sort: ComicSort, page: Int): String {
        val base = baseUrl.trim().trimEnd('/')
        val suffix = sort.pathSegment
        val path = if (suffix.isEmpty()) base else base + "/" + suffix
        return if (page > 1) path + "?page=" + page else path
    }
}
