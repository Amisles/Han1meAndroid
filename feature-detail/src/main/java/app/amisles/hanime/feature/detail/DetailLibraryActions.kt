package app.amisles.hanime.feature.detail

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.domain.model.WatchLaterVideo
import app.amisles.hanime.domain.repository.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DetailLibraryActions(
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val onLocalSaveFailed: () -> Unit
) {
    private val _savedPosition = MutableStateFlow(0L)
    val savedPosition: StateFlow<Long> = _savedPosition.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isWatchLater = MutableStateFlow(false)
    val isWatchLater: StateFlow<Boolean> = _isWatchLater.asStateFlow()

    fun reset() {
        _savedPosition.value = 0L
        _isFavorite.value = false
        _isWatchLater.value = false
    }

    suspend fun loadForVideo(videoId: String, videoUrl: String, detail: VideoDetail) {
        if (videoId.isEmpty()) return
        // 先读取已有历史，保留播放进度，避免每次进入覆盖为 0
        val existing = withContext(Dispatchers.IO) { library.getWatchHistory(videoId) }
        val history = WatchHistory(
            id = videoId,
            title = detail.title,
            thumbnailUrl = detail.posterUrl,
            videoUrl = videoUrl,
            author = detail.author,
            duration = "",
            watchedAt = System.currentTimeMillis(),
            playbackPosition = existing?.playbackPosition ?: 0L,
            playbackDuration = existing?.playbackDuration ?: 0L
        )
        withContext(Dispatchers.IO) { library.addWatchHistory(history) }
        _savedPosition.value = existing?.playbackPosition ?: 0L
        _isFavorite.value = withContext(Dispatchers.IO) { library.isFavorite(videoId) }
        _isWatchLater.value = withContext(Dispatchers.IO) { library.getWatchLaterIds() }
            .contains(videoId)
    }

    fun toggleWatchLater(videoId: String, videoUrl: String, detail: VideoDetail?) {
        if (videoId.isEmpty()) return
        scope.launch {
            try {
                val willAdd = !_isWatchLater.value
                val ok = withContext(Dispatchers.IO) {
                    if (willAdd) {
                        library.addWatchLater(
                            WatchLaterVideo(
                                id = videoId,
                                title = detail?.title.orEmpty(),
                                thumbnailUrl = detail?.posterUrl.orEmpty(),
                                videoUrl = videoUrl,
                                author = detail?.author.orEmpty(),
                                duration = "",
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        library.removeWatchLater(videoId)
                    }
                }
                if (ok) _isWatchLater.value = willAdd
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DetailLibraryActions", "Error toggling watch later: ${e.message}", e)
            }
        }
    }

    fun toggleFavorite(videoId: String, videoUrl: String, detail: VideoDetail?) {
        if (videoId.isEmpty()) return
        scope.launch {
            try {
                val currentState = _isFavorite.value
                var written = true
                if (currentState) {
                    written = withContext(Dispatchers.IO) { library.removeFavorite(videoId) }
                } else if (detail != null) {
                    written = withContext(Dispatchers.IO) {
                        library.addFavorite(
                            FavoriteVideo(
                                id = videoId,
                                title = detail.title,
                                thumbnailUrl = detail.posterUrl,
                                videoUrl = videoUrl,
                                author = detail.author,
                                duration = "",
                                likeRate = "",
                                viewCount = "",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                _isFavorite.value = withContext(Dispatchers.IO) { library.isFavorite(videoId) }
                if (!written) onLocalSaveFailed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DetailLibraryActions", "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    fun saveProgress(
        videoId: String,
        videoUrl: String,
        detail: VideoDetail,
        position: Long,
        duration: Long,
        uncancellable: Boolean
    ) {
        if (videoId.isEmpty()) return
        scope.launch {
            val context = if (uncancellable) NonCancellable + Dispatchers.IO else Dispatchers.IO
            withContext(context) {
                persistProgress(videoId, videoUrl, detail, position, duration)
            }
        }
    }

    suspend fun savedPosition(videoId: String): Long =
        withContext(Dispatchers.IO) { library.getWatchHistory(videoId)?.playbackPosition ?: 0L }

    private suspend fun persistProgress(
        id: String,
        videoUrl: String,
        detail: VideoDetail,
        position: Long,
        duration: Long
    ) {
        try {
            val updated = library.updateWatchProgress(id, position, duration)
            if (updated) return
            library.addWatchHistory(
                WatchHistory(
                    id = id,
                    title = detail.title,
                    thumbnailUrl = detail.posterUrl,
                    videoUrl = videoUrl,
                    author = detail.author,
                    duration = "",
                    watchedAt = System.currentTimeMillis(),
                    playbackPosition = position,
                    playbackDuration = duration
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 进度落盘失败不应影响播放：记录后吞掉，避免异常逃逸出 viewModelScope 导致崩溃
            AppLogger.e("DetailLibraryActions", "Error saving playback progress: ${e.message}", e)
        }
    }
}
