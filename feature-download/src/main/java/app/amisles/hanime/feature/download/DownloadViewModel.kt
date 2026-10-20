package app.amisles.hanime.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.DownloadRepository
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
    private val downloads: DownloadRepository
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = downloads.tasks

    private val mutationGate = Mutex()

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            mutationGate.withLock { block() }
        }
    }

    fun pauseDownload(taskId: Int) = mutate { downloads.pauseDownload(taskId) }
    fun resumeDownload(taskId: Int) = mutate { downloads.resumeDownload(taskId) }
    fun cancelDownload(taskId: Int) = mutate { downloads.cancelDownload(taskId) }
    fun retryAllFailed() = mutate { downloads.retryAllFailed() }
}
