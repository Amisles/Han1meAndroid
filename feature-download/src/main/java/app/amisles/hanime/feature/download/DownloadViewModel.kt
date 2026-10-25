package app.amisles.hanime.feature.download

import android.content.Context
import android.net.Uri
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

/** 迁移包读入上限：与解压后的条目上限同量级，正常包只有几百 KB。 */
private const val MAX_MIGRATION_BYTES = 64L * 1024 * 1024

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

    /** 权限授予后重新扫描：把上一个安装留在公共目录里的成品补进列表。 */
    fun rescanDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { downloads.rescanExistingDownloads() }
            refreshStorageInfo()
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

    private val _migrationBytes = MutableStateFlow<ByteArray?>(null)
    /** 待写入的迁移包；由界面取出后写入用户选定的位置，写完调用 [consumeMigrationBytes]。 */
    val migrationBytes: StateFlow<ByteArray?> = _migrationBytes.asStateFlow()

    fun consumeMigrationBytes() {
        _migrationBytes.value = null
    }

    fun exportMigration(onEmpty: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = runCatching { downloads.exportMigration() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (bytes == null) onEmpty() else _migrationBytes.value = bytes
            }
        }
    }

    /** 把已导出的迁移包写到用户选定的位置；写完清空内存中的副本。 */
    fun writeMigrationTo(uri: Uri, context: Context) {
        val bytes = _migrationBytes.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            withContext(Dispatchers.Main) { consumeMigrationBytes() }
        }
    }

    fun readMigrationFrom(uri: Uri, context: Context, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // 限制读入量：SAF 的 MIME 过滤挡不住用户选错文件，一次性读整个视频会 OOM
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_MIGRATION_BYTES) error("迁移包过大")
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            }.getOrNull()
            val count = if (bytes == null) {
                0
            } else {
                runCatching { downloads.importMigration(bytes) }.getOrDefault(0)
            }
            withContext(Dispatchers.Main) { onDone(count) }
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
