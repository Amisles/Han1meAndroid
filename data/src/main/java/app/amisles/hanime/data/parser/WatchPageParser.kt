package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 视频详情页解析器
 */
class WatchPageParser(
    private val videoListParser: VideoListParser = VideoListParser(),
    private val playlistParser: PlaylistParser = PlaylistParser()
) {

    fun parse(html: String, baseUrl: String): VideoDetail? {
        AppLogger.log("WatchPageParser", "parse called")
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            val videoTag: Element? = doc.selectFirst("video#player")
            val defaultSourceUrl = videoTag?.attr("abs:src") ?: videoTag?.attr("src") ?: ""
            val posterUrl = videoTag?.attr("abs:poster") ?: videoTag?.attr("poster") ?: ""
            AppLogger.log("WatchPageParser", "Video src: $defaultSourceUrl")
            AppLogger.log("WatchPageParser", "Poster: $posterUrl")

            val sources = mutableListOf<VideoSource>()
            val sourceTags = doc.select("video#player source")
            for (source in sourceTags) {
                val src = source.attr("abs:src") ?: source.attr("src") ?: ""
                val size = source.attr("size")?.toIntOrNull() ?: 0
                val type = source.attr("type") ?: ""
                if (src.isNotEmpty()) {
                    sources.add(VideoSource(url = src, resolution = "${size}p", size = size))
                }
            }
            sources.sortByDescending { it.size }
            AppLogger.log("WatchPageParser", "Found ${sources.size} video sources")

            val titleElement = doc.selectFirst("h3#shareBtn-title")
                ?: doc.selectFirst("h3.single-video-title")
                ?: doc.selectFirst("h3")
            val rawTitle = titleElement?.text()?.trim() ?: ""
            val title = if (rawTitle.startsWith("[")) {
                val closingBracket = rawTitle.indexOf(']')
                if (closingBracket > 0 && closingBracket < rawTitle.length - 1) {
                    rawTitle.substring(closingBracket + 1).trim()
                } else rawTitle
            } else rawTitle
            AppLogger.log("WatchPageParser", "Title: $title (raw: $rawTitle)")

            val tags = mutableListOf<String>()
            val tagElements = doc.select(".video-tags-wrapper .single-video-tag a")
            val seenTags = mutableSetOf<String>()
            for (link in tagElements) {
                val tagText = link.text().trim()
                if (tagText.isNotEmpty() && !seenTags.contains(tagText) && tagText.length < 30) {
                    tags.add(tagText)
                    seenTags.add(tagText)
                }
            }
            AppLogger.log("WatchPageParser", "Found ${tags.size} tags")

            val releaseDate = Regex("(20\\d{2}/\\d{2}/\\d{2})").find(html)?.value ?: ""

            val fileSizeMatch = Regex("([\\d.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(html)
            val fileSize = fileSizeMatch?.value ?: ""

            val author = doc.selectFirst("a#video-artist-name")?.text()?.trim() ?: ""
            AppLogger.log("WatchPageParser", "Author: $author")

            val authorLink = doc.selectFirst("a[href*=\"/user/\"]")
            val authorPageUrl = authorLink?.attr("abs:href") ?: ""

            val avatarContainer = doc.selectFirst("div[style*=\"position: relative; display: inline-block;\"]")
            val authorAvatarUrl = avatarContainer?.selectFirst("img[style*=\"position: absolute\"]")?.attr("abs:src")
                ?: avatarContainer?.selectFirst("img#video-user-avatar")?.attr("abs:src")
                ?: ""
            AppLogger.log("WatchPageParser", "Author avatar: $authorAvatarUrl")

            val description = doc.selectFirst(".video-caption-text")?.wholeText()?.trim() ?: ""
            AppLogger.log("WatchPageParser", "Description length: ${description.length}")

            val relatedVideos = videoListParser.parseVideoList(doc, baseUrl)
            AppLogger.log("WatchPageParser", "Found ${relatedVideos.size} related videos")

            val playlist = playlistParser.parse(doc, baseUrl)
            AppLogger.log("WatchPageParser", "Playlist: ${playlist != null}")

            val filteredRelatedVideos = if (playlist != null) {
                val playlistUrls = playlist.videos.map { it.videoUrl }.toSet()
                relatedVideos.filter { it.videoUrl !in playlistUrls }
            } else {
                relatedVideos
            }
            AppLogger.log("WatchPageParser", "Filtered related videos: ${filteredRelatedVideos.size} (removed ${relatedVideos.size - filteredRelatedVideos.size} playlist duplicates)")

            if (defaultSourceUrl.isEmpty() && sources.isNotEmpty()) {
                val defaultSource = sources.find { it.size == 720 } ?: sources.first()
                return VideoDetail(
                    title = title,
                    posterUrl = posterUrl,
                    videoSources = sources,
                    defaultSourceUrl = defaultSource.url,
                    tags = tags,
                    releaseDate = releaseDate,
                    fileSize = fileSize,
                    author = author,
                    authorAvatarUrl = authorAvatarUrl,
                    authorPageUrl = authorPageUrl,
                    description = description,
                    relatedVideos = filteredRelatedVideos,
                    playlist = playlist
                )
            }

            return VideoDetail(
                title = title,
                posterUrl = posterUrl,
                videoSources = sources,
                defaultSourceUrl = defaultSourceUrl,
                tags = tags,
                releaseDate = releaseDate,
                fileSize = fileSize,
                author = author,
                authorAvatarUrl = authorAvatarUrl,
                authorPageUrl = authorPageUrl,
                description = description,
                relatedVideos = filteredRelatedVideos,
                playlist = playlist
            )
        } catch (e: Exception) {
            AppLogger.logError("WatchPageParser", "Error parsing watch page: ${e.message}", e)
            return null
        }
    }
}
