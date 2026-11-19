package app.amisles.hanime.feature.detail.comment

import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply

/** 评论页的全部可见状态。一次收集即可渲染，避免界面侧同时订阅十几个 Flow 出现半更新。 */
data class CommentUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** 评论是否已加载完成。用于区分「未加载」与「已加载但为空」。 */
    val loaded: Boolean = false,
    val error: String? = null,
    val isPosting: Boolean = false,
    val postError: String? = null,
    val likingIds: Set<String> = emptySet(),
    val replies: Map<String, List<Reply>> = emptyMap(),
    val loadingReplies: Set<String> = emptySet(),
    val replyErrors: Map<String, String?> = emptyMap(),
    val replyTarget: ReplyTarget? = null,
    val isPostingReply: Boolean = false,
    val replyError: String? = null,
    val expandedReplies: Set<String> = emptySet()
) {
    val replyPrefill: String
        get() = replyTarget?.replyToUsername?.let { "@$it " } ?: ""
}

/** 一次性事件：不需要留在状态里、展示完即丢弃的提示。 */
sealed interface CommentEvent {
    data class LikeFailed(val message: String) : CommentEvent
}
