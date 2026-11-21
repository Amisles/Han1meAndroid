package app.amisles.hanime.feature.detail.comment

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CommentPagingController(
    private val account: AccountRepository,
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<CommentUiState>
) {
    var currentVideoId: String = ""
        private set

    private var nextCommentPage = 2
    private var loadMoreCommentsJob: Job? = null

    fun selectVideo(videoId: String) {
        if (shouldSkipCommentReset(currentVideoId, videoId)) {
            AppLogger.d("CommentPagingController", "selectVideo 收到同一视频，跳过复位: $videoId")
            return
        }
        currentVideoId = videoId
        nextCommentPage = 2
        state.value = CommentUiState()
    }

    private fun isSameVideoAsRequest(requestVideoId: String): Boolean =
        requestVideoId == currentVideoId

    fun loadComments(force: Boolean = false) {
        val current = state.value
        if (currentVideoId.isEmpty()) return
        if (current.loaded && !force) {
            AppLogger.d("CommentPagingController", "Comments already loaded, skip")
            return
        }
        if (current.isLoading) {
            AppLogger.d("CommentPagingController", "Comments already loading, skip")
            return
        }
        val requestVideoId = currentVideoId
        loadMoreCommentsJob?.cancel()
        nextCommentPage = 2
        state.update {
            it.copy(isLoading = true, error = null, isLoadingMore = false, hasMore = false)
        }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getComments(requestVideoId)
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    AppLogger.d("CommentPagingController", "Discard stale comments response: $requestVideoId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        state.update {
                            it.copy(
                                comments = result.data,
                                loaded = true,
                                hasMore = result.data.size >= COMMENTS_PAGE_SIZE
                            )
                        }
                    }
                    is AppResult.Error -> {
                        state.update {
                            it.copy(
                                error = ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_load_failed)
                                )
                            )
                        }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentPagingController", "Error loading comments: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    state.update {
                        it.copy(
                            error = ErrorMapper.message(
                                context,
                                e,
                                context.getString(R.string.comment_load_failed)
                            )
                        )
                    }
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) {
                    state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /**
     * 追加下一页评论；最后一页（返回数不足页大小）时置 hasMore = false。
     * 站点 /loadComment 对越界页返回与上一页相同的数据，这里以「页数无增量」同样收口，
     * 避免越界后无限重复请求。
     */
    fun loadMoreComments() {
        val current = state.value
        if (currentVideoId.isEmpty()) return
        if (current.isLoading || current.isLoadingMore) return
        if (!current.hasMore) return
        val requestVideoId = currentVideoId
        val page = nextCommentPage
        state.update { it.copy(isLoadingMore = true, error = null) }
        loadMoreCommentsJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getComments(requestVideoId, page)
                }
                if (!isSameVideoAsRequest(requestVideoId)) return@launch
                when (result) {
                    is AppResult.Success -> {
                        val existing = state.value.comments
                        val merged = (existing + result.data).distinctBy { it.id }
                        if (merged.size == existing.size || result.data.size < COMMENTS_PAGE_SIZE) {
                            state.update { it.copy(hasMore = false) }
                        } else {
                            state.update { it.copy(comments = merged) }
                            nextCommentPage = page + 1
                        }
                    }
                    is AppResult.Error -> {
                        state.update {
                            it.copy(
                                error = ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_load_failed)
                                )
                            )
                        }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentPagingController", "loadMoreComments failed: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    state.update {
                        it.copy(
                            error = ErrorMapper.message(
                                context,
                                e,
                                context.getString(R.string.comment_load_failed)
                            )
                        )
                    }
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) {
                    state.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    /** 加载某条评论的回复列表；已缓存则不重复请求，除非 force=true。 */
    fun loadReplies(commentId: String, force: Boolean = false) {
        if (commentId.isEmpty()) return
        val requestVideoId = currentVideoId
        val current = state.value
        if (!force && current.replies.containsKey(commentId)) return
        if (current.loadingReplies.contains(commentId)) return

        state.update {
            it.copy(loadingReplies = it.loadingReplies + commentId, replyErrors = it.replyErrors - commentId)
        }

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getReplies(commentId)
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    AppLogger.d("CommentPagingController", "Discard stale replies response: $commentId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        state.update { it.copy(replies = it.replies + (commentId to result.data)) }
                    }
                    is AppResult.Error -> {
                        val message = ErrorMapper.message(
                            context,
                            result.exception,
                            result.code,
                            context.getString(R.string.comment_reply_load_failed)
                        )
                        state.update { it.copy(replyErrors = it.replyErrors + (commentId to message)) }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentPagingController", "Error loading replies: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    val message = ErrorMapper.message(
                        context,
                        e,
                        context.getString(R.string.comment_reply_load_failed)
                    )
                    state.update { it.copy(replyErrors = it.replyErrors + (commentId to message)) }
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) {
                    state.update { it.copy(loadingReplies = it.loadingReplies - commentId) }
                }
            }
        }
    }

    /** 切换某条评论的回复区展开/收起状态；首次展开且尚未加载时拉取回复列表。 */
    fun toggleReplies(commentId: String) {
        val expanded = state.value.expandedReplies
        state.update {
            it.copy(
                expandedReplies = if (expanded.contains(commentId)) expanded - commentId else expanded + commentId
            )
        }
        if (!state.value.replies.containsKey(commentId)) {
            loadReplies(commentId)
        }
    }

    companion object {
        /** 站点 /loadComment 单页返回的评论数。 */
        const val COMMENTS_PAGE_SIZE = 20
    }
}
