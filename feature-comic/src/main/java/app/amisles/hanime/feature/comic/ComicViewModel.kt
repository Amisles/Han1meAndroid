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
    private var loadMoreJob: Job? = null

    fun load() {
        if (_isLoading.value) return
        loadJob?.cancel()
        loadMoreJob?.cancel()
        _isLoading.value = true
        _isLoadingMore.value = false
        _error.value = null
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { comics.getComics(1) }
                when (result) {
                    is AppResult.Success -> apply(result.data, 1)
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
        if (_isLoading.value || _isLoadingMore.value || !_hasMore.value) return
        val requested = _currentPage.value + 1
        _isLoadingMore.value = true
        loadMoreJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { comics.getComics(requested) }
                when (result) {
                    is AppResult.Success -> apply(result.data, requested)
                    is AppResult.Error -> _error.value = result.message
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("ComicViewModel", "加载更多漫画失败: " + e.message, e)
                _error.value = e.message ?: "加载更多漫画失败"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun apply(page: ComicPage, requested: Int) {
        val incoming = page.sections.filter { it.items.isNotEmpty() }
        val previous = _sections.value
        val merged = if (requested > 1) merge(previous, incoming) else incoming
        val grew = merged.sumOf { it.items.size } > previous.sumOf { it.items.size }
        _sections.value = merged
        _currentPage.value = requested
        _hasMore.value = page.hasNextPage && incoming.isNotEmpty() && (requested == 1 || grew)
        _error.value = null
    }

    private fun merge(old: List<ComicSection>, incoming: List<ComicSection>): List<ComicSection> {
        if (old.isEmpty()) return incoming
        val result = old.toMutableList()
        val seen = result.flatMapTo(HashSet()) { section -> section.items.map { it.detailUrl } }
        incoming.forEach { section ->
            val fresh = section.items.filter { seen.add(it.detailUrl) }
            if (fresh.isEmpty()) return@forEach
            val index = result.indexOfFirst { it.title == section.title }
            if (index < 0) {
                result += section.copy(items = fresh)
            } else {
                result[index] = result[index].copy(items = result[index].items + fresh)
            }
        }
        return result
    }
}
