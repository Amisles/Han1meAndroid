package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通用视频列表解析器
 */
@Singleton
class VideoListParser @Inject constructor() {

    // 从Document解析视频列表
    fun parseVideoList(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()

        val videoContainers: Elements = doc.select(".video-item-container")

        if (videoContainers.isEmpty()) {
            val alternativeSelectors = listOf(
                ".video-card", ".video-item", ".card", ".horizontal-card", "[class*=video]", "[class*=card]"
            )
            for (selector in alternativeSelectors) {
                val elements = doc.select(selector)
            }
        }

        for (container in videoContainers) {
            parseSingleVideoContainer(container, baseUrl)?.let { videos.add(it) }
        }
        return videos
    }

    // 解析单个视频容器
    fun parseSingleVideoContainer(container: Element, baseUrl: String): HanimeVideo? {
        return try {
            val videoLink: Element? = container.selectFirst(".video-link")
                ?: container.selectFirst(".thumb-container a")
            val videoUrl = videoLink?.attr("abs:href") ?: videoLink?.attr("href") ?: return null

            val videoId = ParserUtils.extractVideoId(videoUrl)
            if (videoId.isEmpty()) return null

            val title: Element? = container.selectFirst(".title")
                ?: container.selectFirst(".video-title")
            val titleText = title?.text()?.trim() ?: return null

            val thumbnail: Element? = container.selectFirst(".main-thumb")
            val thumbnailUrl = thumbnail?.attr("abs:src") ?: thumbnail?.attr("src") ?: ParserUtils.generatePlaceholderThumbnail(videoId)

            val duration: Element? = container.selectFirst(".duration")
            val durationText = duration?.text()?.trim() ?: ""

            val statsContainer: Element? = container.selectFirst(".stats-container")
            var likeRate = ""
            var viewCount = ""
            if (statsContainer != null) {
                val statItems: Elements = statsContainer.select(".stat-item")
                for (stat in statItems) {
                    val text = stat.text().trim()
                    if (text.contains("%")) {
                        likeRate = ParserUtils.cleanLikeRate(text)
                    } else {
                        viewCount = text.trim()
                    }
                }
            }

            val subtitle: Element? = container.selectFirst(".subtitle a")
                ?: container.selectFirst(".meta-author a")
            var author = ""
            var publishTime = ""
            if (subtitle != null) {
                val subtitleText = subtitle.text().trim()
                val parts = subtitleText.split("•")
                if (parts.size >= 2) {
                    author = parts[0].trim()
                    publishTime = parts[1].trim()
                } else if (parts.size == 1) {
                    author = parts[0].trim()
                }
            }

            val finalVideoUrl = if (videoUrl.startsWith("/")) "$baseUrl$videoUrl" else videoUrl

            HanimeVideo(
                id = videoId,
                title = titleText,
                thumbnailUrl = thumbnailUrl,
                duration = durationText,
                likeRate = likeRate,
                viewCount = viewCount,
                author = author,
                publishTime = publishTime,
                videoUrl = finalVideoUrl
            )
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("VideoListParser", "Error parsing single video: ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            AppLogger.logError("VideoListParser", "Error parsing single video: ${e.message}", e)
            null
        }
    }

    // 解析指定区块的视频
    fun parseSectionVideos(doc: Document, baseUrl: String, sectionTitle: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        val sectionLinks = doc.select("a.horizontal-row-title")
        for (link in sectionLinks) {
            val h3 = link.selectFirst("h3") ?: continue
            val h3Text = h3.ownText().trim()
            if (h3Text.startsWith(sectionTitle)) {
                var sibling = link.nextElementSibling()
                while (sibling != null) {
                    val wrapper = sibling.selectFirst(".home-rows-videos-wrapper")
                    if (wrapper != null) {
                        val items = wrapper.select(".video-item-container")
                        for (item in items) {
                            val video = parseVideoItem(item, baseUrl) ?: continue
                            videos.add(video)
                        }
                        break
                    }
                    sibling = sibling.nextElementSibling()
                }
                break
            }
        }
        return videos
    }

    // 解析单个视频项（作者页面等场景）
    fun parseVideoItem(item: Element, baseUrl: String): HanimeVideo? {
        try {
            val videoLink = item.selectFirst("a.video-link")
            val videoUrl = videoLink?.attr("abs:href") ?: ""
            if (videoUrl.isEmpty()) return null

            val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
            val duration = item.selectFirst(".duration")?.text()?.trim() ?: ""

            val statsContainer = item.selectFirst(".stats-container")
            val likeRate = statsContainer?.selectFirst(".stat-item")?.text()?.trim()?.let { ParserUtils.cleanLikeRate(it) } ?: ""
            val viewCount = statsContainer?.select("div.stat-item")?.getOrNull(1)?.text() ?: ""

            val title = item.selectFirst(".title")?.text()?.trim() ?: ""
            val author = item.selectFirst(".subtitle a")?.text()?.trim() ?: ""
            val subtitleTime = item.selectFirst(".subtitle-time")?.text()?.trim() ?: ""
            val publishTime = subtitleTime.replace("•", "").trim()

            val videoId = ParserUtils.extractVideoId(videoUrl)

            return HanimeVideo(
                id = videoId,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                likeRate = likeRate,
                viewCount = viewCount,
                author = author,
                publishTime = publishTime,
                videoUrl = videoUrl
            )
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("VideoListParser", "Error parsing video item: ${e.message}", e)
            return null
        } catch (e: NullPointerException) {
            AppLogger.logError("VideoListParser", "Error parsing video item: ${e.message}", e)
            return null
        }
    }

    // 解析用户标签视频项
    fun parseUserTabVideoItem(item: Element, baseUrl: String): HanimeVideo? {
        try {
            val videoLink = item.selectFirst("a[href*=\"watch?v=\"]")
            val videoUrl = videoLink?.attr("abs:href") ?: return null

            val thumbnail = item.selectFirst("img.main-thumb")?.attr("abs:src") ?: ""
            val duration = item.selectFirst(".duration")?.text()?.trim() ?: ""

            val title = item.selectFirst(".title")?.text()?.trim() ?: ""

            val videoId = ParserUtils.extractVideoId(videoUrl)

            return HanimeVideo(
                id = videoId,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                likeRate = "",
                viewCount = "",
                author = "",
                publishTime = "",
                videoUrl = videoUrl
            )
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("VideoListParser", "Error parsing user tab video item: ${e.message}", e)
            return null
        } catch (e: NullPointerException) {
            AppLogger.logError("VideoListParser", "Error parsing user tab video item: ${e.message}", e)
            return null
        }
    }

    // 解析作者上传的视频列表
    fun parseAuthorVideos(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = mutableListOf<HanimeVideo>()
        val videoItems = doc.select(".video-item-container")
        for (item in videoItems) {
            val video = parseVideoItem(item, baseUrl) ?: continue
            videos.add(video)
        }
        return videos
    }
}
