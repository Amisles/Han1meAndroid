package app.amisles.hanime.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.domain.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    private val mutationGate = Mutex()

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            mutationGate.withLock { block() }
        }
    }

    fun pauseDownload(taskId: Int) = mutate { downloadManager.pauseDownload(taskId) }
    fun resumeDownload(taskId: Int) = mutate { downloadManager.resumeDownload(taskId) }
    fun cancelDownload(taskId: Int) = mutate { downloadManager.cancelDownload(taskId) }
    fun retryAllFailed() = mutate { downloadManager.retryAllFailed() }
}
