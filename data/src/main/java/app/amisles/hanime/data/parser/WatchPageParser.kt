package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.core.common.util.AppLogger
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频详情页解析器
 */
@Singleton
class WatchPageParser @Inject constructor(
    private val videoListParser: VideoListParser,
    private val playlistParser: PlaylistParser
) {

    fun parse(html: String, baseUrl: String): VideoDetail? {
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            val videoTag: Element? = doc.selectFirst("video#player")
            val defaultSourceUrl = videoTag?.attr("abs:src") ?: videoTag?.attr("src") ?: ""
            val posterUrl = videoTag?.attr("abs:poster") ?: videoTag?.attr("poster") ?: ""

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

            val releaseDate = Regex("(20\\d{2}/\\d{2}/\\d{2})").find(html)?.value ?: ""

            val fileSizeMatch = Regex("([\\d.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(html)
            val fileSize = fileSizeMatch?.value ?: ""

            val author = doc.selectFirst("a#video-artist-name")?.text()?.trim() ?: ""

            val authorLink = doc.selectFirst("a[href*=\"/user/\"]")
            val authorPageUrl = authorLink?.attr("abs:href") ?: ""

            val avatarContainer = doc.selectFirst("div[style*=\"position: relative; display: inline-block;\"]")
            val authorAvatarUrl = avatarContainer?.selectFirst("img[style*=\"position: absolute\"]")?.attr("abs:src")
                ?: avatarContainer?.selectFirst("img#video-user-avatar")?.attr("abs:src")
                ?: ""

            val description = doc.selectFirst(".video-caption-text")?.wholeText()?.trim() ?: ""

            val relatedVideos = videoListParser.parseVideoList(doc, baseUrl)

            val playlist = playlistParser.parse(doc, baseUrl)

            val filteredRelatedVideos = if (playlist != null) {
                val playlistUrls = playlist.videos.map { it.videoUrl }.toSet()
                relatedVideos.filter { it.videoUrl !in playlistUrls }
            } else {
                relatedVideos
            }

            // 解析 CSRF Token
            val csrfToken = doc.selectFirst("meta[name=\"csrf-token\"]")?.attr("content")?.trim() ?: ""

            // 解析当前登录用户的数字 ID（点赞/评论接口需要），来自详情页内的隐藏域或 JS 全局变量
            val currentUserId = parseCurrentUserId(doc, html)

            // 解析当前评论数：优先从 createComment 表单的 comment-count 隐藏 input 获取
            val commentCount = doc.selectFirst("input[name=\"comment-count\"]")
                ?.attr("value")?.trim()?.toIntOrNull()
                ?: Regex("comment-count\"\\s+value=\"(\\d+)\"").find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 0

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
                    playlist = playlist,
                    csrfToken = csrfToken,
                    commentCount = commentCount,
                    currentUserId = currentUserId
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
                playlist = playlist,
                csrfToken = csrfToken,
                commentCount = commentCount,
                currentUserId = currentUserId
            )
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("WatchPageParser", "Error parsing watch page: ${e.message}", e)
            return null
        } catch (e: NullPointerException) {
            AppLogger.logError("WatchPageParser", "Error parsing watch page: ${e.message}", e)
            return null
        } catch (e: IllegalArgumentException) {
            AppLogger.logError("WatchPageParser", "Error parsing watch page: ${e.message}", e)
            return null
        }
    }

    /**
     * 从详情页 HTML 中解析当前登录用户的数字 ID。
     *
     * 官网的评论/点赞接口需要携带当前用户 ID（comment-user-id / comment-like-user-id），
     * 该值并不在登录 Cookie 中，而是随已登录会话在页面内渲染：
     *  - 评论表单的隐藏域 <input name="comment-user-id" value="...">
     *  - 点赞表单的隐藏域 <input name="comment-like-user-id" value="...">
     *  - <meta name="user-id" content="...">
     *  - JS 全局变量（如 window.userId = 123 或 {userId:123}）
     *
     * 按优先级尝试多种常见位置，命中即返回其数字值；均未命中返回空字符串。
     */
    private fun parseCurrentUserId(doc: Document, html: String): String {
        fun String?.toNumericId(): String? {
            if (this.isNullOrBlank()) return null
            val v = this.trim()
            return if (v.all { it.isDigit() }) v else null
        }

        // 1) meta 标签
        for (name in listOf("user-id", "user_id", "userId", "auth-user-id", "current-user-id")) {
            doc.selectFirst("meta[name=\"$name\"]")?.attr("content")?.toNumericId()
                ?.let { return it }
        }

        // 2) 隐藏输入框（评论/点赞表单常用字段名）
        for (name in listOf(
            "comment-user-id", "comment-like-user-id", "comment_user_id",
            "user-id", "user_id", "userId"
        )) {
            doc.selectFirst("input[name=\"$name\"]")?.attr("value")?.toNumericId()
                ?.let { return it }
        }

        // 3) data-user-id 属性
        doc.selectFirst("[data-user-id]")?.attr("data-user-id")?.toNumericId()?.let { return it }

        // 4) JS 全局变量 / 对象字段（兜底，覆盖 window.userId=123、{userId:123} 等）
        val jsPatterns = listOf(
            "(?:window\\.)?(?:userId|user_id|currentUserId|authUserId|loggedInUserId|auth_user_id|current_user_id)\\s*[:=]\\s*\"?(\\d+)\"?",
            "comment-like-user-id\"?\\s*[:=]\\s*\"?(\\d+)\"?",
            "\"user_id\"\\s*:\\s*\"?(\\d+)\"?",
            "\"userId\"\\s*:\\s*(\\d+)"
        )
        for (pattern in jsPatterns) {
            val m = runCatching { Regex(pattern).find(html) }.getOrNull() ?: continue
            m.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        return ""
    }
}
