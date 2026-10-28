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

    companion object {
        /** 单话页数的合理性上限，防御 data-pages 被污染成超大值导致 OOM。 */
        private const val MAX_PLAUSIBLE_PAGES = 2000
    }

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
        // data-pages 是站点直供属性，无上限约束：异常大值（缓存污染/渲染异常）会在下方
        // (1..pageCount).map 处急切展开成巨型列表直接 OOM，且 OOM 是 Error，仓库层 catch 拦不住。
        // 上限取「扩展名数组长度」与保守常数的较大者 —— 数组来自页面内联脚本、每项对应真实页，
        // 站点真出了超常页数时以它为准，不被常数误截。
        val pageCount = (declaredPages.takeIf { it > 0 } ?: extensions.size)
            .coerceAtMost(maxOf(extensions.size, MAX_PLAUSIBLE_PAGES))

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
