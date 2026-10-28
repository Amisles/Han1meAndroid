package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicDirectoryResult
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 标签 / 作者作品列表（/tags/{name}[/{sort}]、/artists/{name}[/{sort}]）解析。
 *
 * 四种排序只在路径后缀上不同，正文结构与漫画首页的卡片一致，故直接复用 [ComicItemParser]。
 */
@Singleton
class ComicDirectoryResultParser @Inject constructor() {

    fun parse(html: String, baseUrl: String): ComicDirectoryResult {
        val doc = Jsoup.parse(html, baseUrl)
        val items = doc.select("div.comic-rows-videos-div")
            .mapNotNull { ComicItemParser.parse(it) }
            .distinctBy { it.id }
        val page = ComicPaginationParser.parse(doc)
        return ComicDirectoryResult(items, page.current, page.total, page.hasNext)
    }
}
