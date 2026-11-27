package app.amisles.hanime.data.remote

import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.repository.SettingsRepository
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiteApi @Inject constructor(
    private val transport: HttpTransport
) {
    private companion object {
        val HOST_REGEX = Regex("https?://([^/]+)")
    }

    suspend fun fetchHomePage(): FetchResult {
        val baseUrl = transport.baseUrl
        val html = transport.get("$baseUrl/")
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchSearchPage(
        query: String,
        genre: String? = null,
        sort: String? = null,
        page: Int = 1,
        tags: List<String> = emptyList(),
        broad: Boolean = false,
        date: String = "",
        duration: String = ""
    ): FetchResult {
        val baseUrl = transport.baseUrl
        val queryParts = mutableListOf<String>()
        if (query.isNotEmpty()) {
            queryParts += "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        }
        if (genre != null && genre.isNotEmpty()) {
            queryParts += "genre=" + URLEncoder.encode(genre, StandardCharsets.UTF_8.name())
        }
        if (sort != null && sort.isNotEmpty()) {
            queryParts += "sort=" + URLEncoder.encode(sort, StandardCharsets.UTF_8.name())
        }
        for (tag in tags) {
            // tags%5B%5D= 即 tags[]= 的 URL 编码形式，与官网搜索链接保持一致
            queryParts += "tags%5B%5D=" + URLEncoder.encode(tag, StandardCharsets.UTF_8.name())
        }
        if (broad) {
            queryParts += "broad=on"
        }
        if (date.isNotEmpty()) {
            // 官网「发布日期」筛选，快捷值如 date=過去 24 小時；空串 = 全部
            queryParts += "date=" + URLEncoder.encode(date, StandardCharsets.UTF_8.name())
        }
        if (duration.isNotEmpty()) {
            // 官网「时长」筛选，取值如 duration=30 分鐘 +；空串 = 全部
            queryParts += "duration=" + URLEncoder.encode(duration, StandardCharsets.UTF_8.name())
        }
        if (page > 1) {
            queryParts += "page=$page"
        }
        val url = "$baseUrl/search?" + queryParts.joinToString("&")
        val html = transport.get(url)
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchWatchPage(videoUrl: String, userLang: String = ""): FetchResult {
        val url = if (videoUrl.startsWith("http")) videoUrl else transport.baseUrl + videoUrl
        AppLogger.log("SiteApi", "Fetching watch page: ${url.redactUrlForLog()} lang=$userLang")
        val html = transport.get(url, userLang)
        val baseUrl = "https://" +
            (HOST_REGEX.find(url)?.groupValues?.get(1)
                ?: SettingsRepository.DEFAULT_BASE_URL.removePrefix("https://"))
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchDownloadPage(videoId: String, userLang: String = ""): FetchResult {
        val baseUrl = transport.baseUrl
        val html = transport.get("$baseUrl/download?v=$videoId", userLang)
        return FetchResult(html, baseUrl)
    }

    suspend fun fetchAuthorPage(authorPageUrl: String): FetchResult {
        val html = transport.get(authorPageUrl)
        return FetchResult(html, transport.baseUrl)
    }

    suspend fun fetchSubscriptionsPage(query: String = ""): FetchResult {
        val baseUrl = transport.baseUrl
        val url = if (query.isBlank()) {
            "$baseUrl/subscriptions"
        } else {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            "$baseUrl/subscriptions?query=$encoded"
        }
        return FetchResult(transport.get(url), url)
    }

    suspend fun fetchVideoListPage(url: String): FetchResult =
        FetchResult(transport.get(url), transport.baseUrl)

    suspend fun fetchWatchLaterPage(url: String): FetchResult =
        FetchResult(transport.get(url), transport.baseUrl)

    suspend fun fetchPlaylistListPage(url: String): FetchResult =
        FetchResult(transport.get(url), transport.baseUrl)

    suspend fun fetchPlaylistDetailPage(url: String): FetchResult =
        FetchResult(transport.get(url), transport.baseUrl)

    suspend fun fetchUserVideoListPage(authorId: String, page: Int): FetchResult {
        val baseUrl = transport.baseUrl
        val url = if (page > 1) {
            "$baseUrl/user/$authorId/uploaded?page=$page"
        } else {
            "$baseUrl/user/$authorId/uploaded"
        }
        return FetchResult(transport.get(url), url)
    }
}
