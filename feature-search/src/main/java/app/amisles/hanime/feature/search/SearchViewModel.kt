package app.amisles.hanime.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: HanimeRepository
) : ViewModel() {

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
            val result = withContext(Dispatchers.IO) {
                repository.searchVideosWithPagination(
                    query = _query.value,
                    genre = _genre.value,
                    sort = _sort.value,
                    page = currentPageNum
                )
            }
            when (result) {
                is AppResult.Success -> {
                    val data = result.data
                    Log.i("SearchViewModel", "Search returned ${data.videos.size} results, page ${data.currentPage}/${data.totalPages}")
                    _videos.value = data.videos
                    _currentPage.value = data.currentPage
                    _totalPages.value = data.totalPages
                    _hasMore.value = data.hasNextPage
                    if (_query.value.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            repository.addSearchHistory(_query.value)
                        }
                    }
                }
                is AppResult.Error -> {
                    Log.e("SearchViewModel", "Search failed: ${result.message}", result.exception)
                    _videos.value = emptyList()
                    _error.value = result.message
                }
                is AppResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        if (_query.value.isEmpty() && _sort.value == null && _genre.value == null) return

        _isLoadingMore.value = true
        currentPageNum++

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.searchVideosWithPagination(
                    query = _query.value,
                    genre = _genre.value,
                    sort = _sort.value,
                    page = currentPageNum
                )
            }
            when (result) {
                is AppResult.Success -> {
                    val data = result.data
                    Log.i("SearchViewModel", "LoadMore returned ${data.videos.size} results, page ${data.currentPage}/${data.totalPages}")
                    if (data.videos.isNotEmpty()) {
                        _videos.value = _videos.value + data.videos
                    }
                    _currentPage.value = data.currentPage
                    _totalPages.value = data.totalPages
                    _hasMore.value = data.hasNextPage
                }
                is AppResult.Error -> {
                    Log.e("SearchViewModel", "LoadMore failed: ${result.message}", result.exception)
                    _hasMore.value = false
                }
                is AppResult.Loading -> {}
            }
            _isLoadingMore.value = false
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
