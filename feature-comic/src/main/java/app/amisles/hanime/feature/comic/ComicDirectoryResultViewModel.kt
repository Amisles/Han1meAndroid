package app.amisles.hanime.feature.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.ComicDirectoryResult
import app.amisles.hanime.domain.model.ComicItem
import app.amisles.hanime.domain.model.ComicSort
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
class ComicDirectoryResultViewModel @Inject constructor(
    private val comics: ComicRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<ComicItem>>(emptyList())
    val items: StateFlow<List<ComicItem>> = _items.asStateFlow()

    private val _sort = MutableStateFlow(ComicSort.LATEST)
    val sort: StateFlow<ComicSort> = _sort.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var baseUrl = ""
    private var generation = 0
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    fun start(url: String) {
        if (url.isBlank() || url == baseUrl) return
        baseUrl = url
        reload(_sort.value)
    }

    fun selectSort(sort: ComicSort) {
        if (baseUrl.isBlank() || sort == _sort.value) return
        reload(sort)
    }

    fun retry() {
        if (baseUrl.isBlank()) return
        reload(_sort.value)
    }

    fun loadMore() {
        if (baseUrl.isBlank() || _isLoading.value || _isLoadingMore.value || !_hasMore.value) return
        val requested = _currentPage.value + 1
        val gen = generation
        _isLoadingMore.value = true
        loadMoreJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    comics.getComicDirectoryResult(baseUrl, _sort.value, requested)
                }
                if (gen != generation) return@launch
                when (result) {
                    is AppResult.Success -> apply(result.data, requested)
                    is AppResult.Error -> _error.value = result.message
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    AppLogger.logError("ComicDirectoryResultViewModel", "加载更多漫画失败: " + e.message, e)
                    _error.value = e.message ?: "加载更多漫画失败"
                }
            } finally {
                if (gen == generation) _isLoadingMore.value = false
            }
        }
    }

    private fun reload(sort: ComicSort) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        generation++
        val gen = generation
        _sort.value = sort
        _items.value = emptyList()
        _hasMore.value = false
        _currentPage.value = 0
        _isLoadingMore.value = false
        _error.value = null
        _isLoading.value = true
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    comics.getComicDirectoryResult(baseUrl, sort, 1)
                }
                if (gen != generation) return@launch
                when (result) {
                    is AppResult.Success -> apply(result.data, 1)
                    is AppResult.Error -> _error.value = result.message
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    AppLogger.logError("ComicDirectoryResultViewModel", "加载漫画列表失败: " + e.message, e)
                    _error.value = e.message ?: "加载漫画列表失败"
                }
            } finally {
                if (gen == generation) _isLoading.value = false
            }
        }
    }

    private fun apply(page: ComicDirectoryResult, requested: Int) {
        val incoming = page.items
        val merged = if (requested > 1) (_items.value + incoming).distinctBy { it.id } else incoming
        _items.value = merged
        _currentPage.value = requested
        _hasMore.value = page.hasNextPage && incoming.isNotEmpty()
        _error.value = null
    }
}
