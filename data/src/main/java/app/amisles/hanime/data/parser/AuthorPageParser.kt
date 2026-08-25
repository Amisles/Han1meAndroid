package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.AuthorPageData
import app.amisles.hanime.domain.model.AuthorPageDataEvent
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.UserVideoListResult
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthorPageParser @Inject constructor(
    private val videoListParser: VideoListParser,
    private val playlistParser: PlaylistParser,
    private val searchPageParser: SearchPageParser
) {

    fun parse(html: String, baseUrl: String): AuthorPageData? {
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            val profile = parseProfile(doc, baseUrl)
            val videos = videoListParser.parseSectionVideos(doc, baseUrl, "影片")
            val playlists = playlistParser.parseSectionPlaylists(doc, baseUrl, "播放清单")
            return profile.copy(videos = videos, playlists = playlists)
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("AuthorPageParser", "Error parsing author page: ${e.message}", e)
            return null
        } catch (e: NullPointerException) {
            AppLogger.logError("AuthorPageParser", "Error parsing author page: ${e.message}", e)
            return null
        } catch (e: IllegalArgumentException) {
            AppLogger.logError("AuthorPageParser", "Error parsing author page: ${e.message}", e)
            return null
        }
    }

    /**
     * 抽取作者资料头部（不含影片/播放清单列表），供同步 [parse] 与流式 [parseStreaming] 共用。
     * 列表字段置空，由调用方在后续阶段补齐。
     */
    private fun parseProfile(doc: Document, baseUrl: String): AuthorPageData {
        val authorName = doc.selectFirst(".profile-display-name")?.text()?.trim() ?: ""
        val authorAvatarUrl = doc.selectFirst(".profile-avatar-wrapper img")?.attr("abs:src") ?: ""

        val authorIdText = doc.selectFirst(".profile-sub-stats-id")?.text()?.trim() ?: ""
        val authorId = authorIdText.replace("@", "").trim()

        val subStatsElement = doc.selectFirst(".profile-sub-stats-new-line")
        val subStats = subStatsElement?.text()?.trim() ?: ""
        val (subscriberCount, videoCount) = parseSubscriberStats(subStats)

        val uploadedLink = doc.select("a.horizontal-row-title").find {
            val h3Text = it.selectFirst("h3")?.ownText()?.trim() ?: ""
            h3Text.startsWith("影片")
        }?.attr("abs:href") ?: ""
        val playlistsLink = doc.select("a.horizontal-row-title").find {
            val h3Text = it.selectFirst("h3")?.ownText()?.trim() ?: ""
            h3Text.startsWith("播放清单") || h3Text.startsWith("播放清單")
        }?.attr("abs:href") ?: ""

        return AuthorPageData(
            authorId = authorId,
            authorName = authorName,
            authorAvatarUrl = authorAvatarUrl,
            subscriberCount = subscriberCount,
            videoCount = videoCount,
            uploadedPageUrl = uploadedLink,
            playlistsPageUrl = playlistsLink
        )
    }

    /**
     * 流式解析作者主页
     */
    fun parseStreaming(html: String, baseUrl: String): Flow<AuthorPageDataEvent> = flow {
        val doc: Document = Jsoup.parse(html, baseUrl)
        val profile = parseProfile(doc, baseUrl)
        emit(AuthorPageDataEvent.Profile(profile))

        try {
            val videos = videoListParser.parseSectionVideos(doc, baseUrl, "影片")
            emit(AuthorPageDataEvent.Videos(videos))
        } catch (e: Exception) {
            AppLogger.logError("AuthorPageParser", "Error parsing author videos: ${e.message}", e)
        }

        try {
            val playlists = playlistParser.parseSectionPlaylists(doc, baseUrl, "播放清单")
            emit(AuthorPageDataEvent.Playlists(playlists))
        } catch (e: Exception) {
            AppLogger.logError("AuthorPageParser", "Error parsing author playlists: ${e.message}", e)
        }
    }

    fun parseVideoListPage(html: String, baseUrl: String): List<HanimeVideo> {
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)
            return videoListParser.parseAuthorVideos(doc, baseUrl)
        } catch (e: IllegalArgumentException) {
            AppLogger.logError("AuthorPageParser", "Error parsing video list page: ${e.message}", e)
            return emptyList()
        } catch (e: NullPointerException) {
            AppLogger.logError("AuthorPageParser", "Error parsing video list page: ${e.message}", e)
            return emptyList()
        }
    }

    fun parseUserVideoList(html: String, baseUrl: String): UserVideoListResult {
        val doc: Document = Jsoup.parse(html, baseUrl)

        val authorName = doc.selectFirst(".profile-display-name")?.text()?.trim() ?: ""
        val authorIdMatch = Regex("user/(\\d+)").find(baseUrl)
        val authorId = authorIdMatch?.groupValues?.get(1) ?: ""

        val videos = mutableListOf<HanimeVideo>()

        val userTabItems = doc.select(".user-tab-item-wrapper")

        for (item in userTabItems) {
            val video = videoListParser.parseUserTabVideoItem(item, baseUrl)
            if (video != null) {
                videos.add(video)
            }
        }

        if (videos.isEmpty()) {
            val videoContainers = doc.select(".video-item-container")
            for (container in videoContainers) {
                val video = videoListParser.parseVideoItem(container, baseUrl)
                if (video != null) {
                    videos.add(video)
                }
            }
        }

        val (currentPage, totalPages, hasNextPage) = searchPageParser.parsePagination(doc)

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
                break
            }
        }

        return Pair(subscriberCount, videoCount)
    }
}
