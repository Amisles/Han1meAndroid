package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicItem
import app.amisles.hanime.domain.model.ComicPage
import app.amisles.hanime.domain.model.ComicSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 漫画首页（/comics）解析。
 *
 * 分区容器是 div.comic-rows-wrapper，条目是 div.comic-rows-videos-div，
 * 链接指向 /comic/{id}。封面必须取 data-srcset：src 是站点占位图，所有条目都一样。
 */
@Singleton
class ComicPageParser @Inject constructor() {

    fun parse(html: String, baseUrl: String): ComicPage {
        val doc = Jsoup.parse(html, baseUrl)
        val sections = doc.select("div.comic-rows-wrapper").mapNotNull { wrapper ->
            val title = wrapper.selectFirst("h3")?.let { h3 ->
                val copy = h3.clone()
                copy.select("span.material-icons").remove()
                copy.text().trim()
            }.orEmpty()
            val items = wrapper.select("div.comic-rows-videos-div")
                .mapNotNull { parseItem(it) }
                .distinctBy { it.detailUrl }
            if (items.isEmpty()) null else ComicSection(title, items)
        }
        val (current, total, hasNext) = parsePagination(doc)
        return ComicPage(sections, current, total, hasNext)
    }

    private fun parseItem(card: Element): ComicItem? {
        val href = card.selectFirst("a[href*=/comic/]")?.attr("abs:href").orEmpty()
        if (href.isEmpty()) return null
        val id = href.substringAfterLast("/comic/").substringBefore('?').substringBefore('/').trim()
        if (id.isEmpty()) return null
        val title = card.selectFirst(".comic-rows-videos-title")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null
        val img = card.selectFirst("img")
        val cover = img?.attr("data-srcset")?.trim().orEmpty()
            .ifEmpty { img?.attr("abs:data-src")?.trim().orEmpty() }
            .ifEmpty { img?.attr("abs:src")?.trim().orEmpty() }
        return ComicItem(id, title, cover, href)
    }

    private fun parsePagination(doc: Document): Triple<Int, Int, Boolean> {
        val pager = doc.select("div.search-pagination").firstOrNull()
            ?.selectFirst("ul.pagination")
            ?: return Triple(1, 1, false)
        val current = pager.selectFirst("li.page-item.active span.page-link")
            ?.text()?.trim()?.toIntOrNull() ?: 1
        var total = current
        pager.select("li.page-item a.page-link").forEach { link ->
            link.text().trim().toIntOrNull()?.let { if (it > total) total = it }
        }
        val hasNext = pager.selectFirst("a.page-link[rel=next]") != null || current < total
        return Triple(current, total, hasNext)
    }
}
