package app.amisles.hanime.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.SiteProbeResult
import app.amisles.hanime.domain.repository.DiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProbeUiState {
    data object Running : ProbeUiState
    data class Done(val result: SiteProbeResult) : ProbeUiState
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val diagnostics: DiagnosticsRepository
) : ViewModel() {

    private val _probeState = MutableStateFlow<ProbeUiState>(ProbeUiState.Running)
    val probeState: StateFlow<ProbeUiState> = _probeState.asStateFlow()

    init {
        runProbe()
    }

    fun retry() {
        if (_probeState.value is ProbeUiState.Running) return
        runProbe()
    }

    private fun runProbe() {
        viewModelScope.launch {
            _probeState.value = ProbeUiState.Running
            _probeState.value = ProbeUiState.Done(diagnostics.probeSite())
        }
    }
}
