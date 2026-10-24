package app.amisles.hanime.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.CrashLogger
import app.amisles.hanime.core.common.util.CrashRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 崩溃日志页：只做本地读取与导出，不上传任何数据。
 */
@HiltViewModel
class CrashLogViewModel @Inject constructor() : ViewModel() {

    private val _records = MutableStateFlow<List<CrashRecord>>(emptyList())
    val records: StateFlow<List<CrashRecord>> = _records.asStateFlow()

    private val _exportText = MutableStateFlow<String?>(null)
    val exportText: StateFlow<String?> = _exportText.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _records.value = withContext(Dispatchers.IO) { CrashLogger.records() }
        }
    }

    fun prepareExport() {
        viewModelScope.launch {
            _exportText.value = withContext(Dispatchers.IO) { CrashLogger.exportAll() }
        }
    }

    fun consumeExport() {
        _exportText.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { CrashLogger.clear() }
            _records.value = emptyList()
        }
    }
}
