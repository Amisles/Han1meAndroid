package app.amisles.hanime.data.parser

import org.jsoup.nodes.Document

/** 漫画各列表页共用的分页条（div.search-pagination ul.pagination）。站点同时输出桌面与移动两份，取第一份即可。 */
internal object ComicPaginationParser {

    data class PageInfo(val current: Int, val total: Int, val hasNext: Boolean)

    fun parse(doc: Document): PageInfo {
        val pager = doc.select("div.search-pagination").firstOrNull()
            ?.selectFirst("ul.pagination")
            ?: return PageInfo(1, 1, false)
        val current = pager.selectFirst("li.page-item.active span.page-link")
            ?.text()?.trim()?.toIntOrNull() ?: 1
        var total = current
        pager.select("li.page-item a.page-link").forEach { link ->
            link.text().trim().toIntOrNull()?.let { if (it > total) total = it }
        }
        val hasNext = pager.selectFirst("a.page-link[rel=next]") != null || current < total
        return PageInfo(current, total, hasNext)
    }
}
