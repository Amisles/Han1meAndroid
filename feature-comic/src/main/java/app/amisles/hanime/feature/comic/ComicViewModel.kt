package app.amisles.hanime.feature.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.ComicPage
import app.amisles.hanime.domain.model.ComicSection
import app.amisles.hanime.domain.repository.ComicRepository
import app.amisles.hanime.domain.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ComicViewModel @Inject constructor(
    private val comics: ComicRepository
) : ViewModel() {

    private val _sections = MutableStateFlow<List<ComicSection>>(emptyList())
    val sections: StateFlow<List<ComicSection>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        if (_isLoading.value) return
        loadJob?.cancel()
        _isLoading.value = true
        _error.value = null
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { comics.getComics(1) }
                when (result) {
                    is AppResult.Success -> apply(result.data, false)
                    is AppResult.Error -> _error.value = result.message
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("ComicViewModel", "加载漫画失败: " + e.message, e)
                _error.value = e.message ?: "加载漫画失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val next = _currentPage.value + 1
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { comics.getComics(next) }
                when (result) {
                    is AppResult.Success -> apply(result.data, true)
                    is AppResult.Error -> _error.value = result.message
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("ComicViewModel", "加载更多漫画失败: " + e.message, e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun apply(page: ComicPage, append: Boolean) {
        _sections.value = if (append) merge(_sections.value, page.sections) else page.sections
        _currentPage.value = page.currentPage
        _hasMore.value = page.hasNextPage
    }

    /** 分页返回的分区同名时按标题合并条目，保持首次出现的区块顺序。 */
    private fun merge(old: List<ComicSection>, incoming: List<ComicSection>): List<ComicSection> {
        if (old.isEmpty()) return incoming
        val result = old.toMutableList()
        incoming.forEach { section ->
            val index = result.indexOfFirst { it.title == section.title }
            if (index < 0) {
                result += section
            } else {
                val existing = result[index]
                result[index] = existing.copy(
                    items = (existing.items + section.items).distinctBy { it.detailUrl }
                )
            }
        }
        return result
    }
}
