package app.amisles.hanime.feature.profile

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HanimeRepository
) : ViewModel() {

    private val _history = MutableStateFlow<List<WatchHistory>>(emptyList())
    val history: StateFlow<List<WatchHistory>> = _history.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadHistory() {
        AppLogger.d("HistoryViewModel", "loadHistory called")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    repository.getAllWatchHistoryFlow().first()
                }
                _history.value = history
                AppLogger.d("HistoryViewModel", "Got ${history.size} history items")
            } catch (e: IOException) {
                AppLogger.e("HistoryViewModel", "Error loading history: ${e.message}", e)
                _history.value = emptyList()
            } catch (e: SQLiteException) {
                AppLogger.e("HistoryViewModel", "Error loading history: ${e.message}", e)
                _history.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeHistory(videoId: String) {
        AppLogger.d("HistoryViewModel", "removeHistory called, videoId: $videoId")

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.removeWatchHistory(videoId)
                }
                loadHistory()
            } catch (e: IOException) {
                AppLogger.e("HistoryViewModel", "Error removing history: ${e.message}", e)
            }
        }
    }

    fun removeHistories(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        AppLogger.d("HistoryViewModel", "removeHistories called, count: ${videoIds.size}")

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    videoIds.forEach { videoId ->
                        repository.removeWatchHistory(videoId)
                    }
                }
                loadHistory()
            } catch (e: IOException) {
                AppLogger.e("HistoryViewModel", "Error removing histories: ${e.message}", e)
            }
        }
    }

    fun clearHistory() {
        AppLogger.d("HistoryViewModel", "clearHistory called")

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.clearWatchHistory()
                }
                loadHistory()
            } catch (e: IOException) {
                AppLogger.e("HistoryViewModel", "Error clearing history: ${e.message}", e)
            }
        }
    }
}
