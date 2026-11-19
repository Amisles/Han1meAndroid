package app.amisles.hanime.feature.detail.comment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 视频域传给评论域的调用上下文。
 *
 * 发表评论/回复需要 csrfToken（来自视频详情页）与当前 videoId。
 *
 * 二者由视频域在调用时传入，而不是让评论域反向依赖视频域 —— 这样评论域可以单独测试，
 * 也不需要为了传递两个字段而引入一个"视频域单例"。
 */
data class CommentContext(
    val videoId: String,
    val csrfToken: String,
    /** 当前评论数（发表成功后用于回写 VideoDetail.commentCount）。 */
    val commentCount: Int,
)

/**
 * 评论域 ViewModel：评论列表、回复、点赞、乐观更新与回滚。
 *
 * ## 为什么从 DetailViewModel 拆出来
 *
 * 原 DetailViewModel 有 33 个 StateFlow，其中 18 个属于评论域 —— 它同时管着播放器
 * 生命周期、收藏订阅、下载发起和评论互动，任一处的 bug 都要在大类里排查。
 *
 * 拆分依据是"变化的理由"：评论域随站点评论接口而变，视频域随播放/详情接口而变，
 * 二者改动频率与测试关注点完全不同。
 *
 * ## 状态契约
 *
 * 对外只有 [uiState] 与 [events]：列表、加载、错误、回复、点赞都在同一个
 * [CommentUiState] 里原子更新，界面只订阅一次；[CommentEvent] 承载「展示完即丢弃」
 * 的一次性提示（目前只有点赞失败），不再留在状态里等界面清空。
 */
@HiltViewModel
class CommentViewModel @Inject constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommentEvent>(Channel.BUFFERED)
    val events: Flow<CommentEvent> = _events.receiveAsFlow()

    /** 当前评论所属的视频 ID；由 [selectVideo] 设置，用于丢弃过期响应。 */
    private var currentVideoId: String = ""

    private var nextCommentPage = 2

    private var loadMoreCommentsJob: Job? = null

    /**
     * 切换到某个视频（离开详情页时可传空串）。
     *
     * 由视频域在加载新视频前调用：清空全部评论状态。
     *
     * **幂等**：视频域用 StateFlow 承载当前 videoId，UI 重新订阅或配置变更后重放同一个值
     * 时会再次调用本方法。若不短路，一次无意义的复位会把已经加载好的评论与展开态清空，
     * 表现为「旋转屏幕后评论区变空」。
     */
    fun selectVideo(videoId: String) {
        if (shouldSkipCommentReset(currentVideoId, videoId)) {
            AppLogger.d("CommentViewModel", "selectVideo 收到同一视频，跳过复位: $videoId")
            return
        }
        currentVideoId = videoId
        nextCommentPage = 2
        _uiState.value = CommentUiState()
    }

    /** 异步响应是否仍属于当前视频；切页后必须丢弃，否则旧视频的评论会写进新视频。 */
    private fun isSameVideoAsRequest(requestVideoId: String): Boolean =
        requestVideoId == currentVideoId

    /** 加载评论列表；已加载或加载中都不重复请求，[force] 只绕过前者。 */
    fun loadComments(force: Boolean = false) {
        val state = _uiState.value
        if (currentVideoId.isEmpty()) return
        if (state.loaded && !force) {
            AppLogger.d("CommentViewModel", "Comments already loaded, skip")
            return
        }
        if (state.isLoading) {
            AppLogger.d("CommentViewModel", "Comments already loading, skip")
            return
        }
        val requestVideoId = currentVideoId
        AppLogger.d("CommentViewModel", "loadComments called, videoId: $requestVideoId")
        loadMoreCommentsJob?.cancel()
        nextCommentPage = 2
        _uiState.update {
            it.copy(isLoading = true, error = null, isLoadingMore = false, hasMore = false)
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getComments(requestVideoId)
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    // 已切到其他视频：丢弃结果，避免评论串到别的视频
                    AppLogger.d("CommentViewModel", "Discard stale comments response: $requestVideoId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        AppLogger.d("CommentViewModel", "Got ${result.data.size} comments")
                        _uiState.update {
                            it.copy(
                                comments = result.data,
                                loaded = true,
                                hasMore = result.data.size >= COMMENTS_PAGE_SIZE
                            )
                        }
                    }
                    is AppResult.Error -> {
                        AppLogger.e("CommentViewModel", "Error loading comments: ${result.message}", result.exception)
                        _uiState.update {
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
                AppLogger.e("CommentViewModel", "Error loading comments: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update {
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
                    _uiState.update { it.copy(isLoading = false) }
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
        val state = _uiState.value
        if (currentVideoId.isEmpty()) return
        if (state.isLoading || state.isLoadingMore) return
        if (!state.hasMore) return
        val requestVideoId = currentVideoId
        val page = nextCommentPage
        AppLogger.d("CommentViewModel", "loadMoreComments page=$page videoId=$requestVideoId")
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        loadMoreCommentsJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getComments(requestVideoId, page)
                }
                if (!isSameVideoAsRequest(requestVideoId)) return@launch
                when (result) {
                    is AppResult.Success -> {
                        val existing = _uiState.value.comments
                        val merged = (existing + result.data).distinctBy { it.id }
                        if (merged.size == existing.size || result.data.size < COMMENTS_PAGE_SIZE) {
                            _uiState.update { it.copy(hasMore = false) }
                        } else {
                            _uiState.update { it.copy(comments = merged) }
                            nextCommentPage = page + 1
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update {
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
                AppLogger.e("CommentViewModel", "loadMoreComments failed: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update {
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
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    /** 加载某条评论的回复列表；已缓存则不重复请求，除非 force=true。 */
    fun loadReplies(commentId: String, force: Boolean = false) {
        if (commentId.isEmpty()) return
        val requestVideoId = currentVideoId
        val state = _uiState.value
        if (!force && state.replies.containsKey(commentId)) {
            AppLogger.d("CommentViewModel", "Replies for $commentId already cached, skip")
            return
        }
        if (state.loadingReplies.contains(commentId)) {
            AppLogger.d("CommentViewModel", "Replies for $commentId is loading, skip")
            return
        }
        AppLogger.d("CommentViewModel", "loadReplies called, commentId: $commentId")
        _uiState.update {
            it.copy(loadingReplies = it.loadingReplies + commentId, replyErrors = it.replyErrors - commentId)
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getReplies(commentId)
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    // 已切到其他视频：丢弃结果，避免旧视频的回复写进缓存
                    AppLogger.d("CommentViewModel", "Discard stale replies response: $commentId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        AppLogger.d("CommentViewModel", "Got ${result.data.size} replies for comment $commentId")
                        _uiState.update { it.copy(replies = it.replies + (commentId to result.data)) }
                    }
                    is AppResult.Error -> {
                        AppLogger.e("CommentViewModel", "Error loading replies: ${result.message}", result.exception)
                        val replyErrorMessage = ErrorMapper.message(
                            context,
                            result.exception,
                            result.code,
                            context.getString(R.string.comment_reply_load_failed)
                        )
                        _uiState.update { it.copy(replyErrors = it.replyErrors + (commentId to replyErrorMessage)) }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentViewModel", "Error loading replies: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    val message = ErrorMapper.message(
                        context,
                        e,
                        context.getString(R.string.comment_reply_load_failed)
                    )
                    _uiState.update { it.copy(replyErrors = it.replyErrors + (commentId to message)) }
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update { it.copy(loadingReplies = it.loadingReplies - commentId) }
                }
            }
        }
    }

    /**
     * 打开回复输入框；replyToUsername 非空表示回复某条回复（提交内容带 "@用户名 " 前缀）。
     *
     * 同时自动展开该评论的回复区，并在尚未加载时拉取回复列表。
     */
    fun startReply(commentId: String, replyToUsername: String? = null) {
        if (session.savedUserId.isBlank()) {
            _uiState.update { it.copy(replyError = context.getString(R.string.error_login_required_reply)) }
            return
        }
        _uiState.update {
            it.copy(
                replyTarget = ReplyTarget(commentId, replyToUsername),
                expandedReplies = it.expandedReplies + commentId
            )
        }
        if (!_uiState.value.replies.containsKey(commentId)) {
            loadReplies(commentId)
        }
    }

    /** 关闭回复输入框。 */
    fun cancelReply() {
        _uiState.update { it.copy(replyTarget = null, replyError = null) }
    }

    /** 切换某条评论的回复区展开/收起状态；首次展开且尚未加载时拉取回复列表。 */
    fun toggleReplies(commentId: String) {
        val expanded = _uiState.value.expandedReplies
        _uiState.update {
            it.copy(
                expandedReplies = if (expanded.contains(commentId)) expanded - commentId else expanded + commentId
            )
        }
        if (!_uiState.value.replies.containsKey(commentId)) {
            loadReplies(commentId)
        }
    }

    /**
     * 提交回复。采用乐观更新：先 +1 回复数并插入临时回复，接口成功后用服务端返回的真实
     * 回复替换临时项；失败回滚并提示错误。
     *
     * [ctx] 由视频域提供：csrfToken 与 videoId 都在视频域，评论域不反向依赖它。
     */
    fun submitReply(text: String, ctx: CommentContext) {
        val target = _uiState.value.replyTarget ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(replyError = context.getString(R.string.error_reply_empty)) }
            return
        }
        if (ctx.csrfToken.isBlank()) {
            _uiState.update { it.copy(replyError = context.getString(R.string.error_csrf_missing)) }
            return
        }

        // 回复其他回复时，内容前补 "@用户名 "，避免与解析/展示重复
        val finalText = if (!target.replyToUsername.isNullOrEmpty() && !trimmed.startsWith("@")) {
            "@${target.replyToUsername} $trimmed"
        } else {
            trimmed
        }

        // 结果落地前校验 videoId：切页后不能把回复写进新视频的回复列表
        val requestVideoId = ctx.videoId
        _uiState.update { it.copy(isPostingReply = true, replyError = null) }

        // 构造临时回复（用户名/头像待服务端返回）
        val optimisticReply = Reply(
            id = "local_${System.currentTimeMillis()}",
            username = "",
            avatarUrl = "",
            time = "刚刚",
            content = trimmed,
            likeCount = 0,
            replyTo = target.replyToUsername
        )
        _uiState.update {
            it.copy(
                comments = CommentOptimistic.adjustReplyCount(it.comments, target.commentId, 1),
                replies = CommentOptimistic.appendReply(it.replies, target.commentId, optimisticReply),
                expandedReplies = it.expandedReplies + target.commentId
            )
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.replyComment(target.commentId, finalText, ctx.csrfToken)
                }
                // 已切到其他视频：不回写也不回滚（状态已由 selectVideo 整体重置）。
                // 不取消 POST —— 服务端可能已创建回复。
                if (!isSameVideoAsRequest(requestVideoId)) {
                    AppLogger.d("CommentViewModel", "Discard stale reply result: ${target.commentId}")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        val newReply = result.data
                        // 用服务端返回的回复替换临时项：先剔除临时项（防 loadReplies 竞态覆盖），再追加
                        _uiState.update {
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
                        AppLogger.d("CommentViewModel", "Reply posted for comment ${target.commentId}, replyId=${newReply.id}")
                    }
                    is AppResult.Error -> {
                        rollbackOptimisticReply(target.commentId, optimisticReply.id)
                        _uiState.update {
                            it.copy(
                                replyError = ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_post_failed)
                                )
                            )
                        }
                        AppLogger.e("CommentViewModel", "Error posting reply: ${result.message}", result.exception)
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 异常路径同样要回滚乐观更新，否则界面会残留一条永不落地的临时回复
                AppLogger.e("CommentViewModel", "Error posting reply: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    rollbackOptimisticReply(target.commentId, optimisticReply.id)
                    _uiState.update {
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
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update { it.copy(isPostingReply = false) }
                }
            }
        }
    }

    /**
     * 精确回滚一次乐观回复：只移除该临时回复并把该评论的回复数 -1，不再整表恢复提交前快照。
     *
     * 原实现用提交前的 prevComments/prevReplies 整体覆盖，会把请求期间的并发修改一并抹掉 ——
     * 例如同一条评论的回复列表在此期间被重新拉取、或其它评论的点赞已乐观写入。
     */
    private fun rollbackOptimisticReply(commentId: String, optimisticId: String) {
        _uiState.update {
            it.copy(
                comments = CommentOptimistic.adjustReplyCount(it.comments, commentId, -1),
                replies = CommentOptimistic.removeReply(it.replies, commentId, optimisticId)
            )
        }
    }

    /** 清除回复提交的错误状态。 */
    fun clearReplyError() {
        _uiState.update { it.copy(replyError = null) }
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
            _uiState.update { it.copy(postError = context.getString(R.string.error_comment_empty)) }
            return
        }
        if (ctx.videoId.isEmpty()) {
            _uiState.update { it.copy(postError = context.getString(R.string.error_video_info_missing)) }
            return
        }
        if (ctx.csrfToken.isBlank()) {
            _uiState.update { it.copy(postError = context.getString(R.string.error_csrf_missing)) }
            return
        }

        // 绑定发起时的视频：既用于请求，也用于结果落地前的校验
        val requestVideoId = ctx.videoId
        _uiState.update { it.copy(isPosting = true, postError = null) }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.postComment(
                        videoId = requestVideoId,
                        commentText = trimmed,
                        commentCount = ctx.commentCount,
                        csrfToken = ctx.csrfToken
                    )
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    // 已切到其他视频：丢弃 UI 更新（评论已在服务端创建）
                    AppLogger.d("CommentViewModel", "Discard stale posted comment: $requestVideoId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        val (newComment, newCount) = result.data
                        // 新评论追加到列表头部
                        _uiState.update { it.copy(comments = listOf(newComment) + it.comments) }
                        onCommentCountUpdated(newCount)
                        AppLogger.d("CommentViewModel", "Comment posted: id=${newComment.id}, newCount=$newCount")
                    }
                    is AppResult.Error -> {
                        AppLogger.e("CommentViewModel", "Error posting comment: ${result.message}", result.exception)
                        _uiState.update {
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
                AppLogger.e("CommentViewModel", "Error posting comment: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update {
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
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update { it.copy(isPosting = false) }
                }
            }
        }
    }

    /** 清除发表评论的错误状态。 */
    fun clearPostCommentError() {
        _uiState.update { it.copy(postError = null) }
    }

    /**
     * 切换评论点赞状态（点赞 / 取消点赞）。
     *
     * 采用乐观更新：先本地切换状态与计数，接口失败再回滚。
     * 需要 csrfToken 与当前 videoId，同样由 [ctx] 提供。
     */
    fun toggleCommentLike(comment: Comment, ctx: CommentContext) {
        if (ctx.csrfToken.isBlank()) {
            _events.trySend(CommentEvent.LikeFailed(context.getString(R.string.error_csrf_missing)))
            return
        }
        if (session.savedUserId.isBlank()) {
            _events.trySend(CommentEvent.LikeFailed(context.getString(R.string.error_login_required_like)))
            return
        }
        // 防止同一评论在请求未完成前被重复点击
        if (_uiState.value.likingIds.contains(comment.id)) return

        // 回滚按 comment.id 在新列表里定位，切页后必须放弃，否则会写坏新视频的评论
        val requestVideoId = ctx.videoId
        val optimistic = CommentOptimistic.toggleLike(comment)
        _uiState.update {
            it.copy(
                comments = it.comments.map { current -> if (current.id == comment.id) optimistic else current },
                likingIds = it.likingIds + comment.id
            )
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.toggleCommentLike(
                        commentId = comment.id,
                        currentLikeStatus = comment.likeStatus,
                        likeCount = comment.likeCount,
                        csrfToken = ctx.csrfToken
                    )
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    // 已切到其他视频：不回滚也不提示，评论列表已由 selectVideo 整体重置
                    AppLogger.d("CommentViewModel", "Discard stale like result: ${comment.id}")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        AppLogger.d("CommentViewModel", "Comment like toggled for ${comment.id}, willLike=${optimistic.likeStatus == 1}")
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                comments = it.comments.map { current ->
                                    if (current.id == comment.id) comment else current
                                }
                            )
                        }
                        _events.trySend(
                            CommentEvent.LikeFailed(
                                ErrorMapper.message(
                                    context,
                                    result.exception,
                                    result.code,
                                    context.getString(R.string.comment_like_failed)
                                )
                            )
                        )
                        AppLogger.e("CommentViewModel", "Error toggling comment like: ${result.message}", result.exception)
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 异常路径同样回滚乐观更新，避免界面停留在与服务端不一致的点赞态
                AppLogger.e("CommentViewModel", "Error toggling comment like: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update {
                        it.copy(
                            comments = it.comments.map { current ->
                                if (current.id == comment.id) comment else current
                            }
                        )
                    }
                    _events.trySend(
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
                if (isSameVideoAsRequest(requestVideoId)) {
                    _uiState.update { it.copy(likingIds = it.likingIds - comment.id) }
                }
            }
        }
    }

    companion object {
        /** 站点 /loadComment 单页返回的评论数。 */
        const val COMMENTS_PAGE_SIZE = 20
    }
}

/**
 * 评论回复的目标。
 * @param commentId 被回复的评论 ID（replyComment 接口只认父评论 ID）
 * @param replyToUsername 非空表示回复某条回复，提交内容会带 "@用户名 " 前缀
 */
data class ReplyTarget(
    val commentId: String,
    val replyToUsername: String? = null
)
