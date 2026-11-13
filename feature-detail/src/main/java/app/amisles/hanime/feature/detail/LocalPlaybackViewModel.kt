package app.amisles.hanime.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalPlaybackState(
    val ready: Boolean = false,
    val resumePositionMs: Long = 0L,
    val title: String = "",
    val posterUrl: String = ""
)

@HiltViewModel
class LocalPlaybackViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val downloads: DownloadRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LocalPlaybackState())
    val state: StateFlow<LocalPlaybackState> = _state.asStateFlow()

    private var historyId: String = ""
    private var filePath: String = ""

    fun bind(path: String) {
        if (path.isBlank() || path == filePath) return
        filePath = path
        val task = downloads.tasks.value.firstOrNull { it.filePath == path }
        historyId = task?.videoId?.takeIf { it.isNotBlank() } ?: localHistoryId(path)
        val title = task?.title?.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension
        val posterUrl = task?.thumbnailUrl.orEmpty()
        viewModelScope.launch {
            val resume = withContext(Dispatchers.IO) {
                runCatching { library.getWatchHistory(historyId)?.playbackPosition }.getOrNull() ?: 0L
            }
            _state.value = LocalPlaybackState(
                ready = true,
                resumePositionMs = resume,
                title = title,
                posterUrl = posterUrl
            )
        }
    }

    fun saveProgress(position: Long, duration: Long) {
        if (historyId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { persist(position, duration) }
    }

    fun saveFinalProgress(position: Long, duration: Long) {
        if (historyId.isEmpty() || position <= 0L) return
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { persist(position, duration) }
        }
    }

    fun markWatched() {
        if (historyId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { library.markWatched(historyId) }
        }
    }

    private suspend fun persist(position: Long, duration: Long) {
        val title = _state.value.title
        val posterUrl = _state.value.posterUrl
        runCatching {
            if (library.updateWatchProgress(historyId, position, duration)) return@runCatching
            library.addWatchHistory(
                WatchHistory(
                    id = historyId,
                    title = title,
                    thumbnailUrl = posterUrl,
                    videoUrl = filePath,
                    author = "",
                    duration = "",
                    watchedAt = System.currentTimeMillis(),
                    playbackPosition = position,
                    playbackDuration = duration
                )
            )
        }
    }

    private fun localHistoryId(path: String): String =
        LOCAL_HISTORY_PREFIX + path.hashCode().toUInt().toString(16)

    private companion object {
        const val LOCAL_HISTORY_PREFIX = "local:"
    }
}
