package app.amisles.hanime.feature.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.UiConfigImport
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.data.backup.BackupManager
import app.amisles.hanime.domain.model.BackupConflictPolicy
import app.amisles.hanime.domain.model.BackupError
import app.amisles.hanime.domain.model.BackupManifest
import app.amisles.hanime.domain.repository.BackupRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PendingBackup(val manifest: BackupManifest, val bytes: ByteArray)

sealed interface BackupMessage {
    data class Text(val resId: Int, val arg: String? = null) : BackupMessage
    data class Imported(val languageChanged: Boolean = false) : BackupMessage
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    app: Application,
    private val backup: BackupRepository,
    private val settings: SettingsRepository
) : AndroidViewModel(app) {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _pending = MutableStateFlow<PendingBackup?>(null)
    val pending: StateFlow<PendingBackup?> = _pending.asStateFlow()

    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "hanime-backup-" + stamp + ".zip"
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun exportTo(uri: Uri) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    backup.exportBackup(mapOf(BackupManager.ENTRY_UI_CONFIG to UiConfigStore.exportToText()))
                }
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法写入所选位置")
                }
            }
            _isBusy.value = false
            _message.value = result.fold(
                onSuccess = { BackupMessage.Text(R.string.backup_export_done) },
                onFailure = { e ->
                    AppLogger.logError("BackupViewModel", "导出失败: " + e.message, e)
                    BackupMessage.Text(R.string.backup_export_failed)
                }
            )
        }
    }

    fun loadForImport(uri: Uri) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                    PendingBackup(backup.inspectBackup(bytes), bytes)
                }
            }
            _isBusy.value = false
            result.fold(
                onSuccess = { _pending.value = it },
                onFailure = { e ->
                    AppLogger.logError("BackupViewModel", "读取备份失败: " + e.message, e)
                    _message.value = BackupMessage.Text(messageFor(e))
                }
            )
        }
    }

    fun cancelImport() {
        _pending.value = null
    }

    fun confirmImport(policy: BackupConflictPolicy) {
        val current = _pending.value ?: return
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val languageBefore = settings.appLanguage
                    val (contents, summary) = backup.importBackup(current.bytes, policy)
                    contents.uiConfigText?.let { UiConfigStore.importFromText(it) }
                    summary to (settings.appLanguage != languageBefore)
                }
            }
            _isBusy.value = false
            if (result.isSuccess) _pending.value = null
            _message.value = result.fold(
                onSuccess = { (_, languageChanged) -> BackupMessage.Imported(languageChanged) },
                onFailure = { e ->
                    AppLogger.logError("BackupViewModel", "导入失败: " + e.message, e)
                    BackupMessage.Text(messageFor(e))
                }
            )
        }
    }

    private fun messageFor(e: Throwable): Int = when (e) {
        is BackupError.WrongPackage -> R.string.backup_error_package
        is BackupError.UnsupportedSchema -> R.string.backup_error_schema
        is BackupError.MissingManifest, is BackupError.NotASupportedArchive -> R.string.backup_error_format
        else -> R.string.backup_import_failed
    }
}
