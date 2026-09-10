package app.amisles.hanime.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.result.AppResult
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

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

    /**
     * 提交一次搜索（搜索按钮 / 输入法回车 / 历史记录 / 初值注入共用）。
     *
     * [setQuery] 只在关键词变化时才触发 resetSearch → executeSearch，因此这里要覆盖
     * 「关键词未变但用户仍想重搜」的场景；反之关键词变化时不再额外调一次 executeSearch，
     * 避免同一关键词连续发两次请求（审查 S2）。
     */
    fun submitSearch(query: String) {
        if (_query.value == query) executeSearch() else setQuery(query)
    }

    fun setGenre(newGenre: String?) {
        if (_genre.value != newGenre) {
            _genre.value = newGenre
            resetSearch()
        }
    }

    fun setSort(newSort: String?) {
        if (_sort.value != newSort) {
            _sort.value = newSort
            resetSearch()
        }
    }

    fun resetSearch() {
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
        if (_query.value.isEmpty() && _sort.value == null && _genre.value == null) return

        searchJob?.cancel()
        loadMoreJob?.cancel()
        _isLoadingMore.value = false
        _isLoading.value = true
        _error.value = null
        currentPageNum = 1
        _currentPage.value = 1
        _hasMore.value = true

        searchJob = viewModelScope.launch {
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
                    // 去重：同一 id 重复出现会与 LazyColumn 的 key 冲突并直接崩溃
                    _videos.value = data.videos.distinctBy { it.id }
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

        loadMoreJob?.cancel()
        _isLoadingMore.value = true
        currentPageNum++

        loadMoreJob = viewModelScope.launch {
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
                    if (data.videos.isNotEmpty()) {
                        // 分页追加必须去重：站点结果集在两次请求之间漂移时会重复返回已有条目，
                        // 而列表以 id 作为 Lazy key，重复 key 会抛 IllegalArgumentException 崩溃
                        _videos.value = (_videos.value + data.videos).distinctBy { it.id }
                    }
                    _currentPage.value = data.currentPage
                    _totalPages.value = data.totalPages
                    _hasMore.value = data.hasNextPage
                }
                is AppResult.Error -> {
                    currentPageNum--
                    _hasMore.value = true
                }
                is AppResult.Loading -> {}
            }
            _isLoadingMore.value = false
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.clearSearchHistory()
                }
            } catch (e: Exception) {
                AppLogger.e("SearchViewModel", "Error clearing search history: ${e.message}", e)
            }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.removeSearchHistory(query)
                }
            } catch (e: Exception) {
                AppLogger.e("SearchViewModel", "Error removing search history: ${e.message}", e)
            }
        }
    }
}
