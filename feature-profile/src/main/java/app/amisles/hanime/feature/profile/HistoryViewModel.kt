package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: LibraryRepository
) : ViewModel() {

    private val _history = MutableStateFlow<List<WatchHistory>>(emptyList())
    val history: StateFlow<List<WatchHistory>> = _history.asStateFlow()

    // 页面进入即加载，初值置 true：否则首帧会先渲染「暂无记录」再切加载圈
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 一次性提示（字符串资源 id）：本地库写入失败时由界面弹 toast。 */
    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    /** 写失败统一上报：列表会刷新回滚，但用户需要知道「为什么条目还在」。 */
    private fun reportWriteResult(success: Boolean) {
        if (!success) _messages.trySend(R.string.local_save_failed)
    }

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
                val ok = withContext(Dispatchers.IO) {
                    repository.removeWatchHistory(videoId)
                }
                loadHistory()
                reportWriteResult(ok)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("HistoryViewModel", "Error removing history: ${e.message}", e)
            }
        }
    }

    fun removeHistories(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        AppLogger.d("HistoryViewModel", "removeHistories called, count: ${videoIds.size}")

        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    // 单条 SQL 批量删除，避免逐条事务
                    repository.removeWatchHistories(videoIds)
                }
                loadHistory()
                reportWriteResult(ok)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("HistoryViewModel", "Error removing histories: ${e.message}", e)
            }
        }
    }

    fun clearHistory() {
        AppLogger.d("HistoryViewModel", "clearHistory called")

        viewModelScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    repository.clearWatchHistory()
                }
                loadHistory()
                reportWriteResult(ok)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("HistoryViewModel", "Error clearing history: ${e.message}", e)
            }
        }
    }
}
