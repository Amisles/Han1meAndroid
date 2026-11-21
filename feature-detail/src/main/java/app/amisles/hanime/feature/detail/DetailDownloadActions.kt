package app.amisles.hanime.feature.detail

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.StartDownloadResult
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DetailDownloadActions(
    private val content: ContentRepository,
    private val downloads: DownloadRepository,
    private val scope: CoroutineScope
) {
    private val _qualities = MutableStateFlow<List<DownloadQuality>>(emptyList())
    val qualities: StateFlow<List<DownloadQuality>> = _qualities.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _results = Channel<StartDownloadResult>(Channel.BUFFERED)
    val results: Flow<StartDownloadResult> = _results.receiveAsFlow()

    fun reset() {
        _qualities.value = emptyList()
        _isLoading.value = false
    }

    fun load(videoId: String, isCurrent: () -> Boolean) {
        if (videoId.isEmpty()) return
        AppLogger.d("DetailDownloadActions", "load called, videoId: $videoId")
        _isLoading.value = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    content.getDownloadQualities(videoId)
                }
                if (!isCurrent()) {
                    AppLogger.d("DetailDownloadActions", "Discard stale qualities response: $videoId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> _qualities.value = result.data
                    is AppResult.Error -> {
                        AppLogger.e(
                            "DetailDownloadActions",
                            "Error loading qualities: ${result.message}",
                            result.exception
                        )
                        _qualities.value = emptyList()
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DetailDownloadActions", "Error loading qualities: ${e.message}", e)
                if (isCurrent()) _qualities.value = emptyList()
            } finally {
                if (isCurrent()) _isLoading.value = false
            }
        }
    }

    fun start(detail: VideoDetail?, videoId: String, quality: DownloadQuality) {
        val title = detail?.title ?: "video"
        val thumbnailUrl = detail?.posterUrl ?: ""
        scope.launch(Dispatchers.IO) {
            val result = downloads.startDownload(
                title,
                quality.quality,
                quality.downloadUrl,
                thumbnailUrl,
                videoId
            )
            AppLogger.d("DetailDownloadActions", "Start download result=$result, quality=${quality.quality}")
            _results.trySend(result)
        }
    }
}
