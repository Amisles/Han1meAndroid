package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoListParser @Inject constructor() {

    /**
     * 解析页面中的视频列表，兼容两种卡片结构。
     *
     * 站点存在两套卡片：
     * 1. 常规卡片 `.video-item-container`（内含 .video-link / .title / .main-thumb / .stats-container）；
     * 2. **简化卡片** `.home-rows-videos-div`（里番 / 泡面番 等搜索结果页使用）——
     *    不含 .video-item-container，若只按常规选择器解析会得到空列表，
     *    表现为「浏览器打开链接有结果，App 内搜索却为空」。
     *
     * 两类都解析并按 videoId 去重（保留首次出现、保持顺序），避免同一视频重复出现。
     */
    fun parseVideoList(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = LinkedHashMap<String, HanimeVideo>()

        for (container in doc.select(".video-item-container")) {
            parseSingleVideoContainer(container, baseUrl)?.let { videos.putIfAbsent(it.id, it) }
        }

        for (card in doc.select(".home-rows-videos-div")) {
            parseSimpleVideoCard(card, baseUrl)?.let { videos.putIfAbsent(it.id, it) }
        }

        return videos.values.toList()
    }

    /**
     * 解析简化卡片（.home-rows-videos-div），与常规卡片的结构差异：
     * - 视频链接在卡片**外层**的 `<a>` 上，而非内部的 .video-link；
     * - 标题是 .home-rows-videos-title，而非 .title / .video-title；
     * - 没有 .duration / .stats-container / .subtitle，这些字段留空。
     *
     * 链接同时兼容「卡片内部有 <a>」与「<a> 是卡片的直接父级」两种形态。
     */
    fun parseSimpleVideoCard(card: Element, baseUrl: String): HanimeVideo? {
        return try {
            val link = card.selectFirst("a[href*=\"watch?v=\"]")
                ?: card.parent()?.takeIf { it.tagName() == "a" && it.attr("href").contains("watch?v=") }
            val rawUrl = link?.attr("abs:href").orEmpty()
            if (rawUrl.isEmpty()) return null

            val videoId = ParserUtils.extractVideoId(rawUrl)
            if (videoId.isEmpty()) return null

            val title = card.selectFirst(".home-rows-videos-title")?.text()?.trim()
                ?: card.selectFirst(".title")?.text()?.trim()
                ?: return null

            val rawThumbnail = card.selectFirst("img")?.attr("abs:src").orEmpty()
            val thumbnailUrl = rawThumbnail.ifEmpty { ParserUtils.generatePlaceholderThumbnail(videoId) }

            HanimeVideo(
                id = videoId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                duration = card.selectFirst(".duration")?.text()?.trim() ?: "",
                likeRate = "",
                viewCount = "",
                author = "",
                publishTime = "",
                videoUrl = rawUrl
            )
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("VideoListParser", "Error parsing simple video card: ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            AppLogger.logError("VideoListParser", "Error parsing simple video card: ${e.message}", e)
            null
        }
    }

    fun parseSingleVideoContainer(container: Element, baseUrl: String): HanimeVideo? {
        return try {
            val videoLink: Element? = container.selectFirst(".video-link")
                ?: container.selectFirst(".thumb-container a")
            val rawVideoUrl = videoLink?.let { it.attr("abs:href").ifEmpty { it.attr("href") } } ?: ""
            if (rawVideoUrl.isEmpty()) return null
            val videoUrl = rawVideoUrl

            val videoId = ParserUtils.extractVideoId(videoUrl)
            if (videoId.isEmpty()) return null

            val title: Element? = container.selectFirst(".title")
                ?: container.selectFirst(".video-title")
            val titleText = title?.text()?.trim() ?: return null

            val thumbnail: Element? = container.selectFirst(".main-thumb")
            val rawThumbnailUrl = thumbnail?.let { it.attr("abs:src").ifEmpty { it.attr("src") } } ?: ""
            val thumbnailUrl = rawThumbnailUrl.ifEmpty { ParserUtils.generatePlaceholderThumbnail(videoId) }

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
