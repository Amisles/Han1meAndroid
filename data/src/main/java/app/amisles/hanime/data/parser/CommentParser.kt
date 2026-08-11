package app.amisles.hanime.data.parser

import android.util.Log
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 评论解析器
 */
@Singleton
class CommentParser @Inject constructor() {

    fun parse(json: String): List<Comment> {
        if (json.isBlank()) return emptyList()
        return try {
            val html = JSONObject(json).optString("comments", "")
            if (html.isEmpty()) {
                AppLogger.log("CommentParser", "comments field is empty")
                return emptyList()
            }
            parseHtml(html)
        } catch (e: JSONException) {
            AppLogger.logError("CommentParser", "Failed to parse comments JSON: ${e.message}", e)
            emptyList()
        } catch (e: NullPointerException) {
            AppLogger.logError("CommentParser", "Failed to parse comments JSON: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 解析 createComment 接口返回的 JSON。
     *
     * 官网响应：
     * {
     *   "comment_id": 502418,
     *   "comment_count": 14,
     *   "single_video_comment": "<单条评论 HTML>"
     * }
     *
     * single_video_comment 的结构与详情页评论列表单条评论相同：
     * <a><img class="img-circle" src="avatar"></a>
     * <div class="report-btn-wrapper">...</div>
     * <div id="comment-like-form-wrapper">...</div>
     *
     * @return Pair(新评论对象, 新评论总数)，解析失败时为 null
     */
    fun parsePostedComment(json: String): Pair<Comment, Int>? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val newCount = obj.optInt("comment_count", 0)
            val html = obj.optString("single_video_comment", "")
            if (html.isEmpty()) {
                AppLogger.log("CommentParser", "single_video_comment field is empty")
                return null
            }
            val doc = Jsoup.parse(html)
            // single_video_comment 直接以 <a><img class="img-circle"> 开头
            val img = doc.selectFirst("img.img-circle") ?: run {
                AppLogger.log("CommentParser", "No avatar found in posted comment HTML")
                return null
            }
            val comment = parseSingleComment(img) ?: return null
            Pair(comment, newCount)
        } catch (e: JSONException) {
            AppLogger.logError("CommentParser", "Failed to parse posted comment: ${e.message}", e)
            null
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("CommentParser", "Failed to parse posted comment: ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            AppLogger.logError("CommentParser", "Failed to parse posted comment: ${e.message}", e)
            null
        }
    }

    private fun parseHtml(html: String): List<Comment> {
        val doc = Jsoup.parse(html)
        val commentStart = doc.selectFirst("#comment-start") ?: run {
            AppLogger.log("CommentParser", "No #comment-start container found")
            return emptyList()
        }

        val result = mutableListOf<Comment>()
        // 在 comment-start 内查找所有评论头像（form-wrapper 在 comment-start 之外，不会误抓）
        val avatars = commentStart.select("img.img-circle")
        AppLogger.log("CommentParser", "Found ${avatars.size} comment avatars")

        for (img in avatars) {
            try {
                val comment = parseSingleComment(img) ?: continue
                result.add(comment)
            } catch (e: IndexOutOfBoundsException) {
                Log.w("CommentParser", "Skip a malformed comment: ${e.message}")
            } catch (e: NullPointerException) {
                Log.w("CommentParser", "Skip a malformed comment: ${e.message}")
            }
        }
        return result
    }

    private fun parseSingleComment(img: org.jsoup.nodes.Element): Comment? {
        val avatarUrl = img.absUrl("src").ifEmpty { img.attr("src") }

        // img.parent() = <a>，<a>.nextElementSibling() = <div.report-btn-wrapper>
        val aElement = img.parent() ?: return null
        val reportWrapper = aElement.nextElementSibling() ?: return null
        if (!reportWrapper.hasClass("report-btn-wrapper")) return null

        // 评论 ID
        val reportBtn = reportWrapper.selectFirst("span.report-btn[data-reportable-id]")
            ?: return null
        val commentId = reportBtn.attr("data-reportable-id").trim()
        if (commentId.isEmpty()) return null

        // 两个 comment-index-text：第一个是"用户名 + 时间"，第二个是评论内容
        val textDivs = reportWrapper.select("div.comment-index-text")
        if (textDivs.size < 2) return null

        val headerLink = textDivs[0].selectFirst("a")
        // ownText() 不包含子元素文本，正好剥离 <span>时间</span>
        val username = headerLink?.ownText()
            ?.replace("\u00a0", " ")  // &nbsp; → 空格
            ?.trim()
            ?: ""
        val time = textDivs[0].selectFirst("span")?.text()?.trim() ?: ""
        val content = textDivs[1].text().trim()

        // 点赞 / 回复区
        val likeWrapper = reportWrapper.nextElementSibling()
        var likeCount = 0
        var replyCount = 0
        if (likeWrapper != null) {
            likeCount = parseLikeCount(likeWrapper)
            replyCount = parseReplyCount(likeWrapper)
        }

        return Comment(
            id = commentId,
            username = username,
            avatarUrl = avatarUrl,
            time = time,
            content = content,
            likeCount = likeCount,
            replyCount = replyCount
        )
    }

    /**
     * 解析点赞数：thumb_up 图标后的 span 文本（可能为负数，可能 display:none）。
     */
    private fun parseLikeCount(likeWrapper: org.jsoup.nodes.Element): Int {
        val icons = likeWrapper.select("span.material-icons-outlined")
        for (icon in icons) {
            if (icon.text().trim() == "thumb_up") {
                val countSpan = icon.nextElementSibling() ?: return 0
                if (countSpan.tagName() != "span") return 0
                // 注意：官网对 0 赞会加 display:none，但仍解析为 0
                return countSpan.text().trim().toIntOrNull() ?: 0
            }
        }
        return 0
    }

    /**
     * 解析回复数：load-replies-btn 文本形如 "查看 3 则回覆"，用正则提取数字。
     * 没有 load-replies-btn 表示该评论无回复。
     */
    private fun parseReplyCount(likeWrapper: org.jsoup.nodes.Element): Int {
        val btn = likeWrapper.selectFirst(".load-replies-btn") ?: return 0
        val text = btn.text()
        val match = Regex("(\\d+)").find(text) ?: return 0
        return match.value.toIntOrNull() ?: 0
    }

    // ==================== 回复解析 ====================

    /**
     * 解析官网 loadReply 接口返回的 JSON：
     * {
     *   "comment_id": "496354",
     *   "replies": "<HTML 字符串>"
     * }
     *
     * 回复 HTML 结构（与评论不同）：
     * <div id="reply-start-{commentId}">
     *   <div class="report-btn-wrapper">                // 每条回复的主体
     *     <a><img class="img-circle" src="avatar"></a>  // 头像在 report-btn-wrapper 内部
     *     <div class="comment-index-text"><a>username&nbsp;<span>time</span></a></div>
     *     <div class="comment-index-text">@被回复用户名 回复内容</div>  // @部分可能不存在
     *     <span class="report-btn" data-reportable-id="replyId" data-reportable-type="reply">more_vert</span>
     *   </div>
     *   <div style="padding-left: 45px">                 // 点赞区（无 id）
     *     <span class="material-icons-outlined">thumb_up</span>
     *     <span>likeCount</span>                         // 可能为 display:none
     *     <span class="material-icons-outlined">thumb_down</span>
     *     <span class="comment-reply-btn">回复</span>
     *   </div>
     *   ... 下一条回复
     * </div>
     */
    fun parseReplies(json: String): List<Reply> {
        if (json.isBlank()) return emptyList()
        return try {
            val html = JSONObject(json).optString("replies", "")
            if (html.isEmpty()) {
                AppLogger.log("CommentParser", "replies field is empty")
                return emptyList()
            }
            parseRepliesHtml(html)
        } catch (e: JSONException) {
            AppLogger.logError("CommentParser", "Failed to parse replies JSON: ${e.message}", e)
            emptyList()
        } catch (e: NullPointerException) {
            AppLogger.logError("CommentParser", "Failed to parse replies JSON: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseRepliesHtml(html: String): List<Reply> {
        val doc = Jsoup.parse(html)
        // 回复容器 id 形如 reply-start-{commentId}，直接查找所有 report-btn-wrapper
        val wrappers = doc.select("div.report-btn-wrapper")
        AppLogger.log("CommentParser", "Found ${wrappers.size} reply wrappers")

        val result = mutableListOf<Reply>()
        for (wrapper in wrappers) {
            try {
                val reply = parseSingleReply(wrapper) ?: continue
                result.add(reply)
            } catch (e: IndexOutOfBoundsException) {
                Log.w("CommentParser", "Skip a malformed reply: ${e.message}")
            } catch (e: NullPointerException) {
                Log.w("CommentParser", "Skip a malformed reply: ${e.message}")
            }
        }
        return result
    }

    private fun parseSingleReply(wrapper: org.jsoup.nodes.Element): Reply? {
        // 回复 ID
        val reportBtn = wrapper.selectFirst("span.report-btn[data-reportable-id]")
            ?: return null
        val replyId = reportBtn.attr("data-reportable-id").trim()
        if (replyId.isEmpty()) return null

        // 头像（在 report-btn-wrapper 内部的 <a><img> 中）
        val img = wrapper.selectFirst("img.img-circle") ?: return null
        val avatarUrl = img.absUrl("src").ifEmpty { img.attr("src") }

        // 两个 comment-index-text：第一个是"用户名 + 时间"，第二个是回复内容
        val textDivs = wrapper.select("div.comment-index-text")
        if (textDivs.size < 2) return null

        val headerLink = textDivs[0].selectFirst("a")
        val username = headerLink?.ownText()
            ?.replace("\u00a0", " ")
            ?.trim()
            ?: ""
        val time = textDivs[0].selectFirst("span")?.text()?.trim() ?: ""
        val rawContent = textDivs[1].text().trim()

        // 解析 @被回复用户名（回复其他回复时内容以 "@用户名 " 开头）
        val (replyTo, content) = parseMention(rawContent)

        // 点赞区：report-btn-wrapper 的下一个兄弟 div
        val likeWrapper = wrapper.nextElementSibling()
        val likeCount = if (likeWrapper != null) parseReplyLikeCount(likeWrapper) else 0

        return Reply(
            id = replyId,
            username = username,
            avatarUrl = avatarUrl,
            time = time,
            content = content,
            likeCount = likeCount,
            replyTo = replyTo
        )
    }

    /**
     * 从回复内容中提取 @被回复用户名。
     * 形如 "@铃 到时候..." → (replyTo="铃", content="到时候...")
     * 无 @前缀 → (replyTo=null, content=原文)
     */
    private fun parseMention(rawContent: String): Pair<String?, String> {
        if (!rawContent.startsWith("@")) return Pair(null, rawContent)
        // @用户名 后跟一个空格，用户名本身不含空格
        val spaceIndex = rawContent.indexOf(' ')
        if (spaceIndex <= 1) return Pair(null, rawContent)
        val mentionedUser = rawContent.substring(1, spaceIndex).trim()
        val actualContent = rawContent.substring(spaceIndex + 1).trim()
        if (mentionedUser.isEmpty() || actualContent.isEmpty()) {
            return Pair(null, rawContent)
        }
        return Pair(mentionedUser, actualContent)
    }

    /**
     * 解析回复的点赞数：thumb_up 图标后的 span 文本。
     * 回复的点赞区结构与评论略有不同（无 #comment-like-form-wrapper id），但解析方式相同。
     */
    private fun parseReplyLikeCount(likeWrapper: org.jsoup.nodes.Element): Int {
        val icons = likeWrapper.select("span.material-icons-outlined")
        for (icon in icons) {
            if (icon.text().trim() == "thumb_up") {
                val countSpan = icon.nextElementSibling() ?: return 0
                if (countSpan.tagName() != "span") return 0
                return countSpan.text().trim().toIntOrNull() ?: 0
            }
        }
        return 0
    }
}
