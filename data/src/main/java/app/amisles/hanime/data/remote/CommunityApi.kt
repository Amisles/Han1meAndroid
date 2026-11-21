package app.amisles.hanime.data.remote

import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.result.DomainError
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.FormBody

@Singleton
class CommunityApi @Inject constructor(
    private val transport: HttpTransport
) {
    /**
     * 拉取视频评论 JSON（GET /loadComment?id={videoId}&type=video&content=comment-tablink），
     * 返回 {"comments": "<HTML>", "content": "comment-tablink"}。
     */
    suspend fun fetchComments(videoId: String, page: Int = 1): String {
        val baseUrl = transport.baseUrl
        val url = if (page > 1) {
            "$baseUrl/loadComment?id=$videoId&type=video&content=comment-tablink&page=$page"
        } else {
            "$baseUrl/loadComment?id=$videoId&type=video&content=comment-tablink"
        }
        AppLogger.log("CommunityApi", "Fetching comments from: ${url.redactUrlForLog()}")
        return transport.get(url)
    }

    /**
     * 拉取评论回复 JSON（GET /loadReplies?id={commentId}），
     * 返回 {"comment_id": "commentId", "replies": "<HTML>"}。
     */
    suspend fun fetchReplies(commentId: String): String {
        val url = transport.baseUrl + "/loadReplies?id=" + commentId
        AppLogger.log("CommunityApi", "Fetching replies from: ${url.redactUrlForLog()}")
        return transport.get(url)
    }

    /**
     * 发表视频评论（POST /createComment，表单 + x-csrf-token + x-requested-with）。
     * 参数：_token、comment-user-id、comment-type=video、comment-foreign-id、
     * comment-count、comment-text。
     */
    suspend fun postComment(
        videoId: String,
        commentText: String,
        commentCount: Int,
        csrfToken: String,
        userId: String
    ): String {
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("comment-user-id", userId)
            .add("comment-type", "video")
            .add("comment-foreign-id", videoId)
            .add("comment-count", commentCount.toString())
            .add("comment-text", commentText)
            .build()
        return transport.postForm("/createComment", form, csrfToken, "发表评论", DomainError.COMMENT_POST_FAILED)
    }

    /**
     * 切换评论点赞状态（POST /commentLike，表单 + x-csrf-token + x-requested-with）。
     * 参数：_token、foreign_type=comment、foreign_id、is_positive=1、comment-like-user-id、
     * like-comment-status（空 = 本次点赞；"1" = 本次取消）、comment-likes-count / comment-likes-sum、
     * unlike-comment-status=0。
     */
    suspend fun toggleCommentLike(
        commentId: String,
        commentLikeUserId: String,
        currentLikeStatus: Int,
        likeCount: Int,
        csrfToken: String
    ): String {
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("foreign_type", "comment")
            .add("foreign_id", commentId)
            .add("is_positive", "1")
            .add("comment-like-user-id", commentLikeUserId)
            .add("like-comment-status", if (currentLikeStatus == 1) "1" else "")
            .add("comment-likes-count", likeCount.toString())
            .add("comment-likes-sum", likeCount.toString())
            .add("unlike-comment-status", "0")
            .build()
        return transport.postForm("/commentLike", form, csrfToken, "评论点赞", DomainError.COMMENT_LIKE_FAILED)
    }

    /**
     * 发表评论回复（POST /replyComment，表单 + x-csrf-token + x-requested-with）。
     * 参数：_token、reply-comment-id、reply-comment-text（回复其他回复时由前端拼上 "@用户名 " 前缀）。
     */
    suspend fun replyComment(commentId: String, replyText: String, csrfToken: String): String {
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("reply-comment-id", commentId)
            .add("reply-comment-text", replyText)
            .build()
        return transport.postForm("/replyComment", form, csrfToken, "回复评论", DomainError.COMMENT_REPLY_FAILED)
    }

    /**
     * 订阅 / 取消订阅作者（POST /subscribe，表单 + x-csrf-token + x-requested-with）。
     * 参数：_token、subscribe-user-id、subscribe-artist-id、subscribe-status（空 = 订阅；"1" = 取消订阅）。
     */
    suspend fun toggleSubscribe(
        csrfToken: String,
        userId: String,
        artistId: String,
        willSubscribe: Boolean
    ): String {
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("subscribe-user-id", userId)
            .add("subscribe-artist-id", artistId)
            .add("subscribe-status", if (willSubscribe) "" else "1")
            .build()
        return transport.postForm("/subscribe", form, csrfToken, "订阅作者", DomainError.SUBSCRIBE_FAILED)
    }

    suspend fun saveToPlaylist(
        videoId: String,
        target: String,
        saved: Boolean,
        csrfToken: String
    ): String {
        val form = FormBody.Builder()
            .add("input_id", target)
            .add("user_id", "")
            .add("video_id", videoId)
            .add("is_checked", saved.toString())
            .build()
        return transport.postForm("/save", form, csrfToken, "保存到清单", DomainError.PLAYLIST_SAVE_FAILED)
    }

    suspend fun createPlaylist(
        videoId: String,
        title: String,
        description: String,
        csrfToken: String
    ): String {
        val form = FormBody.Builder()
            .add("_token", csrfToken)
            .add("create-playlist-video-id", videoId)
            .add("playlist-title", title)
            .add("playlist-description", description)
            .build()
        return transport.postForm("/createPlaylist", form, csrfToken, "创建清单", DomainError.PLAYLIST_CREATE_FAILED)
    }
}
