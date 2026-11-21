package app.amisles.hanime.feature.detail.comment

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CommentComposerController(
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<CommentUiState>,
    private val currentVideoId: () -> String,
    private val loadReplies: (String) -> Unit,
    private val onEvent: (CommentEvent) -> Unit
) {
    fun startReply(commentId: String, replyToUsername: String? = null) {
        if (session.savedUserId.isBlank()) {
            state.update { it.copy(replyError = context.getString(R.string.error_login_required_reply)) }
            return
        }
        state.update {
            it.copy(
                replyTarget = ReplyTarget(commentId, replyToUsername),
                expandedReplies = it.expandedReplies + commentId
            )
        }
        if (!state.value.replies.containsKey(commentId)) {
            loadReplies(commentId)
        }
    }

    fun cancelReply() {
        state.update { it.copy(replyTarget = null, replyError = null) }
    }

    fun clearReplyError() {
        state.update { it.copy(replyError = null) }
    }

    /**
     * 提交回复。采用乐观更新：先 +1 回复数并插入临时回复，接口成功后用服务端返回的真实
     * 回复替换临时项；失败回滚并提示错误。
     *
     * [ctx] 由视频域提供：csrfToken 与 videoId 都在视频域，评论域不反向依赖它。
     */
    fun submitReply(text: String, ctx: CommentContext) {
        val target = state.value.replyTarget ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            state.update { it.copy(replyError = context.getString(R.string.error_reply_empty)) }
            return
        }
        if (ctx.csrfToken.isBlank()) {
            state.update { it.copy(replyError = context.getString(R.string.error_csrf_missing)) }
            return
        }

        val finalText = if (!target.replyToUsername.isNullOrEmpty() && !trimmed.startsWith("@")) {
            "@${target.replyToUsername} $trimmed"
        } else {
            trimmed
        }

        val requestVideoId = ctx.videoId
        state.update { it.copy(isPostingReply = true, replyError = null) }

        val optimisticReply = Reply(
            id = "local_${System.currentTimeMillis()}",
            username = "",
            avatarUrl = "",
            time = "刚刚",
            content = trimmed,
            likeCount = 0,
            replyTo = target.replyToUsername
        )
        state.update {
            it.copy(
                comments = CommentOptimistic.adjustReplyCount(it.comments, target.commentId, 1),
                replies = CommentOptimistic.appendReply(it.replies, target.commentId, optimisticReply),
                expandedReplies = it.expandedReplies + target.commentId
            )
        }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.replyComment(target.commentId, finalText, ctx.csrfToken)
                }
                if (!isSameVideo(requestVideoId)) {
                    AppLogger.d("CommentComposerController", "Discard stale reply result: ${target.commentId}")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        val newReply = result.data
                        state.update {
                            it.copy(
                                replies = CommentOptimistic.replaceReply(
                                    it.replies,
                                    target.commentId,
                                    optimisticReply.id,
                                    newReply
                                ),
                                replyTarget = null
                            )
                        }
                    }
                    is AppResult.Error -> {
                        rollbackOptimisticReply(target.commentId, optimisticReply.id)
                        state.update {
                            it.copy(
                                replyError = ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_post_failed)
                                )
                            )
                        }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentComposerController", "Error posting reply: ${e.message}", e)
                if (isSameVideo(requestVideoId)) {
                    rollbackOptimisticReply(target.commentId, optimisticReply.id)
                    state.update {
                        it.copy(
                            replyError = ErrorMapper.message(
                                context,
                                e,
                                context.getString(R.string.comment_post_failed)
                            )
                        )
                    }
                }
            } finally {
                if (isSameVideo(requestVideoId)) {
                    state.update { it.copy(isPostingReply = false) }
                }
            }
        }
    }

    private fun rollbackOptimisticReply(commentId: String, optimisticId: String) {
        state.update {
            it.copy(
                comments = CommentOptimistic.adjustReplyCount(it.comments, commentId, -1),
                replies = CommentOptimistic.removeReply(it.replies, commentId, optimisticId)
            )
        }
    }

    /**
     * 发表评论。
     *
     * 成功后将新评论追加到列表头部，并通过 [onCommentCountUpdated] 把服务端返回的
     * 评论总数回给视频域（用于刷新 VideoDetail.commentCount）。
     *
     * [ctx] 由视频域提供，理由同 [submitReply]。
     */
    fun postComment(text: String, ctx: CommentContext, onCommentCountUpdated: (Int) -> Unit = {}) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            state.update { it.copy(postError = context.getString(R.string.error_comment_empty)) }
            return
        }
        if (ctx.videoId.isEmpty()) {
            state.update { it.copy(postError = context.getString(R.string.error_video_info_missing)) }
            return
        }
        if (ctx.csrfToken.isBlank()) {
            state.update { it.copy(postError = context.getString(R.string.error_csrf_missing)) }
            return
        }

        val requestVideoId = ctx.videoId
        state.update { it.copy(isPosting = true, postError = null) }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.postComment(
                        videoId = requestVideoId,
                        commentText = trimmed,
                        commentCount = ctx.commentCount,
                        csrfToken = ctx.csrfToken
                    )
                }
                if (!isSameVideo(requestVideoId)) {
                    AppLogger.d("CommentComposerController", "Discard stale posted comment: $requestVideoId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        val (newComment, newCount) = result.data
                        state.update { it.copy(comments = listOf(newComment) + it.comments) }
                        onCommentCountUpdated(newCount)
                    }
                    is AppResult.Error -> {
                        state.update {
                            it.copy(
                                postError = ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_post_failed)
                                )
                            )
                        }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentComposerController", "Error posting comment: ${e.message}", e)
                if (isSameVideo(requestVideoId)) {
                    state.update {
                        it.copy(
                            postError = ErrorMapper.message(
                                context,
                                e,
                                context.getString(R.string.comment_post_failed)
                            )
                        )
                    }
                }
            } finally {
                if (isSameVideo(requestVideoId)) {
                    state.update { it.copy(isPosting = false) }
                }
            }
        }
    }

    fun clearPostCommentError() {
        state.update { it.copy(postError = null) }
    }

    /**
     * 切换评论点赞状态（点赞 / 取消点赞）。
     *
     * 采用乐观更新：先本地切换状态与计数，接口失败再回滚。
     * 需要 csrfToken 与当前 videoId，同样由 [ctx] 提供。
     */
    fun toggleCommentLike(comment: Comment, ctx: CommentContext) {
        if (ctx.csrfToken.isBlank()) {
            onEvent(CommentEvent.LikeFailed(context.getString(R.string.error_csrf_missing)))
            return
        }
        if (session.savedUserId.isBlank()) {
            onEvent(CommentEvent.LikeFailed(context.getString(R.string.error_login_required_like)))
            return
        }
        if (state.value.likingIds.contains(comment.id)) return

        val requestVideoId = ctx.videoId
        val optimistic = CommentOptimistic.toggleLike(comment)
        state.update {
            it.copy(
                comments = it.comments.map { current -> if (current.id == comment.id) optimistic else current },
                likingIds = it.likingIds + comment.id
            )
        }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.toggleCommentLike(
                        commentId = comment.id,
                        currentLikeStatus = comment.likeStatus,
                        likeCount = comment.likeCount,
                        csrfToken = ctx.csrfToken
                    )
                }
                if (!isSameVideo(requestVideoId)) {
                    AppLogger.d("CommentComposerController", "Discard stale like result: ${comment.id}")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {}
                    is AppResult.Error -> {
                        state.update {
                            it.copy(
                                comments = it.comments.map { current ->
                                    if (current.id == comment.id) comment else current
                                }
                            )
                        }
                        onEvent(
                            CommentEvent.LikeFailed(
                                ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_like_failed)
                                )
                            )
                        )
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentComposerController", "Error toggling comment like: ${e.message}", e)
                if (isSameVideo(requestVideoId)) {
                    state.update {
                        it.copy(
                            comments = it.comments.map { current ->
                                if (current.id == comment.id) comment else current
                            }
                        )
                    }
                    onEvent(
                        CommentEvent.LikeFailed(
                            ErrorMapper.message(
                                context,
                                e,
                                context.getString(R.string.comment_like_failed)
                            )
                        )
                    )
                }
            } finally {
                if (isSameVideo(requestVideoId)) {
                    state.update { it.copy(likingIds = it.likingIds - comment.id) }
                }
            }
        }
    }

    private fun isSameVideo(requestVideoId: String): Boolean = requestVideoId == currentVideoId()
}
