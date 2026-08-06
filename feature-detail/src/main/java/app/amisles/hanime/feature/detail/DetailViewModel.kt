package app.amisles.hanime.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
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

        viewModelScope.launch {
            try {
                val detail = withContext(Dispatchers.IO) {
                    repository.getVideoDetail(videoUrl)
                }
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
            } catch (e: Exception) {
                AppLogger.e("DetailViewModel", "Error loading video detail: ${e.message}", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadDownloadQualities() {
        if (currentVideoId.isEmpty()) return
        AppLogger.d("DetailViewModel", "loadDownloadQualities called, videoId: $currentVideoId")
        _isLoadingQualities.value = true

        viewModelScope.launch {
            try {
                val qualities = withContext(Dispatchers.IO) {
                    repository.getDownloadQualities(currentVideoId)
                }
                AppLogger.d("DetailViewModel", "Got ${qualities.size} download qualities")
                _downloadQualities.value = qualities
            } catch (e: Exception) {
                AppLogger.e("DetailViewModel", "Error loading download qualities: ${e.message}", e)
                _downloadQualities.value = emptyList()
            } finally {
                _isLoadingQualities.value = false
            }
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
            try {
                val list = withContext(Dispatchers.IO) {
                    repository.getComments(currentVideoId)
                }
                AppLogger.d("DetailViewModel", "Got ${list.size} comments")
                _comments.value = list
                _commentsLoaded.value = true
            } catch (e: Exception) {
                AppLogger.e("DetailViewModel", "Error loading comments: ${e.message}", e)
                _commentsError.value = e.message ?: "评论加载失败"
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun startDownload(quality: DownloadQuality) {
        val detail = _videoDetail.value
        val title = detail?.title ?: "video"
        val thumbnailUrl = detail?.posterUrl ?: ""
        downloadManager.startDownload(title, quality.resolution, quality.downloadUrl, thumbnailUrl)
        AppLogger.d("DetailViewModel", "Started download: ${quality.resolution}")
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
