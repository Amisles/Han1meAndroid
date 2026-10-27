package app.amisles.hanime.feature.comic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.ComicChapter
import app.amisles.hanime.domain.model.ComicProgress
import app.amisles.hanime.domain.repository.ComicRepository
import app.amisles.hanime.domain.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val SAVE_DEBOUNCE_MS = 400L

data class ComicReaderUiState(
    val comicId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val chapter: ComicChapter? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1
) {
    val pageCount: Int get() = chapter?.pageCount ?: 0
    val hasPages: Boolean get() = pageCount > 0
}

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    private val comics: ComicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ComicReaderUiState())
    val state: StateFlow<ComicReaderUiState> = _state.asStateFlow()

    // 进度落库独立于 viewModelScope：onCleared 时 viewModelScope 已取消，最后一次进度仍需写入
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingSave: Job? = null
    private var loadJob: Job? = null

    fun load(comicId: String, startPage: Int, title: String, coverUrl: String) {
        if (_state.value.comicId == comicId && _state.value.chapter != null) return
        _state.value = _state.value.copy(
            comicId = comicId,
            title = title,
            coverUrl = coverUrl,
            isLoading = true,
            error = null
        )
        fetch(comicId, startPage)
    }

    fun retry() {
        val current = _state.value
        if (current.comicId.isBlank()) return
        _state.value = current.copy(isLoading = true, error = null)
        fetch(current.comicId, current.currentPage)
    }

    private fun fetch(comicId: String, startPage: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    comics.getComicChapter(comicId, startPage.coerceAtLeast(1))
                }
                when (result) {
                    is AppResult.Success -> {
                        val chapter = result.data
                        val page = startPage.coerceIn(1, chapter.pageCount.coerceAtLeast(1))
                        _state.value = _state.value.copy(
                            chapter = chapter,
                            currentPage = page,
                            error = null
                        )
                        scheduleSave(page)
                    }

                    is AppResult.Error -> _state.value = _state.value.copy(error = result.message)
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("ComicReaderViewModel", "加载漫画失败: " + e.message, e)
                _state.value = _state.value.copy(
                    error = e.message ?: context.getString(R.string.common_load_failed)
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun onPageChanged(page: Int) {
        val current = _state.value
        if (!current.hasPages) return
        val clamped = page.coerceIn(1, current.pageCount)
        if (clamped == current.currentPage) return
        _state.value = current.copy(currentPage = clamped)
        scheduleSave(clamped)
    }

    /** 连续滚动会快速产生大量页码变化，合并成一次写入。 */
    private fun scheduleSave(page: Int) {
        pendingSave?.cancel()
        pendingSave = saveScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(page)
        }
    }

    private suspend fun persist(page: Int) {
        val current = _state.value
        if (current.comicId.isBlank() || !current.hasPages) return
        comics.saveComicProgress(
            ComicProgress(
                comicId = current.comicId,
                title = current.title,
                coverUrl = current.coverUrl,
                pageCount = current.pageCount,
                lastPage = page,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun onCleared() {
        pendingSave?.cancel()
        val current = _state.value
        if (current.comicId.isNotBlank() && current.hasPages) {
            val page = current.currentPage
            saveScope.launch { persist(page) }
        }
        super.onCleared()
    }
}
