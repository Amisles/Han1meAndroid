package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.WatchStats
import app.amisles.hanime.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchStatsViewModel @Inject constructor(
    private val stats: StatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<WatchStats?>(null)
    val state: StateFlow<WatchStats?> = _state.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load() {
        _isLoading.value = true
        viewModelScope.launch {
            _state.value = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { stats.loadWatchStats() }
            }.getOrElse { e ->
                AppLogger.logError("WatchStatsViewModel", "加载统计失败: " + e.message, e)
                null
            }
            _isLoading.value = false
        }
    }
}
