package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicChapter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 漫画正文（/comic/{id}/{page}）解析。
 *
 * 站点不给整话列表，只给「图库前缀 + 当前页扩展名」和一段逐页扩展名数组。
 * 因此请求一次即可推出全部页码地址：prefix + 页码 + "." + 扩展名，翻页不再发请求。
 */
@Singleton
class ComicReaderParser @Inject constructor() {

    fun parse(html: String, baseUrl: String, comicId: String): ComicChapter {
        val doc = Jsoup.parse(html, baseUrl)
        val img = doc.selectFirst("#current-page-image")
        val currentSrc = listOf("abs:src", "src")
            .firstNotNullOfOrNull { key -> img?.attr(key)?.trim()?.takeIf { it.isNotEmpty() } }
            .orEmpty()

        val prefix = img?.attr("data-prefix")?.trim().orEmpty().ifEmpty {
            currentSrc.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        }
        val fallbackExtension = img?.attr("data-extension")?.trim().orEmpty()
            .ifEmpty { currentSrc.substringAfterLast('.', "webp") }

        val extensions = readExtensions(doc)
        val declaredPages = doc.selectFirst(".comic-show-content-nav")
            ?.attr("data-pages")?.trim()?.toIntOrNull() ?: 0
        val pageCount = declaredPages.takeIf { it > 0 } ?: extensions.size

        if (prefix.isEmpty() || pageCount <= 0) return ComicChapter(comicId, 0, emptyList())

        val pages = (1..pageCount).map { number ->
            prefix + number + "." + resolveExtension(extensions.getOrNull(number - 1), fallbackExtension)
        }
        return ComicChapter(comicId, pageCount, pages)
    }

    /** 站点用首字母缩写标记每页格式：w = webp，j = jpg，p = png。 */
    private fun resolveExtension(code: String?, fallback: String): String =
        when (code?.lowercase()) {
            "w" -> "webp"
            "j" -> "jpg"
            "p" -> "png"
            else -> fallback
        }

    /**
     * 扩展名数组写在 #comic-content-wrapper 的内联脚本里，且被 HTML 转义成 &quot;。
     * 先解码再按「单个字母的字符串字面量」提取，避免手工拆 JSON。
     */
    private fun readExtensions(doc: Document): List<String> {
        val script = doc.select("#comic-content-wrapper script").joinToString("\n") { it.data() }
        if (script.isEmpty()) return emptyList()
        val block = script.substringAfter("extensions.innerHTML", "").substringBefore("JSON.parse")
        if (block.isEmpty()) return emptyList()
        return Regex("\"([A-Za-z])\"").findAll(block.replace("&quot;", "\""))
            .map { it.groupValues[1] }
            .toList()
    }
}
