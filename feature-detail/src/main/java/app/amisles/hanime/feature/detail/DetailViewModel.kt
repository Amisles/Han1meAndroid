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
import app.amisles.hanime.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
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

    // 回复缓存：commentId → 回复列表
    private val _repliesCache = MutableStateFlow<Map<String, List<Reply>>>(emptyMap())
    val repliesCache: StateFlow<Map<String, List<Reply>>> = _repliesCache.asStateFlow()

    // 正在加载回复的评论 ID 集合
    private val _loadingReplies = MutableStateFlow<Set<String>>(emptySet())
    val loadingReplies: StateFlow<Set<String>> = _loadingReplies.asStateFlow()

    // 回复加载错误：commentId → 错误信息
    private val _repliesError = MutableStateFlow<Map<String, String?>>(emptyMap())
    val repliesError: StateFlow<Map<String, String?>> = _repliesError.asStateFlow()

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

        // 切换视频时重置发表评论状态
        _isPostingComment.value = false
        _postCommentError.value = null
        _lastPostedComment.value = null

        // 切换视频时清空回复缓存
        _repliesCache.value = emptyMap()
        _loadingReplies.value = emptySet()
        _repliesError.value = emptyMap()

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getVideoDetail(videoUrl)
            }
            when (result) {
                is AppResult.Success -> {
                    val detail = result.data
                    AppLogger.d("DetailViewModel", "Got video detail: ${detail.title}, sources: ${detail.videoSources.size}")
                    _videoDetail.value = detail
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
            } catch (e: Exception) {
                AppLogger.e("DetailViewModel", "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    private fun extractVideoId(url: String): String {
        val regex = Regex("v=(\\d+)")
        val match = regex.find(url)
        return match?.groupValues?.get(1) ?: ""
    }
}
