package app.amisles.hanime.feature.download

import androidx.lifecycle.ViewModel
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.domain.model.DownloadTask
import kotlinx.coroutines.flow.StateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    fun pauseDownload(taskId: Int) = downloadManager.pauseDownload(taskId)
    fun resumeDownload(taskId: Int) = downloadManager.resumeDownload(taskId)
    fun cancelDownload(taskId: Int) = downloadManager.cancelDownload(taskId)
    fun retryAllFailed() = downloadManager.retryAllFailed()
}
