package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicItem
import app.amisles.hanime.domain.model.ComicPage
import app.amisles.hanime.domain.model.ComicSection
import org.jsoup.Jsoup
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
        val page = ComicPaginationParser.parse(doc)
        return ComicPage(sections, page.current, page.total, page.hasNext)
    }

    private fun parseItem(card: Element): ComicItem? = ComicItemParser.parse(card)
}
