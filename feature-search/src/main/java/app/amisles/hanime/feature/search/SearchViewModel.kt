package app.amisles.hanime.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.SearchResult
import app.amisles.hanime.data.repository.HanimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {
    private val repository = HanimeRepository.getInstance()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _genre = MutableStateFlow<String?>(null)
    val genre: StateFlow<String?> = _genre.asStateFlow()

    private val _sort = MutableStateFlow<String?>(null)
    val sort: StateFlow<String?> = _sort.asStateFlow()

    private val _videos = MutableStateFlow<List<HanimeVideo>>(emptyList())
    val videos: StateFlow<List<HanimeVideo>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var currentPageNum = 1

    init {
        viewModelScope.launch {
            repository.getSearchHistory().collect { entities ->
                _searchHistory.value = entities.map { it.query }
            }
        }
    }

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
            resetSearch()
        }
    }

    fun setGenre(newGenre: String?) {
        Log.i("SearchViewModel", "setGenre called: old='${_genre.value}', new='$newGenre'")
        if (_genre.value != newGenre) {
            _genre.value = newGenre
            resetSearch()
        }
    }

    fun setSort(newSort: String?) {
        Log.i("SearchViewModel", "setSort called: old='${_sort.value}', new='$newSort'")
        if (_sort.value != newSort) {
            _sort.value = newSort
            resetSearch()
        }
    }

    fun resetSearch() {
        Log.i("SearchViewModel", "resetSearch: query='${_query.value}', sort='${_sort.value}', genre='${_genre.value}'")
        currentPageNum = 1
        _currentPage.value = 1
        _totalPages.value = 1
        _videos.value = emptyList()
        _hasMore.value = true
        _error.value = null
        if (_query.value.isNotEmpty() || _sort.value != null || _genre.value != null) {
            executeSearch()
        }
    }

    fun executeSearch() {
        Log.i("SearchViewModel", "executeSearch: query='${_query.value}', sort='${_sort.value}', genre='${_genre.value}'")
        if (_query.value.isEmpty() && _sort.value == null && _genre.value == null) return

        _isLoading.value = true
        _error.value = null
        currentPageNum = 1
        _currentPage.value = 1
        _hasMore.value = true

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.searchVideosWithPagination(
                        query = _query.value,
                        genre = _genre.value,
                        sort = _sort.value,
                        page = currentPageNum
                    )
                }
                Log.i("SearchViewModel", "Search returned ${result.videos.size} results, page ${result.currentPage}/${result.totalPages}")
                _videos.value = result.videos
                _currentPage.value = result.currentPage
                _totalPages.value = result.totalPages
                _hasMore.value = result.hasNextPage
                if (_query.value.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        repository.addSearchHistory(_query.value)
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Search failed: ${e.message}", e)
                _videos.value = emptyList()
                _error.value = e.message ?: "搜索失败，请重试"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        if (_query.value.isEmpty() && _sort.value == null && _genre.value == null) return

        _isLoadingMore.value = true
        currentPageNum++

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.searchVideosWithPagination(
                        query = _query.value,
                        genre = _genre.value,
                        sort = _sort.value,
                        page = currentPageNum
                    )
                }
                Log.i("SearchViewModel", "LoadMore returned ${result.videos.size} results, page ${result.currentPage}/${result.totalPages}")
                if (result.videos.isNotEmpty()) {
                    _videos.value = _videos.value + result.videos
                }
                _currentPage.value = result.currentPage
                _totalPages.value = result.totalPages
                _hasMore.value = result.hasNextPage
            } catch (e: Exception) {
                Log.e("SearchViewModel", "LoadMore failed: ${e.message}", e)
                _hasMore.value = false
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearSearchHistory()
            }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.removeSearchHistory(query)
            }
        }
    }
}