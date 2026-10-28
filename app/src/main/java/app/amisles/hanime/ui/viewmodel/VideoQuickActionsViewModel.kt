package app.amisles.hanime.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.extractVideoId
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.LibraryRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface QuickDownloadEvent {
    data class Started(val taskId: Int) : QuickDownloadEvent
    data object AlreadyActive : QuickDownloadEvent
    data object AlreadyCompleted : QuickDownloadEvent
    data object InvalidUrl : QuickDownloadEvent
    data object Failed : QuickDownloadEvent
}

data class QuickActionTarget(
    val video: HanimeVideo,
    val qualities: List<DownloadQuality> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: Boolean = false
)

@HiltViewModel
class VideoQuickActionsViewModel @Inject constructor(
    private val content: ContentRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _target = MutableStateFlow<QuickActionTarget?>(null)
    val target: StateFlow<QuickActionTarget?> = _target.asStateFlow()

    private val _events = Channel<Any>(Channel.BUFFERED)
    val events: Flow<Any> = _events.receiveAsFlow()

    private val _downloadEvents = Channel<QuickDownloadEvent>(Channel.BUFFERED)
    val downloadEvents: Flow<QuickDownloadEvent> = _downloadEvents.receiveAsFlow()

    private var favoriteCheckJob: Job? = null
    private var qualitiesJob: Job? = null
    private var requestedVideoId: String = ""

    fun ensureFavoritesLoaded(ids: List<String>) {
        val missing = ids.filter { it.isNotEmpty() && !_favoriteIds.value.contains(it) }
        if (missing.isEmpty()) return
        favoriteCheckJob?.cancel()
        favoriteCheckJob = viewModelScope.launch {
            try {
                val favorites = withContext(Dispatchers.IO) { library.getAllFavorites() }
                _favoriteIds.value = favorites.map { it.id }.toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _favoriteIds.value = emptySet()
            }
        }
    }

    fun toggleFavorite(video: HanimeVideo) {
        val videoId = extractVideoId(video.videoUrl)
        if (videoId.isEmpty()) return
        val willAdd = !_favoriteIds.value.contains(videoId)
        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    if (willAdd) {
                        library.addFavorite(
                            FavoriteVideo(
                                id = videoId,
                                title = video.title,
                                thumbnailUrl = video.thumbnailUrl,
                                videoUrl = video.videoUrl,
                                author = video.author,
                                duration = video.duration,
                                likeRate = video.likeRate,
                                viewCount = video.viewCount,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        library.removeFavorite(videoId)
                    }
                }
                val verified = withContext(Dispatchers.IO) { library.isFavorite(videoId) }
                _favoriteIds.value = if (verified) {
                    _favoriteIds.value + videoId
                } else {
                    _favoriteIds.value - videoId
                }
                if (!ok) {
                    _events.trySend(R.string.local_save_failed)
                } else {
                    _events.trySend(if (verified) R.string.favorite_added else R.string.favorite_removed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend(R.string.local_save_failed)
            }
        }
    }

    fun openDownloadSheet(video: HanimeVideo) {
        val videoId = extractVideoId(video.videoUrl)
        if (videoId.isEmpty()) {
            _downloadEvents.trySend(QuickDownloadEvent.InvalidUrl)
            return
        }
        requestedVideoId = videoId
        _target.value = QuickActionTarget(video = video, isLoading = true)
        qualitiesJob?.cancel()
        qualitiesJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { content.getDownloadQualities(videoId) }
                if (requestedVideoId != videoId) return@launch
                when (result) {
                    is AppResult.Success -> {
                        _target.value = _target.value?.copy(
                            qualities = result.data,
                            isLoading = false,
                            loadError = result.data.isEmpty()
                        )
                    }
                    is AppResult.Error -> {
                        _target.value = _target.value?.copy(isLoading = false, loadError = true)
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestedVideoId == videoId) {
                    _target.value = _target.value?.copy(isLoading = false, loadError = true)
                }
            }
        }
    }

    fun dismissDownloadSheet() {
        requestedVideoId = ""
        _target.value = null
    }

    /** 播放列表一键收藏：跳过已收藏条目，只新增缺失的。 */
    fun favoriteAll(videos: List<HanimeVideo>) {
        if (videos.isEmpty()) return
        viewModelScope.launch {
            try {
                val favorites = withContext(Dispatchers.IO) { library.getAllFavorites() }
                val existing = favorites.map { it.id }.toSet()
                val now = System.currentTimeMillis()
                var added = 0
                videos.distinctBy { extractVideoId(it.videoUrl) }
                    .filter { extractVideoId(it.videoUrl).isNotEmpty() }
                    .filter { extractVideoId(it.videoUrl) !in existing }
                    .forEach { video ->
                        val ok = withContext(Dispatchers.IO) {
                            library.addFavorite(
                                FavoriteVideo(
                                    id = extractVideoId(video.videoUrl),
                                    title = video.title,
                                    thumbnailUrl = video.thumbnailUrl,
                                    videoUrl = video.videoUrl,
                                    author = video.author,
                                    duration = video.duration,
                                    likeRate = video.likeRate,
                                    viewCount = video.viewCount,
                                    createdAt = now + added
                                )
                            )
                        }
                        if (ok) added++
                    }
                _events.trySend(
                    if (added > 0) context.getString(R.string.playlist_favorite_done_with_count, added)
                    else context.getString(R.string.playlist_favorite_exists)
                )
                if (added > 0) {
                    _favoriteIds.value = _favoriteIds.value + videos.mapNotNull { v ->
                        extractVideoId(v.videoUrl).takeIf { it.isNotEmpty() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend(R.string.local_save_failed)
            }
        }
    }

    fun startQuickDownload(quality: DownloadQuality) {
        val current = _target.value ?: return
        val videoId = extractVideoId(current.video.videoUrl)
        if (videoId.isEmpty()) {
            _downloadEvents.trySend(QuickDownloadEvent.InvalidUrl)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val taskId = downloads.startDownload(
                title = current.video.title,
                quality = quality.quality,
                url = quality.downloadUrl,
                thumbnailUrl = current.video.thumbnailUrl,
                videoId = videoId
            )
            val event = when {
                taskId >= 0 -> QuickDownloadEvent.Started(taskId)
                taskId == DownloadRepository.RESULT_ALREADY_ACTIVE -> QuickDownloadEvent.AlreadyActive
                taskId == DownloadRepository.RESULT_ALREADY_COMPLETED -> QuickDownloadEvent.AlreadyCompleted
                taskId == DownloadRepository.RESULT_INVALID_URL -> QuickDownloadEvent.InvalidUrl
                else -> QuickDownloadEvent.Failed
            }
            _downloadEvents.trySend(event)
        }
    }
}
