package app.amisles.hanime.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.remote.DiagnosticResult
import app.amisles.hanime.data.remote.DiagnosticStatus
import app.amisles.hanime.data.remote.NetworkDiagnostics
import app.amisles.hanime.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 诊断页 UI 状态
 */
sealed class DiagnosticsUiState {
    /** 初始空闲态 */
    object Idle : DiagnosticsUiState()
    /** 诊断进行中，results 为已完成项（增量更新） */
    data class Running(val results: List<DiagnosticResult>) : DiagnosticsUiState()
    /** 诊断完成 */
    data class Done(val results: List<DiagnosticResult>) : DiagnosticsUiState()
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val diagnostics = NetworkDiagnostics()

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Idle)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    fun runDiagnostics() {
        if (_uiState.value is DiagnosticsUiState.Running) return
        viewModelScope.launch {
            _uiState.value = DiagnosticsUiState.Running(emptyList())
            try {
                val results = diagnostics.runAll()
                _uiState.value = DiagnosticsUiState.Done(results)
            } catch (e: Exception) {
                AppLogger.e("DiagnosticsViewModel", "诊断异常: ${e.message}", e)
                _uiState.value = DiagnosticsUiState.Done(
                    listOf(
                        DiagnosticResult(
                            type = app.amisles.hanime.data.remote.DiagnosticType.CONNECTIVITY,
                            status = DiagnosticStatus.FAIL,
                            title = context.getString(R.string.diagnostics_error_title),
                            detail = context.getString(R.string.diagnostics_error_detail, e.message),
                            suggestion = context.getString(R.string.diagnostics_error_suggestion)
                        )
                    )
                )
            }
        }
    }
}
