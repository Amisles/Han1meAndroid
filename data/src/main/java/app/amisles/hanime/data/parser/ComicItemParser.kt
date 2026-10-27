package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicItem
import org.jsoup.nodes.Element

/**
 * 漫画卡片解析。漫画首页、详情页的「相关集数」「更多相关漫画」结构一致，共用这一份。
 *
 * 封面必须取 data-srcset：src 是站点统一的占位图，所有条目都一样。
 */
internal object ComicItemParser {

    fun parse(card: Element): ComicItem? {
        val anchor = card.selectFirst("a[href*=/comic/]") ?: return null
        val href = anchor.attr("abs:href").trim().ifEmpty { anchor.attr("href").trim() }
        if (href.isEmpty()) return null
        val id = href.substringAfterLast("/comic/").substringBefore('?').substringBefore('/').trim()
        if (id.isEmpty()) return null
        val title = card.selectFirst(".comic-rows-videos-title")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null
        val img = card.selectFirst("img")
        val cover = listOf("abs:data-srcset", "abs:data-src", "abs:src", "data-srcset", "src")
            .firstNotNullOfOrNull { key -> img?.attr(key)?.trim()?.takeIf { it.isNotEmpty() } }
            .orEmpty()
            .substringBefore(' ')
            .substringBefore(',')
        return ComicItem(id, title, cover, href)
    }
}
