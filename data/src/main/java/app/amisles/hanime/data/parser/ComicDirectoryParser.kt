package app.amisles.hanime.data.parser

import app.amisles.hanime.data.remote.pathSegment
import app.amisles.hanime.domain.model.ComicDirectoryEntry
import app.amisles.hanime.domain.model.ComicDirectoryKind
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签 / 作者目录（/comics/tags、/comics/artists）解析。
 *
 * 两种目录结构一致：content-padding 内是一串 <a href="/tags/{name}"><div><span>名称</span><span>数量</span></div></a>。
 * 数量可能缺失；导航栏里的 /comics/tags 不含尾部斜杠，不会被误当成条目。
 */
@Singleton
class ComicDirectoryParser @Inject constructor() {

    fun parse(html: String, baseUrl: String, kind: ComicDirectoryKind): List<ComicDirectoryEntry> {
        val doc = Jsoup.parse(html, baseUrl)
        val marker = "/" + kind.pathSegment + "/"
        return doc.select("a[href]").mapNotNull { anchor ->
            val href = anchor.attr("abs:href").trim().ifEmpty { anchor.attr("href").trim() }
            if (!href.contains(marker)) return@mapNotNull null
            val spans = anchor.select("span")
            val name = spans.firstOrNull()?.text()?.trim().orEmpty()
                .ifEmpty { anchor.text().trim() }
            if (name.isEmpty()) return@mapNotNull null
            val rawCount = spans.getOrNull(1)?.text()?.trim().orEmpty()
            ComicDirectoryEntry(name, rawCount.takeIf { COUNT.matches(it) }.orEmpty(), href)
        }.distinctBy { it.url }
    }

    private companion object {
        val COUNT = Regex("^\\d[\\d.,]*\\s*[kKmM]?$")
    }
}
