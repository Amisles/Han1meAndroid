package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索页解析器
 */
@Singleton
class SearchPageParser @Inject constructor(private val videoListParser: VideoListParser) {

    private fun isVideoLink(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull() ?: return false
        return path.startsWith("/watch")
    }

    fun parse(html: String, baseUrl: String): List<HanimeVideo> {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val videos = videoListParser.parseVideoList(doc, baseUrl)
        return videos.filter { isVideoLink(it.videoUrl) }
    }

    fun parseWithPagination(html: String, baseUrl: String): SearchResult {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val videos = videoListParser.parseVideoList(doc, baseUrl)
        val filtered = videos.filter { isVideoLink(it.videoUrl) }

        val (currentPage, totalPages, hasNextPage) = parsePagination(doc)

        return SearchResult(
            videos = filtered,
            currentPage = currentPage,
            totalPages = totalPages,
            hasNextPage = hasNextPage
        )
    }

    fun parsePagination(doc: Document): Triple<Int, Int, Boolean> {
        var currentPage = 1
        var totalPages = 1
        var hasNextPage = false

        try {
            val activeItem = doc.selectFirst("ul.pagination li.page-item.active span.page-link")
            activeItem?.text()?.trim()?.toIntOrNull()?.let { currentPage = it }

            val pageLinks = doc.select("ul.pagination li.page-item a.page-link")
            for (link in pageLinks) {
                val rel = link.attr("rel")
                if (rel == "next") {
                    hasNextPage = true
                }
                link.text().trim().toIntOrNull()?.let { num ->
                    if (num > totalPages) totalPages = num
                }
            }

            val skipInput = doc.selectFirst("#skip-page-input")
            if (skipInput != null) {
                skipInput.attr("oninput").let { oninput ->
                    val maxMatch = Regex("validateNumberInput\\(this,\\s*\\d+,\\s*(\\d+)").find(oninput)
                    maxMatch?.groupValues?.get(1)?.toIntOrNull()?.let { max ->
                        if (max > totalPages) totalPages = max
                    }
                }
            }

            if (currentPage < totalPages) hasNextPage = true
        } catch (e: Exception) {
            AppLogger.logError("SearchPageParser", "Error parsing pagination: ${e.message}", e)
        }

        return Triple(currentPage, totalPages, hasNextPage)
    }
}
