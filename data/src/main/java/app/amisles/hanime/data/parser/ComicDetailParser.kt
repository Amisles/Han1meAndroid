package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicDetail
import app.amisles.hanime.domain.model.ComicPageThumb
import app.amisles.hanime.domain.model.ComicSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 漫画详情（/comic/{id}）解析。
 *
 * 元数据在若干 h5 里，形如「標籤：<a><div>巨乳</div></a>」。标签文字在 h5 自身的文本节点，
 * 取值在子 div 里 —— 用 ownText() 取标签、select("div") 取值，一个循环覆盖全部字段。
 *
 * 页缩略图容器的 class 与相关推荐不同：前者 comics-thumbnail-wrapper，后者 comics-related-wrapper。
 * 两者的卡片类名相同，必须靠容器区分，否则会把几百页缩略图当成推荐位。
 */
@Singleton
class ComicDetailParser @Inject constructor() {

    fun parse(html: String, baseUrl: String, comicId: String): ComicDetail {
        val doc = Jsoup.parse(html, baseUrl)
        val meta = readMetadata(doc)
        val thumbs = readPageThumbnails(doc)
        return ComicDetail(
            id = comicId,
            title = readTitle(doc.selectFirst("h3.title")),
            originalTitle = readTitle(doc.selectFirst("h4.title")),
            coverUrl = readCover(doc),
            tags = meta[LABEL_TAGS].orEmpty(),
            artists = meta[LABEL_ARTIST].orEmpty(),
            languages = meta[LABEL_LANGUAGE].orEmpty(),
            categories = meta[LABEL_CATEGORY].orEmpty(),
            pageCount = meta[LABEL_PAGES]?.firstOrNull()?.toIntOrNull() ?: thumbs.size,
            uploadedAt = meta[LABEL_UPLOAD]?.firstOrNull().orEmpty(),
            pageThumbnails = thumbs,
            relatedSections = readRelatedSections(doc)
        )
    }

    /** 收集「标签 → 该行所有取值」。重复出现的同名行只取第一次。 */
    private fun readMetadata(doc: Document): Map<String, List<String>> {
        val result = LinkedHashMap<String, List<String>>()
        doc.select("h5").forEach { row ->
            val label = row.ownText()
                .replace('\u00a0', ' ')
                .trim()
                .trimEnd('：', ':')
                .trim()
            if (label.isEmpty()) return@forEach
            val values = row.select("div").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (values.isNotEmpty()) result.putIfAbsent(label, values)
        }
        return result
    }

    /** 站点把标题拆成 before / pretty / after 三段，缺段时整体取 text()。 */
    private fun readTitle(element: Element?): String {
        if (element == null) return ""
        val pretty = element.selectFirst("span.pretty")?.text()?.trim().orEmpty()
        if (pretty.isEmpty()) return element.text().trim()
        val before = element.selectFirst("span.before")?.text()?.trim().orEmpty()
        val after = element.selectFirst("span.after")?.text()?.trim().orEmpty()
        return listOf(before, pretty, after).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun readCover(doc: Document): String {
        val img = doc.selectFirst("div.col-md-4 img") ?: return ""
        return listOf("abs:data-src", "abs:src", "data-src", "src")
            .firstNotNullOfOrNull { key -> img.attr(key).trim().takeIf { it.isNotEmpty() } }
            .orEmpty()
    }

    private fun readPageThumbnails(doc: Document): List<ComicPageThumb> {
        val wrapper = doc.selectFirst("div.comics-thumbnail-wrapper") ?: return emptyList()
        return wrapper.select("a[href]").mapNotNull { anchor ->
            val number = anchor.attr("abs:href")
                .substringAfterLast('/')
                .substringBefore('?')
                .toIntOrNull() ?: return@mapNotNull null
            val img = anchor.selectFirst("img") ?: return@mapNotNull null
            val url = listOf("abs:data-srcset", "abs:data-src", "abs:src", "data-srcset", "src")
                .firstNotNullOfOrNull { key -> img.attr(key).trim().takeIf { it.isNotEmpty() } }
                .orEmpty()
                .substringBefore(' ')
                .substringBefore(',')
            if (url.isEmpty()) return@mapNotNull null
            ComicPageThumb(number, url)
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    private fun readRelatedSections(doc: Document): List<ComicSection> =
        doc.select("div.comics-related-wrapper").mapNotNull { wrapper ->
            val title = wrapper.selectFirst("h3")?.text()?.trim().orEmpty()
            val items = wrapper.select("div.comic-rows-videos-div")
                .mapNotNull { ComicItemParser.parse(it) }
                .distinctBy { it.id }
            if (items.isEmpty()) null else ComicSection(title, items)
        }

    private companion object {
        const val LABEL_TAGS = "標籤"
        const val LABEL_ARTIST = "作者"
        const val LABEL_LANGUAGE = "語言"
        const val LABEL_CATEGORY = "分類"
        const val LABEL_PAGES = "頁數"
        const val LABEL_UPLOAD = "上傳"
    }
}
