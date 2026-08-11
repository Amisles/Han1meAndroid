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
        var likeStatus = 0
        if (likeWrapper != null) {
            likeCount = parseLikeCount(likeWrapper)
            replyCount = parseReplyCount(likeWrapper)
            // 重载后保持点赞高亮：从服务端返回的评论 HTML 解析当前用户点赞态
            likeStatus = parseLikeStatus(likeWrapper)
        }
        AppLogger.d("CommentParser", "comment $commentId likeStatus=$likeStatus likeCount=$likeCount")

        return Comment(
            id = commentId,
            username = username,
            avatarUrl = avatarUrl,
            time = time,
            content = content,
            likeCount = likeCount,
            replyCount = replyCount,
            likeStatus = likeStatus
        )
    }

    /**
     * 从点赞/点踩区提取某个图标（thumb_up/thumb_down）之后的数字（点赞/点踩数）。
     * 仅作为「读取隐藏 input 失败」时的兜底，因为评论列表里 thumb_up 的图标 class
     * 是 material-icons-sharp（点赞/取消响应片段里才是 material-icons-outlined），
     * 且可见数字 span 在 0 赞时带 display:none，不可靠。
     */
    private fun extractCountAfter(likeWrapper: org.jsoup.nodes.Element, iconText: String): Int {
        // 兼容两种图标 class：material-icons-outlined（响应片段）/ material-icons-sharp（评论列表）
        val icons = likeWrapper.select("span.material-icons-outlined, span.material-icons-sharp")
        for (icon in icons) {
            if (icon.text().trim() != iconText) continue
            // 1) 直接兄弟 span
            val sib = icon.nextElementSibling()
            if (sib != null && sib.tagName() == "span") {
                sib.text().trim().toIntOrNull()?.let { return it }
            }
            // 2) 父 <a> 的兄弟 span（图标与数字分处不同层级时）
            val parent = icon.parent()
            if (parent != null) {
                val pSib = parent.nextElementSibling()
                if (pSib != null && pSib.tagName() == "span") {
                    pSib.text().trim().toIntOrNull()?.let { return it }
                }
            }
            // 3) 容器内该图标之后第一个数字 span
            val spans = likeWrapper.select("span")
            var seen = false
            for (span in spans) {
                if (seen) span.text().trim().toIntOrNull()?.let { return it }
                if (span == icon) seen = true
            }
            return 0
        }
        return 0
    }

    /**
     * 解析评论点赞数。
     *
     * 官方评论区返回的每条评论都内嵌隐藏 input `comment-likes-sum` 存真实点赞数，
     * 这是权威值。评论列表里 thumb_up 的图标 class 为 material-icons-sharp，且其后的
     * 可见数字 span 在 0 赞时带 display:none，因此必须以隐藏 input 为准，可见 span
     * 仅作为兜底。
     */
    private fun parseLikeCount(likeWrapper: org.jsoup.nodes.Element): Int {
        // 优先读取隐藏 input（权威值）
        val sumInput = likeWrapper.selectFirst("input[name=comment-likes-sum]")
        sumInput?.attr("value")?.trim()?.toIntOrNull()?.let { return it }
        // 兜底：图标（material-icons-sharp / material-icons-outlined）后的数字 span
        return extractCountAfter(likeWrapper, "thumb_up")
    }

    /**
     * 解析当前用户对评论的点赞状态（0=未赞，1=已赞）。
     *
     * 官方标记位于点赞按钮内的隐藏 input `like-comment-status`：
     *   值为 "1" → 当前用户已赞；值为 "0" 或空 → 未赞。
     * （并非 CSS 类 liked/active，原先按类探测永远为 0。）
     * 重载评论列表时据此还原高亮，保证「点赞→重进页面→再次点击是取消而非重复点赞」。
     */
    private fun parseLikeStatus(likeWrapper: org.jsoup.nodes.Element): Int {
        val statusInput = likeWrapper.selectFirst("input[name=like-comment-status]")
        val v = statusInput?.attr("value")?.trim() ?: return 0
        return if (v == "1") 1 else 0
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

    /**
     * 解析 replyComment 接口返回的 JSON：
     * {
     *   "comment_id": <父评论ID>,
     *   "single_video_comment": "<新回复 HTML>",
     *   "csrf_token": "..."
     * }
     *
     * 与 loadReply 返回的回复结构不同，replyComment 的 single_video_comment 没有外层
     * report-btn-wrapper，而是：
     * <div style="padding-top: 12px">
     *   <a><img class="img-circle" src="avatar"></a>
     *   <div class="comment-index-text"><a>username&nbsp;<span>time</span></a></div>
     *   <div class="comment-index-text">content</div>
     * </div>
     * <div style="padding-left: 45px; ...">
     *   <form class="comment-like-form" ...>
     *     <input name="foreign_id" value="<回复ID>">   // 回复点赞用的 foreign_id 即回复 ID
     *     <input name="comment-likes-sum" value="0">
     *     ...
     *
     * @return 解析出的 Reply，失败为 null
     */
    fun parsePostedReply(json: String): Reply? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            val html = obj.optString("single_video_comment", "")
            if (html.isEmpty()) {
                AppLogger.log("CommentParser", "single_video_comment field is empty")
                return null
            }
            val doc = Jsoup.parse(html)
            val img = doc.selectFirst("img.img-circle") ?: run {
                AppLogger.log("CommentParser", "No avatar found in posted reply HTML")
                return null
            }
            val avatarUrl = img.absUrl("src").ifEmpty { img.attr("src") }
            val aElement = img.parent() ?: return null
            // 外层 <div style="padding-top: 12px">
            val scope = aElement.parent() ?: return null

            // 两个 comment-index-text：第一个"用户名 + 时间"，第二个"内容"
            val textDivs = scope.select("div.comment-index-text")
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

            // 回复 ID：优先找 span.report-btn[data-reportable-id]（可能存在于完整 HTML），
            // 否则取点赞表单里的 foreign_id（回复点赞用的 foreign_id 即回复 ID）。
            val replyId = doc.selectFirst("span.report-btn[data-reportable-id]")
                ?.attr("data-reportable-id")?.trim()
                .takeIf { !it.isNullOrEmpty() }
                ?: doc.selectFirst("input[name=foreign_id]")?.attr("value")?.trim()
                .takeIf { !it.isNullOrEmpty() }
                ?: "local_${System.currentTimeMillis()}"

            // 点赞区：scope 的下一个兄弟 div（含 comment-like-form）
            val likeWrapper = scope.nextElementSibling()
            val likeCount = if (likeWrapper != null) {
                likeWrapper.selectFirst("input[name=comment-likes-sum]")
                    ?.attr("value")?.trim()?.toIntOrNull()
                    ?: extractCountAfter(likeWrapper, "thumb_up")
            } else 0

            AppLogger.d("CommentParser", "Parsed posted reply id=$replyId username=$username")
            Reply(
                id = replyId,
                username = username,
                avatarUrl = avatarUrl,
                time = time,
                content = content,
                likeCount = likeCount,
                replyTo = replyTo
            )
        } catch (e: JSONException) {
            AppLogger.logError("CommentParser", "Failed to parse posted reply: ${e.message}", e)
            null
        } catch (e: IndexOutOfBoundsException) {
            AppLogger.logError("CommentParser", "Failed to parse posted reply: ${e.message}", e)
            null
        } catch (e: NullPointerException) {
            AppLogger.logError("CommentParser", "Failed to parse posted reply: ${e.message}", e)
            null
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
        return extractCountAfter(likeWrapper, "thumb_up")
    }
}
