package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.AuthorPageData
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.UserVideoListResult
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 作者页面解析器
 */
@Singleton
class AuthorPageParser @Inject constructor(
    private val videoListParser: VideoListParser,
    private val playlistParser: PlaylistParser,
    private val searchPageParser: SearchPageParser
) {

    fun parse(html: String, baseUrl: String): AuthorPageData? {
        AppLogger.log("AuthorPageParser", "parse called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            val authorName = doc.selectFirst(".profile-display-name")?.text()?.trim() ?: ""
            val authorAvatarUrl = doc.selectFirst(".profile-avatar-wrapper img")?.attr("abs:src") ?: ""

            val authorIdText = doc.selectFirst(".profile-sub-stats-id")?.text()?.trim() ?: ""
            val authorId = authorIdText.replace("@", "").trim()

            val subStatsElement = doc.selectFirst(".profile-sub-stats-new-line")
            val subStats = subStatsElement?.text()?.trim() ?: ""
            AppLogger.log("AuthorPageParser", "subStats element found: ${subStatsElement != null}, text: '$subStats'")
            AppLogger.log("AuthorPageParser", "subStats length: ${subStats.length}, bytes: ${subStats.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02x".format(it) }}")
            val (subscriberCount, videoCount) = parseSubscriberStats(subStats)

            AppLogger.log("AuthorPageParser", "Looking for horizontal-row-title elements...")
            val sectionLinks = doc.select("a.horizontal-row-title")
            AppLogger.log("AuthorPageParser", "Found ${sectionLinks.size} horizontal-row-title links")
            for ((index, link) in sectionLinks.withIndex()) {
                val h3 = link.selectFirst("h3")
                val h3OwnText = h3?.ownText()?.trim() ?: ""
                val h3FullText = h3?.text()?.trim() ?: ""
                AppLogger.log("AuthorPageParser", "  [$index] h3 ownText='$h3OwnText', fullText='$h3FullText'")
            }

            val videos = videoListParser.parseSectionVideos(doc, baseUrl, "影片")
            val playlists = playlistParser.parseSectionPlaylists(doc, baseUrl, "播放清单")

            val uploadedLink = doc.select("a.horizontal-row-title").find {
                val h3Text = it.selectFirst("h3")?.ownText()?.trim() ?: ""
                h3Text.startsWith("影片")
            }?.attr("abs:href") ?: ""
            val playlistsLink = doc.select("a.horizontal-row-title").find {
                val h3Text = it.selectFirst("h3")?.ownText()?.trim() ?: ""
                h3Text.startsWith("播放清单") || h3Text.startsWith("播放清單")
            }?.attr("abs:href") ?: ""

            AppLogger.log("AuthorPageParser", "Parsed author page: $authorName ($authorId), sub=$subscriberCount, vid=$videoCount, ${videos.size} videos, ${playlists.size} playlists")
            AppLogger.log("AuthorPageParser", "Links: uploaded='$uploadedLink', playlists='$playlistsLink'")

            return AuthorPageData(
                authorId = authorId,
                authorName = authorName,
                authorAvatarUrl = authorAvatarUrl,
                subscriberCount = subscriberCount,
                videoCount = videoCount,
                videos = videos,
                playlists = playlists,
                uploadedPageUrl = uploadedLink,
                playlistsPageUrl = playlistsLink
            )
        } catch (e: Exception) {
            AppLogger.logError("AuthorPageParser", "Error parsing author page: ${e.message}", e)
            return null
        }
    }

    fun parseVideoListPage(html: String, baseUrl: String): List<HanimeVideo> {
        AppLogger.log("AuthorPageParser", "parseVideoListPage called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            return videoListParser.parseAuthorVideos(doc, baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("AuthorPageParser", "Error parsing video list page: ${e.message}", e)
            return emptyList()
        }
    }

    fun parseUserVideoList(html: String, baseUrl: String): UserVideoListResult {
        AppLogger.log("AuthorPageParser", "parseUserVideoList called")
        val doc: Document = Jsoup.parse(html, baseUrl)

        val authorName = doc.selectFirst(".profile-display-name")?.text()?.trim() ?: ""
        val authorIdMatch = Regex("user/(\\d+)").find(baseUrl)
        val authorId = authorIdMatch?.groupValues?.get(1) ?: ""

        val videos = mutableListOf<HanimeVideo>()

        val userTabItems = doc.select(".user-tab-item-wrapper")
        AppLogger.log("AuthorPageParser", "Found ${userTabItems.size} user-tab-item-wrapper elements")

        for (item in userTabItems) {
            val video = videoListParser.parseUserTabVideoItem(item, baseUrl)
            if (video != null) {
                videos.add(video)
            }
        }

        if (videos.isEmpty()) {
            val videoContainers = doc.select(".video-item-container")
            AppLogger.log("AuthorPageParser", "Fallback: Found ${videoContainers.size} video-item-container elements")
            for (container in videoContainers) {
                val video = videoListParser.parseVideoItem(container, baseUrl)
                if (video != null) {
                    videos.add(video)
                }
            }
        }

        val (currentPage, totalPages, hasNextPage) = searchPageParser.parsePagination(doc)

        AppLogger.log("AuthorPageParser", "Parsed user video list: ${videos.size} videos, page $currentPage/$totalPages, hasNext=$hasNextPage")

        return UserVideoListResult(
            videos = videos,
            currentPage = currentPage,
            totalPages = totalPages,
            hasNextPage = hasNextPage,
            authorName = authorName,
            authorId = authorId
        )
    }

    private fun parseSubscriberStats(stats: String): Pair<String, String> {
        AppLogger.log("AuthorPageParser", "parseSubscriberStats input: '$stats'")

        val subscriberPatterns = listOf(
            Regex("(\\d[\\d,]*)\\s+位訂閱者"),
            Regex("(\\d[\\d,]*)\\s+位订阅者"),
            Regex("(\\d[\\d,]*)\\s*位訂閱者"),
            Regex("(\\d[\\d,]*)\\s*位订阅者"),
            Regex("(\\d[\\d,]*)位訂閱者"),
            Regex("(\\d[\\d,]*)位订阅者")
        )
        var subscriberCount = ""
        for (pattern in subscriberPatterns) {
            val match = pattern.find(stats)
            if (match != null) {
                subscriberCount = match.groupValues[1]
                AppLogger.log("AuthorPageParser", "Matched subscriber with pattern: $pattern")
                break
            }
        }

        val videoPatterns = listOf(
            Regex("(\\d[\\d,]*)\\s+部影片"),
            Regex("(\\d[\\d,]*)\\s+个视频"),
            Regex("(\\d[\\d,]*)\\s*部影片"),
            Regex("(\\d[\\d,]*)\\s*个视频"),
            Regex("(\\d[\\d,]*)部影片"),
            Regex("(\\d[\\d,]*)个视频")
        )
        var videoCount = ""
        for (pattern in videoPatterns) {
            val match = pattern.find(stats)
            if (match != null) {
                videoCount = match.groupValues[1]
                AppLogger.log("AuthorPageParser", "Matched video count with pattern: $pattern")
                break
            }
        }

        AppLogger.log("AuthorPageParser", "parseSubscriberStats result: sub='$subscriberCount', video='$videoCount'")
        return Pair(subscriberCount, videoCount)
    }
}
