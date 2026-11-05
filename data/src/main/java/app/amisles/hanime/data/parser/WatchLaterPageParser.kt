package app.amisles.hanime.data.parser

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.HanimeVideo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/** 站点「稍后观看」列表页（/user/{id}/saves）解析：先按通用视频卡片解析，取不到时按链接兜底。 */
@Singleton
class WatchLaterPageParser @Inject constructor(
    private val videoListParser: VideoListParser
) {

    fun parse(html: String, baseUrl: String): List<HanimeVideo> {
        try {
            val doc = Jsoup.parse(html, baseUrl)
            val videos = videoListParser.parseVideoList(doc, baseUrl)
            if (videos.isNotEmpty()) return videos
            return parseAnchorCards(doc, baseUrl)
        } catch (e: IllegalArgumentException) {
            AppLogger.logError("WatchLaterPageParser", "Error parsing watch later page: ${e.message}", e)
            return emptyList()
        } catch (e: NullPointerException) {
            AppLogger.logError("WatchLaterPageParser", "Error parsing watch later page: ${e.message}", e)
            return emptyList()
        }
    }

    private fun parseAnchorCards(doc: Document, baseUrl: String): List<HanimeVideo> {
        val videos = LinkedHashMap<String, HanimeVideo>()
        for (anchor in doc.select("a[href*=\"watch?v=\"]")) {
            val rawUrl = anchor.attr("abs:href").ifEmpty { anchor.attr("href") }
            val videoId = ParserUtils.extractVideoId(rawUrl)
            if (videoId.isEmpty() || videos.containsKey(videoId)) continue

            val image = anchor.selectFirst("img") ?: continue
            val title = anchor.selectFirst(".title")?.text()?.trim().orEmpty().ifEmpty {
                anchor.attr("title").trim().ifEmpty { image.attr("alt").trim() }
            }
            if (title.isEmpty()) continue

            val thumbnail = image.attr("abs:src").ifEmpty { ParserUtils.generatePlaceholderThumbnail(videoId) }
            val duration = anchor.selectFirst(".duration")?.text()?.trim().orEmpty()
            val author = anchor.selectFirst(".subtitle a")?.text()?.trim().orEmpty()

            videos[videoId] = HanimeVideo(
                id = videoId,
                title = title,
                thumbnailUrl = thumbnail,
                duration = duration,
                likeRate = "",
                viewCount = "",
                author = author,
                publishTime = "",
                videoUrl = if (rawUrl.startsWith("/")) baseUrl.trimEnd('/') + rawUrl else rawUrl
            )
        }
        return videos.values.toList()
    }
}
