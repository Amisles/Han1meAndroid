package app.amisles.hanime.feature.detail.comment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
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
 */
@HiltViewModel
class CommentViewModel @Inject constructor(
    private val account: AccountRepository,
    private val session: SessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

    /** 评论是否已加载完成。用于区分「未加载」与「已加载但为空」。 */
    private val _commentsLoaded = MutableStateFlow(false)
    val commentsLoaded: StateFlow<Boolean> = _commentsLoaded.asStateFlow()

    private val _isPostingComment = MutableStateFlow(false)
    val isPostingComment: StateFlow<Boolean> = _isPostingComment.asStateFlow()

    private val _postCommentError = MutableStateFlow<String?>(null)
    val postCommentError: StateFlow<String?> = _postCommentError.asStateFlow()

    // 最近发表的评论（用于追加到列表头部）
    private val _lastPostedComment = MutableStateFlow<Comment?>(null)
    val lastPostedComment: StateFlow<Comment?> = _lastPostedComment.asStateFlow()

    // 正在切换点赞状态的评论 ID 集合（防止重复点击）
    private val _likingComments = MutableStateFlow<Set<String>>(emptySet())
    val likingComments: StateFlow<Set<String>> = _likingComments.asStateFlow()

    private val _commentLikeError = MutableStateFlow<String?>(null)
    val commentLikeError: StateFlow<String?> = _commentLikeError.asStateFlow()

    // 回复缓存：commentId -> 回复列表
    private val _repliesCache = MutableStateFlow<Map<String, List<Reply>>>(emptyMap())
    val repliesCache: StateFlow<Map<String, List<Reply>>> = _repliesCache.asStateFlow()

    // 正在加载回复的评论 ID 集合
    private val _loadingReplies = MutableStateFlow<Set<String>>(emptySet())
    val loadingReplies: StateFlow<Set<String>> = _loadingReplies.asStateFlow()

    // 回复加载错误：commentId -> 错误信息
    private val _repliesError = MutableStateFlow<Map<String, String?>>(emptyMap())
    val repliesError: StateFlow<Map<String, String?>> = _repliesError.asStateFlow()

    // 当前正在回复的目标：commentId 为父评论 ID；replyToUsername 非空表示回复某条回复
    private val _activeReplyTarget = MutableStateFlow<ReplyTarget?>(null)
    val activeReplyTarget: StateFlow<ReplyTarget?> = _activeReplyTarget.asStateFlow()

    private val _isPostingReply = MutableStateFlow(false)
    val isPostingReply: StateFlow<Boolean> = _isPostingReply.asStateFlow()

    private val _replyError = MutableStateFlow<String?>(null)
    val replyError: StateFlow<String?> = _replyError.asStateFlow()

    // 已展开回复区的评论 ID 集合
    private val _expandedReplies = MutableStateFlow<Set<String>>(emptySet())
    val expandedReplies: StateFlow<Set<String>> = _expandedReplies.asStateFlow()

    /** 当前评论所属的视频 ID；由 [selectVideo] 设置，用于丢弃过期响应。 */
    private var currentVideoId: String = ""

    /**
     * 切换到某个视频（离开详情页时可传空串）。
     *
     * 由视频域在加载新视频前调用：清空全部评论状态。
     *
     * 原先这些复位散在 loadVideoDetail 里逐个赋值，一次要写 14 行，漏一个就会串数据；
     * 收拢到本方法后视频域只需一行调用。
     *
     * **幂等**：视频域用 StateFlow 承载当前 videoId，UI 重新订阅或配置变更后重放同一个值
     * 时会再次调用本方法。若不短路，一次无意义的复位会把已经加载好的评论与展开态清空，
     * 表现为「旋转屏幕后评论区变空」。
     */
    fun selectVideo(videoId: String) {
        if (videoId == currentVideoId && videoId.isNotEmpty()) {
            AppLogger.d("CommentViewModel", "selectVideo 收到同一视频，跳过复位: $videoId")
            return
        }
        currentVideoId = videoId
        _comments.value = emptyList()
        _commentsLoaded.value = false
        _commentsError.value = null
        _isLoadingComments.value = false
        _isPostingComment.value = false
        _postCommentError.value = null
        _lastPostedComment.value = null
        _likingComments.value = emptySet()
        _commentLikeError.value = null
        _repliesCache.value = emptyMap()
        _loadingReplies.value = emptySet()
        _repliesError.value = emptyMap()
        _activeReplyTarget.value = null
        _isPostingReply.value = false
        _replyError.value = null
        _expandedReplies.value = emptySet()
    }

    /** 异步响应是否仍属于当前视频；切页后必须丢弃，否则旧视频的评论会写进新视频。 */
    private fun isSameVideoAsRequest(requestVideoId: String): Boolean =
        requestVideoId == currentVideoId

    private val _isLoadingMoreComments = MutableStateFlow(false)
    val isLoadingMoreComments: StateFlow<Boolean> = _isLoadingMoreComments.asStateFlow()

    private val _hasMoreComments = MutableStateFlow(false)
    val hasMoreComments: StateFlow<Boolean> = _hasMoreComments.asStateFlow()

    private var nextCommentPage = 2

    private var loadMoreCommentsJob: Job? = null

    /** 加载评论列表；已加载过则不重复请求，除非 force=true。 */
    fun loadComments(force: Boolean = false) {
        if (currentVideoId.isEmpty()) return
        if (_commentsLoaded.value && !force) {
            AppLogger.d("CommentViewModel", "Comments already loaded, skip")
            return
        }
        val requestVideoId = currentVideoId
        AppLogger.d("CommentViewModel", "loadComments called, videoId: $requestVideoId")
        _isLoadingComments.value = true
        _commentsError.value = null
        loadMoreCommentsJob?.cancel()
        _isLoadingMoreComments.value = false
        _hasMoreComments.value = false
        nextCommentPage = 2

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
                        _comments.value = result.data
                        _commentsLoaded.value = true
                        _hasMoreComments.value = result.data.size >= COMMENTS_PAGE_SIZE
                    }
                    is AppResult.Error -> {
                        AppLogger.e("CommentViewModel", "Error loading comments: ${result.message}", result.exception)
                        _commentsError.value = result.message
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentViewModel", "Error loading comments: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) _commentsError.value = e.message
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) _isLoadingComments.value = false
            }
        }
    }

    /**
     * 追加下一页评论；最后一页（返回数不足页大小）时置 [hasMoreComments] = false。
     * 站点 /loadComment 对越界页返回与上一页相同的数据，这里以「页数无增量」同样收口，
     * 避免越界后无限重复请求。
     */
    fun loadMoreComments() {
        if (currentVideoId.isEmpty()) return
        if (_isLoadingComments.value || _isLoadingMoreComments.value) return
        if (!_hasMoreComments.value) return
        val requestVideoId = currentVideoId
        val page = nextCommentPage
        AppLogger.d("CommentViewModel", "loadMoreComments page=$page videoId=$requestVideoId")
        _isLoadingMoreComments.value = true
        _commentsError.value = null
        loadMoreCommentsJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.getComments(requestVideoId, page)
                }
                if (!isSameVideoAsRequest(requestVideoId)) return@launch
                when (result) {
                    is AppResult.Success -> {
                        val existing = _comments.value
                        val merged = (existing + result.data).distinctBy { it.id }
                        if (merged.size == existing.size || result.data.size < COMMENTS_PAGE_SIZE) {
                            _hasMoreComments.value = false
                        } else {
                            _comments.value = merged
                            nextCommentPage = page + 1
                        }
                    }
                    is AppResult.Error -> {
                        _commentsError.value = result.message
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentViewModel", "loadMoreComments failed: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _commentsError.value = e.message
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) _isLoadingMoreComments.value = false
            }
        }
    }

    /**
     * 加载某条评论的回复列表；已缓存则不重复请求，除非 force=true。
     */
    fun loadReplies(commentId: String, force: Boolean = false) {
        if (commentId.isEmpty()) return
        val requestVideoId = currentVideoId
        if (!force && _repliesCache.value.containsKey(commentId)) {
            AppLogger.d("CommentViewModel", "Replies for $commentId already cached, skip")
            return
        }
        if (_loadingReplies.value.contains(commentId)) {
            AppLogger.d("CommentViewModel", "Replies for $commentId is loading, skip")
            return
        }
        AppLogger.d("CommentViewModel", "loadReplies called, commentId: $commentId")
        _loadingReplies.value = _loadingReplies.value + commentId
        _repliesError.value = _repliesError.value - commentId

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
                        _repliesCache.value = _repliesCache.value + (commentId to result.data)
                    }
                    is AppResult.Error -> {
                        AppLogger.e("CommentViewModel", "Error loading replies: ${result.message}", result.exception)
                        _repliesError.value = _repliesError.value + (commentId to result.message)
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentViewModel", "Error loading replies: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _repliesError.value = _repliesError.value + (commentId to e.message)
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) {
                    _loadingReplies.value = _loadingReplies.value - commentId
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
            _replyError.value = context.getString(R.string.error_login_required_reply)
            return
        }
        _activeReplyTarget.value = ReplyTarget(commentId, replyToUsername)
        _expandedReplies.value = _expandedReplies.value + commentId
        if (!_repliesCache.value.containsKey(commentId)) {
            loadReplies(commentId)
        }
    }

    /** 关闭回复输入框。 */
    fun cancelReply() {
        _activeReplyTarget.value = null
        _replyError.value = null
    }

    /** 切换某条评论的回复区展开/收起状态；首次展开且尚未加载时拉取回复列表。 */
    fun toggleReplies(commentId: String) {
        val expanded = _expandedReplies.value
        _expandedReplies.value = if (expanded.contains(commentId)) {
            expanded - commentId
        } else {
            expanded + commentId
        }
        if (!_repliesCache.value.containsKey(commentId)) {
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
        val target = _activeReplyTarget.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _replyError.value = context.getString(R.string.error_reply_empty)
            return
        }
        if (ctx.csrfToken.isBlank()) {
            _replyError.value = context.getString(R.string.error_csrf_missing)
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
        _isPostingReply.value = true
        _replyError.value = null

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
        // +1 回复数
        _comments.value = _comments.value.map { c ->
            if (c.id == target.commentId) c.copy(replyCount = c.replyCount + 1) else c
        }
        // 追加临时回复
        _repliesCache.value = _repliesCache.value.toMutableMap().apply {
            val list = this[target.commentId] ?: emptyList()
            this[target.commentId] = list + optimisticReply
        }
        _expandedReplies.value = _expandedReplies.value + target.commentId

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
                        _repliesCache.value = _repliesCache.value.toMutableMap().apply {
                            val list = this[target.commentId] ?: emptyList()
                            this[target.commentId] = list.filter { it.id != optimisticReply.id } + newReply
                        }
                        _activeReplyTarget.value = null
                        AppLogger.d("CommentViewModel", "Reply posted for comment ${target.commentId}, replyId=${newReply.id}")
                    }
                    is AppResult.Error -> {
                        rollbackOptimisticReply(target.commentId, optimisticReply.id)
                        _replyError.value = result.message
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
                    _replyError.value = e.message
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) _isPostingReply.value = false
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
        _comments.value = _comments.value.map { c ->
            if (c.id == commentId) c.copy(replyCount = (c.replyCount - 1).coerceAtLeast(0)) else c
        }
        _repliesCache.value = _repliesCache.value.toMutableMap().apply {
            val list = this[commentId] ?: return@apply
            this[commentId] = list.filter { it.id != optimisticId }
        }
    }

    /** 清除回复提交的错误状态。 */
    fun clearReplyError() {
        _replyError.value = null
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
            _postCommentError.value = context.getString(R.string.error_comment_empty)
            return
        }
        if (ctx.videoId.isEmpty()) {
            _postCommentError.value = context.getString(R.string.error_video_info_missing)
            return
        }
        if (ctx.csrfToken.isBlank()) {
            _postCommentError.value = context.getString(R.string.error_csrf_missing)
            return
        }

        // 绑定发起时的视频：既用于请求，也用于结果落地前的校验
        val requestVideoId = ctx.videoId
        _isPostingComment.value = true
        _postCommentError.value = null

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
                        _comments.value = listOf(newComment) + _comments.value
                        _lastPostedComment.value = newComment
                        onCommentCountUpdated(newCount)
                        AppLogger.d("CommentViewModel", "Comment posted: id=${newComment.id}, newCount=$newCount")
                    }
                    is AppResult.Error -> {
                        AppLogger.e("CommentViewModel", "Error posting comment: ${result.message}", result.exception)
                        _postCommentError.value = result.message
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("CommentViewModel", "Error posting comment: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) _postCommentError.value = e.message
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) _isPostingComment.value = false
            }
        }
    }

    /** 清除发表评论的错误状态。 */
    fun clearPostCommentError() {
        _postCommentError.value = null
    }

    /**
     * 切换评论点赞状态（点赞 / 取消点赞）。
     *
     * 采用乐观更新：先本地切换状态与计数，接口失败再回滚。
     * 需要 csrfToken 与当前 videoId，同样由 [ctx] 提供。
     */
    fun toggleCommentLike(comment: Comment, ctx: CommentContext) {
        if (ctx.csrfToken.isBlank()) {
            _commentLikeError.value = context.getString(R.string.error_csrf_missing)
            return
        }
        if (session.savedUserId.isBlank()) {
            _commentLikeError.value = context.getString(R.string.error_login_required_like)
            return
        }
        // 防止同一评论在请求未完成前被重复点击
        if (_likingComments.value.contains(comment.id)) return

        // 回滚按 comment.id 在新列表里定位，切页后必须放弃，否则会写坏新视频的评论
        val requestVideoId = ctx.videoId
        val willLike = comment.likeStatus != 1
        val optimistic = comment.copy(
            likeStatus = if (willLike) 1 else 0,
            likeCount = (comment.likeCount + if (willLike) 1 else -1).coerceAtLeast(0)
        )
        _comments.value = _comments.value.map { if (it.id == comment.id) optimistic else it }
        _likingComments.value = _likingComments.value + comment.id

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
                        AppLogger.d("CommentViewModel", "Comment like toggled for ${comment.id}, willLike=$willLike")
                    }
                    is AppResult.Error -> {
                        _comments.value = _comments.value.map { if (it.id == comment.id) comment else it }
                        _commentLikeError.value = result.message
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
                    _comments.value = _comments.value.map { if (it.id == comment.id) comment else it }
                    _commentLikeError.value = e.message
                }
            } finally {
                if (isSameVideoAsRequest(requestVideoId)) {
                    _likingComments.value = _likingComments.value - comment.id
                }
            }
        }
    }

    /** 清除评论点赞的错误状态。 */
    fun clearCommentLikeError() {
        _commentLikeError.value = null
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
