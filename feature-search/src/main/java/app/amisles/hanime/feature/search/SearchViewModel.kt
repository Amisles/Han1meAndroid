package app.amisles.hanime.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.CancellationException
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
    // 搜索页既要读站点内容，也要读写本地搜索历史 —— 两个窄接口分别注入
    private val content: ContentRepository,
    private val library: LibraryRepository
) : ViewModel() {

    /**
     * 已注入过的导航入参指纹。
     *
     * 用 ViewModel 而非 Composable 的 LaunchedEffect(Unit) 记账：从详情页返回时
     * 搜索页会重新进入组合，LaunchedEffect(Unit) 随之再次执行，把用户后来输入的
     * 关键词覆盖回导航参数里的旧值。
     *
     * 记录**参数内容**而非布尔标记：带参导航若命中 launchSingleTop，会复用同一个
     * NavBackStackEntry 与其 ViewModel，布尔标记会让新参数被当成「已注入过」而忽略，
     * 表现为再次点大图时搜索框不填充。按内容去重则两种情况都正确：
     * 参数未变（返回页面）不重复注入，参数变了（新的点按）重新注入。
     */
    private var appliedArgsFingerprint: String? = null

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _genre = MutableStateFlow<String?>(null)
    val genre: StateFlow<String?> = _genre.asStateFlow()

    private val _sort = MutableStateFlow<String?>(null)
    val sort: StateFlow<String?> = _sort.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _broad = MutableStateFlow(false)
    val broad: StateFlow<Boolean> = _broad.asStateFlow()

    private val _date = MutableStateFlow("")
    val date: StateFlow<String> = _date.asStateFlow()

    private val _duration = MutableStateFlow("")
    val duration: StateFlow<String> = _duration.asStateFlow()

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

    // null 表示历史尚未从数据库读出：与「读完后确实为空」区分，避免进页面时先闪一下空态提示
    private val _searchHistory = MutableStateFlow<List<String>?>(null)
    val searchHistory: StateFlow<List<String>?> = _searchHistory.asStateFlow()

    private var currentPageNum = 1

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    // 请求世代号：新一轮搜索/加载更多会递增，使被取消的旧任务的 finally 不再触碰加载态。
    // 旧任务取消是异步的，其 finally 可能在新任务已把 _isLoading 置 true 之后才执行。
    private var searchEpoch = 0
    private var loadMoreEpoch = 0

    init {
        viewModelScope.launch {
            try {
                library.getSearchHistory().collect { queries ->
                    _searchHistory.value = queries
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 搜索历史读取失败（如数据库损坏）不应让搜索页崩溃；按「无历史」收口，避免一直停在未加载态
                AppLogger.e("SearchViewModel", "读取搜索历史失败: ${e.message}", e)
                _searchHistory.value = emptyList()
            }
        }
    }

    /**
     * 应用导航入参（关键词 / 分类 / 排序），每次导航意图只生效一次。
     *
     * @param token 导航意图标识，由调用方为每次点按生成新值。它让「同参数的新一次点按」
     *   与「从详情页返回」可以区分：前者 token 变化需要重新注入，后者不变应跳过。
     * @return true 表示本次调用实际注入了初值，false 表示此前已注入过、本次忽略。
     */
    fun applyInitialArgs(
        keyword: String?,
        genre: String?,
        sort: String?,
        token: String? = null
    ): Boolean {
        val fingerprint = "$token|$keyword|$genre|$sort"
        if (appliedArgsFingerprint == fingerprint) return false
        appliedArgsFingerprint = fingerprint

        val cleaned = keyword?.trimStart('#')?.trim().orEmpty()
        // 无关键词时也要显式清空：否则复用条目场景下，上一次的关键词会残留在输入框，
        // 与「点大图只想按标题搜索」的意图不符。
        _query.value = cleaned
        _genre.value = genre?.takeIf { it.isNotBlank() }
        _sort.value = sort?.takeIf { it.isNotBlank() }
        if (hasAnyCriteria()) executeSearch()
        return true
    }

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
            resetSearch()
        }
    }

    /** 提交一次搜索（搜索按钮 / 输入法回车 / 历史记录 / 初值注入共用）；关键词未变则直接重搜，避免同一关键词连发两次请求。 */
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

    /** 设置标签搜索条件（多选）；仅更新状态，由调用方在面板「应用」时统一提交，避免每次勾选都发请求。 */
    fun setTags(newTags: List<String>) {
        _tags.value = newTags
    }

    /** 设置广泛匹配开关；与 [setTags] 同样只更新状态，由「应用」动作统一提交。 */
    fun setBroad(newBroad: Boolean) {
        _broad.value = newBroad
    }

    /** 设置发布日期筛选值（官网原值，空串 = 全部）；单值筛选，变更后立即提交搜索。 */
    fun setDate(newDate: String) {
        if (_date.value != newDate) {
            _date.value = newDate
            resetSearch()
        }
    }

    /** 设置时长筛选值（官网原值，空串 = 全部）；单值筛选，变更后立即提交搜索。 */
    fun setDuration(newDuration: String) {
        if (_duration.value != newDuration) {
            _duration.value = newDuration
            resetSearch()
        }
    }

    /** 一键清除全部筛选条件（分类 / 标签 / 广泛匹配 / 发布日期 / 时长），只发起一次搜索。 */
    fun clearFilters() {
        val hasFilter = _genre.value != null || _tags.value.isNotEmpty() ||
            _broad.value || _date.value.isNotEmpty() || _duration.value.isNotEmpty()
        if (!hasFilter) return
        _genre.value = null
        _tags.value = emptyList()
        _broad.value = false
        _date.value = ""
        _duration.value = ""
        resetSearch()
    }

    /** 是否存在任一有效搜索条件（关键词 / 分类 / 排序 / 标签 / 发布日期 / 时长）。 */
    private fun hasAnyCriteria(): Boolean =
        _query.value.isNotEmpty() ||
            _sort.value != null ||
            _genre.value != null ||
            _tags.value.isNotEmpty() ||
            _date.value.isNotEmpty() ||
            _duration.value.isNotEmpty()

    fun resetSearch() {
        currentPageNum = 1
        _currentPage.value = 1
        _totalPages.value = 1
        _hasMore.value = true
        _error.value = null
        if (hasAnyCriteria()) {
            // 保留上一次结果直到新结果返回，由界面在顶部显示细进度条，避免列表清空后闪成空 spinner
            executeSearch()
        } else {
            _videos.value = emptyList()
        }
    }

    fun executeSearch() {
        if (!hasAnyCriteria()) return

        val epoch = ++searchEpoch
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _isLoadingMore.value = false
        _isLoading.value = true
        _error.value = null
        currentPageNum = 1
        _currentPage.value = 1
        _hasMore.value = true

        searchJob = viewModelScope.launch {
            // 快照本轮请求的页码：协程挂起期间 currentPageNum 可能被 loadMore 改动，
            // 直接读共享变量会发出错误的页码。
            val requestPage = 1
            try {
                val result = withContext(Dispatchers.IO) {
                    content.searchVideosWithPagination(
                        query = _query.value,
                        genre = _genre.value,
                        sort = _sort.value,
                        page = requestPage,
                        tags = _tags.value,
                        broad = _broad.value,
                        date = _date.value,
                        duration = _duration.value
                    )
                }
                when (result) {
                    is AppResult.Success -> {
                        val data = result.data
                        // 去重：同一 id 重复会与 LazyColumn 的 key 冲突并崩溃
                        _videos.value = data.videos.distinctBy { it.id }
                        _currentPage.value = data.currentPage
                        _totalPages.value = data.totalPages
                        _hasMore.value = data.hasNextPage
                        if (_query.value.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                library.addSearchHistory(_query.value)
                            }
                        }
                    }
                    is AppResult.Error -> {
                        _videos.value = emptyList()
                        _error.value = result.message
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                // 新一轮搜索取消旧任务时必须原样抛出；加载态由新一轮搜索接管，不在此复位
                throw e
            } catch (e: Exception) {
                // 解析/URL 构建等运行期异常若逃逸出 viewModelScope 会直接崩溃，且会跳过加载态复位
                AppLogger.e("SearchViewModel", "搜索失败: ${e.message}", e)
                _videos.value = emptyList()
                _error.value = e.message ?: "搜索失败"
            } finally {
                // 仅当本任务仍是最新一轮搜索时才复位，避免被取消的旧任务误清新一轮的加载态
                if (epoch == searchEpoch) _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        if (!hasAnyCriteria()) return

        val epoch = ++loadMoreEpoch
        loadMoreJob?.cancel()
        _isLoadingMore.value = true
        // 局部页码：不再共享改写 currentPageNum。原实现在失败路径 currentPageNum--，若期间用户
        // 已触发新一轮搜索（resetSearch 把它置 1），这个 -- 就会把页码减成 0，后续每一页都从 0 请求。
        val pageToLoad = currentPageNum + 1

        loadMoreJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    content.searchVideosWithPagination(
                        query = _query.value,
                        genre = _genre.value,
                        sort = _sort.value,
                        page = pageToLoad,
                        tags = _tags.value,
                        broad = _broad.value,
                        date = _date.value,
                        duration = _duration.value
                    )
                }
                when (result) {
                    is AppResult.Success -> {
                        val data = result.data
                        if (data.videos.isNotEmpty()) {
                            // 分页追加必须去重：站点结果集在两次请求间漂移时会重复返回，重复 key 会抛异常
                            _videos.value = (_videos.value + data.videos).distinctBy { it.id }
                        }
                        // 只有仍属于当前这一轮搜索时才推进共享游标
                        if (epoch == loadMoreEpoch) currentPageNum = pageToLoad
                        _currentPage.value = data.currentPage
                        _totalPages.value = data.totalPages
                        _hasMore.value = data.hasNextPage
                    }
                    is AppResult.Error -> {
                        _hasMore.value = true
                        // 记录失败原因。注意：SearchScreen 仅在 videos 为空时渲染 error，
                        // 而分页失败时列表非空，故此处暂无可见提示（属既有 UI 缺口，未在本次修复范围内）
                        _error.value = result.message
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchViewModel", "加载更多失败: ${e.message}", e)
                // 不触碰共享游标：本轮失败只是没加载下一页，下一轮仍从同一页继续
                _hasMore.value = true
                _error.value = e.message ?: "加载更多失败"
            } finally {
                if (epoch == loadMoreEpoch) _isLoadingMore.value = false
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    library.clearSearchHistory()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchViewModel", "Error clearing search history: ${e.message}", e)
            }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    library.removeSearchHistory(query)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchViewModel", "Error removing search history: ${e.message}", e)
            }
        }
    }
}
