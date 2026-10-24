package app.amisles.hanime.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.DownloadGateState
import app.amisles.hanime.domain.model.DownloadStorageInfo
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloads: DownloadRepository
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> = downloads.tasks

    /** 调度闸门：非空表示正因条件不满足而等待，用于列表顶部提示。 */
    val gateState: StateFlow<DownloadGateState> = downloads.gateState

    private val _storageInfo = MutableStateFlow<DownloadStorageInfo?>(null)
    val storageInfo: StateFlow<DownloadStorageInfo?> = _storageInfo.asStateFlow()

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageInfo.value = runCatching { downloads.getStorageInfo() }.getOrNull()
        }
    }

    fun clearOrphanPartmaps(onDone: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = runCatching { downloads.clearOrphanPartmaps() }.getOrDefault(0L)
            _storageInfo.value = runCatching { downloads.getStorageInfo() }.getOrNull()
            withContext(Dispatchers.Main) { onDone(freed) }
        }
    }

    private val mutationGate = Mutex()

    private fun mutate(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            mutationGate.withLock { block() }
        }
    }

    fun pauseAll() = mutate { downloads.pauseAll() }
    fun resumeAll() = mutate { downloads.resumeAll() }
    fun moveToFront(taskId: Int) = mutate { downloads.moveToFront(taskId) }

    fun pauseDownload(taskId: Int) = mutate { downloads.pauseDownload(taskId) }
    fun resumeDownload(taskId: Int) = mutate { downloads.resumeDownload(taskId) }
    fun cancelDownload(taskId: Int) = mutate { downloads.cancelDownload(taskId) }
    fun retryAllFailed() = mutate { downloads.retryAllFailed() }
}
