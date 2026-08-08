package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistDetail
import app.amisles.hanime.domain.model.PlaylistInfo
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放列表解析器
 */
@Singleton
class PlaylistParser @Inject constructor() {

    // 解析播放列表信息（详情页内嵌的播放列表）
    fun parse(doc: Document, baseUrl: String): PlaylistInfo? {
        try {
            val playlistWrapper = doc.selectFirst(".video-playlist-wrapper")
            if (playlistWrapper == null) {
                return null
            }

            val topBlock = playlistWrapper.selectFirst("#playlist-top-block")

            val titleElement = topBlock?.selectFirst("h4 a")
            val playlistTitle = titleElement?.text()?.trim() ?: ""

            val authorLink = topBlock?.selectFirst("div[style*=\"font-size: 12px\"] a")
            val author = authorLink?.text()?.trim() ?: ""

            val videoCountText = topBlock?.selectFirst("div[style*=\"font-size: 12px\"] span:last-child")?.text() ?: "0"
            val videoCount = Regex("(\\d+)").find(videoCountText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val videos = mutableListOf<HanimeVideo>()
            val playlistItems = playlistWrapper.select(".playlist-hover-wrap")

            for (item in playlistItems) {
                try {
                    val videoCard = item.selectFirst(".playlist-video-card") ?: continue

                    val videoLink = videoCard.selectFirst("a[href*=\"watch?v=\"]")
                    val videoUrl = videoLink?.attr("abs:href") ?: ""
                    if (videoUrl.isEmpty()) continue

                    val thumbnail = videoCard.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                    val duration = videoCard.selectFirst(".duration")?.text()?.trim() ?: ""

                    val statsContainer = videoCard.selectFirst(".stats-container")
                    val likeRate = statsContainer?.selectFirst(".stat-item")?.text()?.trim()?.let { ParserUtils.cleanLikeRate(it) } ?: ""
                    val viewCount = statsContainer?.select("div.stat-item")?.getOrNull(1)?.text() ?: ""

                    val title = videoCard.selectFirst(".video-title a")?.text()?.trim() ?: ""

                    val videoAuthor = videoCard.selectFirst(".meta-author a")?.text()?.trim() ?: ""

                    val metaStats = videoCard.selectFirst(".meta-stats")?.text()?.split("•") ?: listOf()
                    val publishTime = metaStats.lastOrNull()?.trim() ?: ""

                    val videoId = ParserUtils.extractVideoId(videoUrl)

                    videos.add(
                        HanimeVideo(
                            id = videoId,
                            title = title,
                            thumbnailUrl = thumbnail,
                            duration = duration,
                            likeRate = likeRate,
                            viewCount = viewCount,
                            author = videoAuthor,
                            publishTime = publishTime,
                            videoUrl = videoUrl
                        )
                    )
                } catch (e: Exception) {
                    AppLogger.logError("PlaylistParser", "Error parsing playlist item: ${e.message}", e)
                }
            }

            if (playlistTitle.isEmpty() || videos.isEmpty()) {
                return null
            }

            return PlaylistInfo(
                title = playlistTitle,
                author = author,
                videoCount = videoCount,
                videos = videos
            )
        } catch (e: Exception) {
            AppLogger.logError("PlaylistParser", "Error parsing playlist: ${e.message}", e)
            return null
        }
    }

    // 解析播放列表列表页
    fun parseListPage(html: String, baseUrl: String): List<PlaylistSummary> {
        try {
            val doc = Jsoup.parse(html, baseUrl)
            return parsePlaylistSummaries(doc, baseUrl)
        } catch (e: Exception) {
            AppLogger.logError("PlaylistParser", "Error parsing playlist list page: ${e.message}", e)
            return emptyList()
        }
    }

    // 解析播放列表详情页
    fun parseDetailPage(html: String, baseUrl: String): PlaylistDetail? {
        try {
            val doc = Jsoup.parse(html, baseUrl)

            val title = doc.selectFirst(".playlist-title")?.text()?.trim() ?: ""
            val coverUrl = doc.selectFirst(".playlist-main-thumbnail")?.attr("abs:src") ?: ""

            val authorInfo = doc.selectFirst(".playlist-author-info")
            val author = authorInfo?.selectFirst("a")?.text()?.trim() ?: ""
            val authorAvatarUrl = authorInfo?.selectFirst("img.author-avatar")?.attr("abs:src") ?: ""

            val statsText = doc.selectFirst(".playlist-stats")?.text()?.trim() ?: ""
            val videoCountMatch = Regex("(\\d+)").find(statsText)
            val videoCount = videoCountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val viewCountMatch = Regex("觀看次數：([\\d,]+)\\s*次").find(statsText)
            val viewCount = viewCountMatch?.groupValues?.get(1) ?: "0"

            val description = doc.selectFirst(".playlist-description")?.text()?.trim() ?: ""

            val videos = parsePlaylistDetailVideos(doc, baseUrl)

            return PlaylistDetail(
                title = title,
                coverUrl = coverUrl,
                author = author,
                authorAvatarUrl = authorAvatarUrl,
                videoCount = videoCount,
                viewCount = viewCount,
                description = description,
                videos = videos
            )
        } catch (e: Exception) {
            AppLogger.logError("PlaylistParser", "Error parsing playlist detail page: ${e.message}", e)
            return null
        }
    }

    // 解析作者页面中的播放列表区块
    fun parseSectionPlaylists(doc: Document, baseUrl: String, sectionTitle: String): List<PlaylistSummary> {
        val playlists = mutableListOf<PlaylistSummary>()
        val sectionLinks = doc.select("a.horizontal-row-title")

        for (link in sectionLinks) {
            val h3 = link.selectFirst("h3") ?: continue
            val h3Text = h3.ownText().trim()
            val h3FullText = h3.text().trim()

            val matches = h3Text.startsWith(sectionTitle) ||
                          h3Text.contains(sectionTitle) ||
                          (sectionTitle == "播放清单" && (h3Text.startsWith("播放清單") || h3Text.contains("播放清單"))) ||
                          (sectionTitle == "影片" && (h3Text.startsWith("影片")))

            if (matches) {
                var sibling = link.nextElementSibling()
                var siblingIndex = 0
                while (sibling != null && siblingIndex < 10) {
                    siblingIndex++

                    val wrapper = sibling.selectFirst(".home-rows-videos-wrapper")
                    if (wrapper != null) {
                        val items = wrapper.select(".video-item-container")

                        for ((idx, item) in items.withIndex()) {
                            try {
                                val videoLink = item.selectFirst("a.video-link")
                                val playlistUrl = videoLink?.attr("abs:href") ?: ""

                                if (playlistUrl.isEmpty()) {
                                    continue
                                }

                                val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                                val title = item.selectFirst(".title")?.text()?.trim() ?: ""
                                val author = item.selectFirst(".subtitle a")?.text()?.trim() ?: ""
                                val subtitleTime = item.selectFirst(".subtitle-time")?.text()?.trim()?.replace("•", "")?.trim() ?: ""

                                val statsContainer = item.selectFirst(".stats-container")
                                val videoCount = statsContainer?.selectFirst(".stat-item")?.text()?.trim() ?: ""

                                playlists.add(PlaylistSummary(
                                    title = title,
                                    thumbnailUrl = thumbnail,
                                    videoCount = videoCount,
                                    author = author,
                                    publishTime = subtitleTime,
                                    playlistUrl = playlistUrl
                                ))
                            } catch (e: Exception) {
                                AppLogger.logError("PlaylistParser", "Error parsing section playlist item: ${e.message}", e)
                            }
                        }
                        break
                    }
                    sibling = sibling.nextElementSibling()
                }
                break
            }
        }
        return playlists
    }

    private fun parsePlaylistSummaries(doc: Document, baseUrl: String): List<PlaylistSummary> {
        val playlists = mutableListOf<PlaylistSummary>()
        val items = doc.select(".video-item-container")
        for (item in items) {
            try {
                val link = item.selectFirst("a.video-link")
                val playlistUrl = link?.attr("abs:href") ?: ""
                if (playlistUrl.isEmpty()) continue

                val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                val title = item.selectFirst(".title")?.text()?.trim() ?: ""
                val author = item.selectFirst(".subtitle a")?.text()?.trim() ?: ""
                val subtitleTime = item.selectFirst(".subtitle-time")?.text()?.trim()?.replace("•", "")?.trim() ?: ""
                val statsContainer = item.selectFirst(".stats-container")
                val videoCount = statsContainer?.selectFirst(".stat-item")?.text()?.trim() ?: ""

                playlists.add(PlaylistSummary(
                    title = title,
                    thumbnailUrl = thumbnail,
                    videoCount = videoCount,
                    author = author,
                    publishTime = subtitleTime,
                    playlistUrl = playlistUrl
                ))
            } catch (e: Exception) {
                AppLogger.logError("PlaylistParser", "Error parsing playlist summary: ${e.message}", e)
            }
        }
        return playlists
    }

    private fun parsePlaylistDetailVideos(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        val items = doc.select(".playlist-video-card")
        for (item in items) {
            try {
                val videoLink = item.selectFirst("a[href*=\"watch?v=\"]")
                val videoUrl = videoLink?.attr("abs:href") ?: ""
                if (videoUrl.isEmpty()) continue

                val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
                val duration = item.selectFirst(".duration")?.text()?.trim() ?: ""

                val statsContainer = item.selectFirst(".stats-container")
                val likeRate = statsContainer?.selectFirst(".stat-item")?.text()?.trim()?.let { ParserUtils.cleanLikeRate(it) } ?: ""
                val viewCount = statsContainer?.select("div.stat-item")?.getOrNull(1)?.text() ?: ""

                val title = item.selectFirst(".video-title a")?.text()?.trim() ?: ""
                val author = item.selectFirst(".meta-author a")?.text()?.trim() ?: ""
                val publishTime = item.selectFirst(".meta-stats span")?.text()?.trim() ?: ""

                val videoId = ParserUtils.extractVideoId(videoUrl)

                videos.add(HanimeVideo(
                    id = videoId,
                    title = title,
                    thumbnailUrl = thumbnail,
                    duration = duration,
                    likeRate = likeRate,
                    viewCount = viewCount,
                    author = author,
                    publishTime = publishTime,
                    videoUrl = videoUrl
                ))
            } catch (e: Exception) {
                AppLogger.logError("PlaylistParser", "Error parsing playlist detail video: ${e.message}", e)
            }
        }
        return videos
    }
}
