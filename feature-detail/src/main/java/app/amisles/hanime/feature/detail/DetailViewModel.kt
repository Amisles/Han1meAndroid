package app.amisles.hanime.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: HanimeRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _downloadQualities = MutableStateFlow<List<DownloadQuality>>(emptyList())
    val downloadQualities: StateFlow<List<DownloadQuality>> = _downloadQualities.asStateFlow()

    private val _isLoadingQualities = MutableStateFlow(false)
    val isLoadingQualities: StateFlow<Boolean> = _isLoadingQualities.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // 订阅作者相关状态
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _isSubscribing = MutableStateFlow(false)
    val isSubscribing: StateFlow<Boolean> = _isSubscribing.asStateFlow()

    // 订阅错误（未登录 / CSRF 缺失 / 作者 ID 缺失 / 网络失败等），以枚举区分便于 UI 本地化
    private val _subscribeError = MutableStateFlow<SubscribeError?>(null)
    val subscribeError: StateFlow<SubscribeError?> = _subscribeError.asStateFlow()

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _commentsError = MutableStateFlow<String?>(null)
    val commentsError: StateFlow<String?> = _commentsError.asStateFlow()

    private val _commentsLoaded = MutableStateFlow(false)
    val commentsLoaded: StateFlow<Boolean> = _commentsLoaded.asStateFlow()

    // 发表评论相关状态
    private val _isPostingComment = MutableStateFlow(false)
    val isPostingComment: StateFlow<Boolean> = _isPostingComment.asStateFlow()

    private val _postCommentError = MutableStateFlow<String?>(null)
    val postCommentError: StateFlow<String?> = _postCommentError.asStateFlow()

    // 最近发表的评论（用于 UI 追加到列表头部）
    private val _lastPostedComment = MutableStateFlow<Comment?>(null)
    val lastPostedComment: StateFlow<Comment?> = _lastPostedComment.asStateFlow()

    // 正在切换点赞状态的评论 ID 集合（防止重复点击）
    private val _likingComments = MutableStateFlow<Set<String>>(emptySet())
    val likingComments: StateFlow<Set<String>> = _likingComments.asStateFlow()

    // 评论点赞错误（未登录 / CSRF 缺失 / 网络失败等）
    private val _commentLikeError = MutableStateFlow<String?>(null)
    val commentLikeError: StateFlow<String?> = _commentLikeError.asStateFlow()

    // 回复缓存：commentId → 回复列表
    private val _repliesCache = MutableStateFlow<Map<String, List<Reply>>>(emptyMap())
    val repliesCache: StateFlow<Map<String, List<Reply>>> = _repliesCache.asStateFlow()

    // 正在加载回复的评论 ID 集合
    private val _loadingReplies = MutableStateFlow<Set<String>>(emptySet())
    val loadingReplies: StateFlow<Set<String>> = _loadingReplies.asStateFlow()

    // 回复加载错误：commentId → 错误信息
    private val _repliesError = MutableStateFlow<Map<String, String?>>(emptyMap())
    val repliesError: StateFlow<Map<String, String?>> = _repliesError.asStateFlow()

    // 回复相关状态
    // 当前正在回复的目标：commentId 为父评论 ID；replyToUsername 非空表示回复某条回复
    private val _activeReplyTarget = MutableStateFlow<ReplyTarget?>(null)
    val activeReplyTarget: StateFlow<ReplyTarget?> = _activeReplyTarget.asStateFlow()

    // 是否正在提交回复
    private val _isPostingReply = MutableStateFlow(false)
    val isPostingReply: StateFlow<Boolean> = _isPostingReply.asStateFlow()

    // 回复提交错误（未登录 / CSRF 缺失 / 网络失败 / 空内容等）
    private val _replyError = MutableStateFlow<String?>(null)
    val replyError: StateFlow<String?> = _replyError.asStateFlow()

    // 已展开回复区的评论 ID 集合（由 ViewModel 驱动，便于发表回复后自动展开）
    private val _expandedReplies = MutableStateFlow<Set<String>>(emptySet())
    val expandedReplies: StateFlow<Set<String>> = _expandedReplies.asStateFlow()

    private var currentVideoId: String = ""
    private var currentVideoUrl: String = ""

    fun loadVideoDetail(videoUrl: String) {
        AppLogger.d("DetailViewModel", "loadVideoDetail called, url: $videoUrl")
        _isLoading.value = true
        _error.value = null

        currentVideoId = extractVideoId(videoUrl)
        currentVideoUrl = videoUrl

        // 切换视频时重置评论状态，使新视频的评论可被重新加载
        _comments.value = emptyList()
        _commentsLoaded.value = false
        _commentsError.value = null
        _isLoadingComments.value = false

        // 切换视频时重置订阅状态
        _isSubscribed.value = false
        _isSubscribing.value = false
        _subscribeError.value = null

        // 切换视频时重置发表评论状态
        _isPostingComment.value = false
        _postCommentError.value = null
        _lastPostedComment.value = null

        // 切换视频时重置评论点赞状态
        _likingComments.value = emptySet()
        _commentLikeError.value = null

        // 切换视频时清空回复缓存
        _repliesCache.value = emptyMap()
        _loadingReplies.value = emptySet()
        _repliesError.value = emptyMap()

        // 切换视频时重置回复输入/展开状态
        _activeReplyTarget.value = null
        _isPostingReply.value = false
        _replyError.value = null
        _expandedReplies.value = emptySet()

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getVideoDetail(videoUrl)
            }
            when (result) {
                is AppResult.Success -> {
                    val detail = result.data
                    AppLogger.d("DetailViewModel", "Got video detail: ${detail.title}, sources: ${detail.videoSources.size}")
                    _videoDetail.value = detail
                    // 当前登录用户 ID：订阅表单中的 subscribe-user-id 最权威（与官网一致），
                    // 缺失时回退到 currentUserId（评论/点赞表单解析），持久化供评论/点赞等接口使用
                    val resolvedUserId = detail.subscribeUserId.ifBlank { detail.currentUserId }
                    if (resolvedUserId.isNotBlank()) {
                        Preferences.saveUserId(resolvedUserId)
                    }
                    if (currentVideoId.isNotEmpty()) {
                        val history = WatchHistory(
                            id = currentVideoId,
                            title = detail.title,
                            thumbnailUrl = detail.posterUrl,
                            videoUrl = currentVideoUrl,
                            author = detail.author,
                            duration = detail.releaseDate,
                            watchedAt = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            repository.addWatchHistory(history)
                        }
                    }

                    val isFav = withContext(Dispatchers.IO) {
                        repository.isFavorite(currentVideoId)
                    }
                    _isFavorite.value = isFav

                    // 详情页已解析出订阅状态（subscribeStatus == "1" 表示已订阅），还原高亮
                    _isSubscribed.value = detail.subscribeStatus == "1"
                }
                is AppResult.Error -> {
                    AppLogger.e("DetailViewModel", "Error loading video detail: ${result.message}", result.exception)
                    _error.value = result.message
                }
                is AppResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun loadDownloadQualities() {
        if (currentVideoId.isEmpty()) return
        AppLogger.d("DetailViewModel", "loadDownloadQualities called, videoId: $currentVideoId")
        _isLoadingQualities.value = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getDownloadQualities(currentVideoId)
            }
            when (result) {
                is AppResult.Success -> {
                    AppLogger.d("DetailViewModel", "Got ${result.data.size} download qualities")
                    _downloadQualities.value = result.data
                }
                is AppResult.Error -> {
                    AppLogger.e("DetailViewModel", "Error loading download qualities: ${result.message}", result.exception)
                    _downloadQualities.value = emptyList()
                }
                is AppResult.Loading -> {}
            }
            _isLoadingQualities.value = false
        }
    }

    /**
     * 加载评论列表。
     * 已加载过则不重复请求，除非 force=true。
     */
    fun loadComments(force: Boolean = false) {
        if (currentVideoId.isEmpty()) return
        if (_commentsLoaded.value && !force) {
            AppLogger.d("DetailViewModel", "Comments already loaded, skip")
            return
        }
        AppLogger.d("DetailViewModel", "loadComments called, videoId: $currentVideoId")
        _isLoadingComments.value = true
        _commentsError.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getComments(currentVideoId)
            }
            when (result) {
                is AppResult.Success -> {
                    AppLogger.d("DetailViewModel", "Got ${result.data.size} comments")
                    _comments.value = result.data
                    _commentsLoaded.value = true
                }
                is AppResult.Error -> {
                    AppLogger.e("DetailViewModel", "Error loading comments: ${result.message}", result.exception)
                    _commentsError.value = result.message ?: "评论加载失败"
                }
                is AppResult.Loading -> {}
            }
            _isLoadingComments.value = false
        }
    }

    /**
     * 加载某条评论的回复列表。
     * 已缓存则不重复请求，除非 force=true。
     */
    fun loadReplies(commentId: String, force: Boolean = false) {
        if (commentId.isEmpty()) return
        if (!force && _repliesCache.value.containsKey(commentId)) {
            AppLogger.d("DetailViewModel", "Replies for $commentId already cached, skip")
            return
        }
        if (_loadingReplies.value.contains(commentId)) {
            AppLogger.d("DetailViewModel", "Replies for $commentId is loading, skip")
            return
        }
        AppLogger.d("DetailViewModel", "loadReplies called, commentId: $commentId")
        _loadingReplies.value = _loadingReplies.value + commentId
        _repliesError.value = _repliesError.value - commentId

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getReplies(commentId)
            }
            when (result) {
                is AppResult.Success -> {
                    AppLogger.d("DetailViewModel", "Got ${result.data.size} replies for comment $commentId")
                    _repliesCache.value = _repliesCache.value + (commentId to result.data)
                }
                is AppResult.Error -> {
                    AppLogger.e("DetailViewModel", "Error loading replies: ${result.message}", result.exception)
                    _repliesError.value = _repliesError.value + (commentId to (result.message ?: "回复加载失败"))
                }
                is AppResult.Loading -> {}
            }
            _loadingReplies.value = _loadingReplies.value - commentId
        }
    }

    /**
     * 打开某条评论的回复输入框。
     * replyToUsername 非空表示回复某条回复（内容将带 "@用户名 " 前缀，与官网一致）。
     * 同时自动展开该评论的回复区，并在尚未加载时拉取回复列表，方便立即看到新回复。
     */
    fun startReply(commentId: String, replyToUsername: String? = null) {
        if (Preferences.savedUserId.isBlank()) {
            _replyError.value = "请先登录后再回复评论"
            return
        }
        _activeReplyTarget.value = ReplyTarget(commentId, replyToUsername)
        _expandedReplies.value = _expandedReplies.value + commentId
        if (!_repliesCache.value.containsKey(commentId)) {
            loadReplies(commentId)
        }
    }

    /**
     * 关闭回复输入框。
     */
    fun cancelReply() {
        _activeReplyTarget.value = null
        _replyError.value = null
    }

    /**
     * 切换某条评论的回复区展开/收起状态。
     * 首次展开且尚未加载时拉取回复列表。
     */
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
     * 提交回复。
     * 采用乐观更新：先 +1 评论回复数并把临时回复插入列表，接口成功后再用服务端返回的
     * 真实回复（含真实 ID / 用户名 / 时间）替换临时项；失败回滚并提示错误。
     */
    fun submitReply(text: String) {
        val target = _activeReplyTarget.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _replyError.value = "回复内容不能为空"
            return
        }
        val detail = _videoDetail.value
        if (detail == null || detail.csrfToken.isBlank()) {
            _replyError.value = "CSRF Token 缺失，请刷新页面后重试"
            return
        }

        // 回复其他回复时，内容前补 "@用户名 "（与官网一致），避免与解析/展示重复
        val finalText = if (!target.replyToUsername.isNullOrEmpty() && !trimmed.startsWith("@")) {
            "@${target.replyToUsername} $trimmed"
        } else trimmed

        _isPostingReply.value = true
        _replyError.value = null

        // 构造临时回复（用户名/头像待服务端返回），用于乐观更新
        val optimisticReply = Reply(
            id = "local_${System.currentTimeMillis()}",
            username = "",
            avatarUrl = "",
            time = "刚刚",
            content = trimmed,
            likeCount = 0,
            replyTo = target.replyToUsername
        )
        val prevComments = _comments.value
        val prevReplies = _repliesCache.value
        // +1 回复数
        _comments.value = prevComments.map { c ->
            if (c.id == target.commentId) c.copy(replyCount = c.replyCount + 1) else c
        }
        // 追加临时回复
        _repliesCache.value = prevReplies.toMutableMap().apply {
            val list = this[target.commentId] ?: emptyList()
            this[target.commentId] = list + optimisticReply
        }
        _expandedReplies.value = _expandedReplies.value + target.commentId

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.replyComment(target.commentId, finalText, detail.csrfToken)
            }
            when (result) {
                is AppResult.Success -> {
                    val newReply = result.data
                    // 用服务端返回的回复替换临时项：先剔除临时项（防 loadReplies 竞态覆盖），再追加真实回复
                    _repliesCache.value = _repliesCache.value.toMutableMap().apply {
                        val list = this[target.commentId] ?: emptyList()
                        this[target.commentId] = list.filter { it.id != optimisticReply.id } + newReply
                    }
                    _activeReplyTarget.value = null
                    AppLogger.d("DetailViewModel", "Reply posted for comment ${target.commentId}, replyId=${newReply.id}")
                }
                is AppResult.Error -> {
                    // 回滚到提交前状态
                    _comments.value = prevComments
                    _repliesCache.value = prevReplies
                    _replyError.value = result.message ?: "回复评论失败"
                    AppLogger.e("DetailViewModel", "Error posting reply: ${result.message}", result.exception)
                }
                is AppResult.Loading -> {}
            }
            _isPostingReply.value = false
        }
    }

    /**
     * 清除回复提交的错误状态。
     */
    fun clearReplyError() {
        _replyError.value = null
    }

    fun startDownload(quality: DownloadQuality) {
        val detail = _videoDetail.value
        val title = detail?.title ?: "video"
        val thumbnailUrl = detail?.posterUrl ?: ""
        downloadManager.startDownload(
            title,
            quality.resolution,
            quality.downloadUrl,
            thumbnailUrl,
            currentVideoId
        )
        AppLogger.d("DetailViewModel", "Started download: ${quality.resolution}")
    }

    /**
     * 发表评论。
     * 成功后将新评论追加到列表头部，并更新 VideoDetail 中的 commentCount。
     */
    fun postComment(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _postCommentError.value = "评论内容不能为空"
            return
        }
        if (currentVideoId.isEmpty()) {
            _postCommentError.value = "视频信息缺失，无法发表评论"
            return
        }
        val detail = _videoDetail.value
        if (detail == null || detail.csrfToken.isBlank()) {
            _postCommentError.value = "CSRF Token 缺失，请刷新页面后重试"
            return
        }

        _isPostingComment.value = true
        _postCommentError.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.postComment(
                    videoId = currentVideoId,
                    commentText = trimmed,
                    commentCount = detail.commentCount,
                    csrfToken = detail.csrfToken
                )
            }
            when (result) {
                is AppResult.Success -> {
                    val (newComment, newCount) = result.data
                    // 追加新评论到列表头部
                    _comments.value = listOf(newComment) + _comments.value
                    _lastPostedComment.value = newComment

                    // 更新 VideoDetail 中的 commentCount
                    _videoDetail.value = detail.copy(commentCount = newCount)

                    AppLogger.d("DetailViewModel", "Comment posted: id=${newComment.id}, newCount=$newCount")
                }
                is AppResult.Error -> {
                    AppLogger.e("DetailViewModel", "Error posting comment: ${result.message}", result.exception)
                    _postCommentError.value = result.message ?: "发表评论失败"
                }
                is AppResult.Loading -> {}
            }
            _isPostingComment.value = false
        }
    }

    /**
     * 清除发表评论的错误状态。
     */
    fun clearPostCommentError() {
        _postCommentError.value = null
    }

    /**
     * 切换评论点赞状态（点赞 / 取消点赞）。
     * 调用官网 /commentLike 接口，凭 like-comment-status 区分点赞与取消。
     * 采用乐观更新：先本地切换状态与计数，接口失败再回滚到原始状态。
     */
    fun toggleCommentLike(comment: Comment) {
        val detail = _videoDetail.value
        if (detail == null || detail.csrfToken.isBlank()) {
            _commentLikeError.value = "CSRF Token 缺失，请刷新页面后重试"
            return
        }
        if (Preferences.savedUserId.isBlank()) {
            _commentLikeError.value = "请先登录后再点赞评论"
            return
        }
        // 防止同一评论在请求未完成前被重复点击
        if (_likingComments.value.contains(comment.id)) return

        val willLike = comment.likeStatus != 1
        val optimistic = comment.copy(
            likeStatus = if (willLike) 1 else 0,
            likeCount = (comment.likeCount + if (willLike) 1 else -1).coerceAtLeast(0)
        )
        // 乐观更新列表
        _comments.value = _comments.value.map { if (it.id == comment.id) optimistic else it }
        _likingComments.value = _likingComments.value + comment.id

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.toggleCommentLike(
                    commentId = comment.id,
                    currentLikeStatus = comment.likeStatus,
                    likeCount = comment.likeCount,
                    csrfToken = detail.csrfToken
                )
            }
            when (result) {
                is AppResult.Success -> {
                    AppLogger.d("DetailViewModel", "Comment like toggled for ${comment.id}, willLike=$willLike")
                }
                is AppResult.Error -> {
                    AppLogger.e("DetailViewModel", "Error toggling comment like: ${result.message}", result.exception)
                    // 回滚到原始状态
                    _comments.value = _comments.value.map { if (it.id == comment.id) comment else it }
                    _commentLikeError.value = result.message ?: "评论点赞失败"
                }
                is AppResult.Loading -> {}
            }
            _likingComments.value = _likingComments.value - comment.id
        }
    }

    /**
     * 清除评论点赞的错误状态。
     */
    fun clearCommentLikeError() {
        _commentLikeError.value = null
    }

    fun toggleFavorite() {
        if (currentVideoId.isEmpty()) return
        AppLogger.d("DetailViewModel", "toggleFavorite called, currentVideoId: $currentVideoId")

        viewModelScope.launch {
            try {
                val currentState = _isFavorite.value
                val detail = _videoDetail.value

                if (currentState) {
                    withContext(Dispatchers.IO) {
                        repository.removeFavorite(currentVideoId)
                    }
                    _isFavorite.value = false
                    AppLogger.d("DetailViewModel", "Removed from favorites")
                } else {
                    if (detail != null) {
                        val favoriteVideo = FavoriteVideo(
                            id = currentVideoId,
                            title = detail.title,
                            thumbnailUrl = detail.posterUrl,
                            videoUrl = currentVideoUrl,
                            author = detail.author,
                            duration = "",
                            likeRate = "",
                            viewCount = "",
                            createdAt = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            repository.addFavorite(favoriteVideo)
                        }
                        _isFavorite.value = true
                        AppLogger.d("DetailViewModel", "Added to favorites")
                    }
                }
            } catch (e: IOException) {
                AppLogger.e("DetailViewModel", "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    private fun extractVideoId(url: String): String {
        val regex = Regex("v=(\\d+)")
        val match = regex.find(url)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * 订阅 / 取消订阅作者。
     * 调用官网 /subscribe 接口，凭 subscribe-status 区分订阅与取消。
     * 采用乐观更新：先本地切换订阅状态，接口成功用服务端返回的新状态/新 CSRF Token 修正，
     * 失败则回滚到原始状态并提示错误。
     */
    fun toggleSubscribe() {
        val detail = _videoDetail.value
        if (detail == null || detail.subscribeArtistId.isBlank()) {
            _subscribeError.value = SubscribeError.ARTIST_ID_MISSING
            return
        }
        if (detail.csrfToken.isBlank()) {
            _subscribeError.value = SubscribeError.CSRF_MISSING
            return
        }
        if (detail.subscribeUserId.isBlank()) {
            _subscribeError.value = SubscribeError.NOT_LOGGED_IN
            return
        }
        // 防止请求未完成前重复点击
        if (_isSubscribing.value) return

        val willSubscribe = !_isSubscribed.value
        // 乐观更新订阅状态
        _isSubscribed.value = willSubscribe
        _isSubscribing.value = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.toggleSubscribe(
                    userId = detail.subscribeUserId,
                    artistId = detail.subscribeArtistId,
                    willSubscribe = willSubscribe,
                    csrfToken = detail.csrfToken
                )
            }
            when (result) {
                is AppResult.Success -> {
                    val res = result.data
                    // 以服务端返回的新状态为准
                    _isSubscribed.value = res.subscribeStatus == "1"
                    // 刷新详情页的 CSRF Token（接口回传的新令牌），保证后续请求有效
                    if (res.csrfToken.isNotBlank()) {
                        _videoDetail.value = _videoDetail.value?.copy(csrfToken = res.csrfToken)
                    }
                    AppLogger.d("DetailViewModel", "Subscribe toggled for artist ${detail.subscribeArtistId}, willSubscribe=$willSubscribe")
                }
                is AppResult.Error -> {
                    // 回滚到原始状态
                    _isSubscribed.value = !willSubscribe
                    _subscribeError.value = SubscribeError.FAILED
                    AppLogger.e("DetailViewModel", "Error toggling subscribe: ${result.message}", result.exception)
                }
                is AppResult.Loading -> {}
            }
            _isSubscribing.value = false
        }
    }

    /**
     * 清除订阅操作的错误状态。
     */
    fun clearSubscribeError() {
        _subscribeError.value = null
    }
}

/**
 * 订阅作者操作的错误类型，用于 UI 侧按类型选择本地化文案。
 */
enum class SubscribeError {
    NOT_LOGGED_IN,   // 未登录
    CSRF_MISSING,    // CSRF Token 缺失
    ARTIST_ID_MISSING, // 作者 ID 缺失
    FAILED           // 其他失败（网络/解析等）
}

/**
 * 评论回复的目标。
 * @param commentId 被回复的评论 ID（replyComment 接口只认父评论 ID）
 * @param replyToUsername 非空时表示回复某条回复，提交内容会带 "@用户名 " 前缀
 */
data class ReplyTarget(
    val commentId: String,
    val replyToUsername: String? = null
)
