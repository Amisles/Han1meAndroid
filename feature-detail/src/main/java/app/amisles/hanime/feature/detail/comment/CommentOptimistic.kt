package app.amisles.hanime.feature.detail.comment

import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply

internal object CommentOptimistic {

    fun toggleLike(comment: Comment): Comment {
        val willLike = comment.likeStatus != 1
        return comment.copy(
            likeStatus = if (willLike) 1 else 0,
            likeCount = (comment.likeCount + if (willLike) 1 else -1).coerceAtLeast(0)
        )
    }

    fun adjustReplyCount(comments: List<Comment>, commentId: String, delta: Int): List<Comment> =
        comments.map { comment ->
            if (comment.id == commentId) {
                comment.copy(replyCount = (comment.replyCount + delta).coerceAtLeast(0))
            } else {
                comment
            }
        }

    fun appendReply(cache: Map<String, List<Reply>>, commentId: String, reply: Reply): Map<String, List<Reply>> =
        cache.toMutableMap().apply {
            this[commentId] = (this[commentId] ?: emptyList()) + reply
        }

    fun replaceReply(
        cache: Map<String, List<Reply>>,
        commentId: String,
        removeId: String,
        reply: Reply
    ): Map<String, List<Reply>> = cache.toMutableMap().apply {
        this[commentId] = (this[commentId] ?: emptyList()).filter { it.id != removeId } + reply
    }

    fun removeReply(cache: Map<String, List<Reply>>, commentId: String, removeId: String): Map<String, List<Reply>> =
        cache.toMutableMap().apply {
            val list = this[commentId] ?: return@apply
            this[commentId] = list.filter { it.id != removeId }
        }
}
