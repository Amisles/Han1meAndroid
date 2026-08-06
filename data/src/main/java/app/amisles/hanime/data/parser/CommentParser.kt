package app.amisles.hanime.data.parser

import android.util.Log
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.Comment
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 评论解析器
 *
 * 解析官网 loadComment 接口返回的 JSON：
 * {
 *   "comments": "<HTML 字符串>",
 *   "content": "comment-tablink"
 * }
 *
 * HTML 结构：
 * <div id="comment-create-form-wrapper">...</div>          // 输入框，跳过
 * <div id="comment-start">
 *   <a><img class="img-circle" src="avatar"></a>            // 头像
 *   <div class="report-btn-wrapper">                        // 评论主体
 *     <div class="comment-index-text"><a>username&nbsp;<span>time</span></a></div>
 *     <div class="comment-index-text">content</div>
 *     <span class="report-btn" data-reportable-id="commentId">more_vert</span>
 *   </div>
 *   <div id="comment-like-form-wrapper">                    // 点赞/回复区
 *     <span class="material-icons-outlined">thumb_up</span>
 *     <span>likeCount</span>                                // 可能为 display:none
 *     <span class="material-icons-outlined">thumb_down</span>
 *     <span>回复</span>
 *     <div class="load-replies-btn" data-commentid="...">查看 N 则回覆</div>  // 可选
 *   </div>
 *   <br>
 *   ... 下一条评论
 * </div>
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
        } catch (e: Exception) {
            AppLogger.logError("CommentParser", "Failed to parse comments JSON: ${e.message}", e)
            emptyList()
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
            } catch (e: Exception) {
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
}
