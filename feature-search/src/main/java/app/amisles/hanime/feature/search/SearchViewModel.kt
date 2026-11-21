package app.amisles.hanime.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.SearchFilterState
import app.amisles.hanime.domain.repository.SearchFilterRepository
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.util.AppSnackbar
import app.amisles.hanime.domain.remote.CloudflareChallengeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private data class SearchCriteria(
    val query: String,
    val genre: String?,
    val sort: String?,
    val tags: List<String>,
    val broad: Boolean,
    val date: String,
    val duration: String
) {
    val hasAny: Boolean
        get() = query.isNotEmpty() || genre != null || sort != null || tags.isNotEmpty() ||
            date.isNotEmpty() || duration.isNotEmpty()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    // 搜索页既要读站点内容，也要读写本地搜索历史 —— 两个窄接口分别注入
    private val content: ContentRepository,
    private val library: LibraryRepository,
    private val filters: SearchFilterRepository,
    @ApplicationContext private val context: Context
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

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    // 请求世代号：新一轮搜索/加载更多会递增，使被取消的旧任务的 finally 不再触碰加载态。
    // 旧任务取消是异步的，其 finally 可能在新任务已把 _state.isLoading 置 true 之后才执行。
    private var searchEpoch = 0
    private var loadMoreEpoch = 0

    init {
        restoreFilters()
        viewModelScope.launch {
            try {
                library.getSearchHistory().collect { queries ->
                    _state.update { it.copy(searchHistory = queries) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 搜索历史读取失败（如数据库损坏）不应让搜索页崩溃；按「无历史」收口，避免一直停在未加载态
                AppLogger.e("SearchViewModel", "读取搜索历史失败: ${e.message}", e)
                _state.update { it.copy(searchHistory = emptyList()) }
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
        _state.update { current ->
            // 无关键词时也要显式清空：否则复用条目场景下，上一次的关键词会残留在输入框，
            // 与「点大图只想按标题搜索」的意图不符。
            val injectFilters = cleaned.isNotEmpty() || !genre.isNullOrBlank() || !sort.isNullOrBlank()
            current.copy(
                query = cleaned,
                genre = if (injectFilters) genre?.takeIf { it.isNotBlank() } else current.genre,
                sort = if (injectFilters) sort?.takeIf { it.isNotBlank() } else current.sort
            )
        }
        persistFilters()
        if (hasAnyCriteria()) executeSearch()
        return true
    }

    fun setQuery(newQuery: String) {
        if (_state.value.query != newQuery) {
            _state.update { it.copy(query = newQuery) }
            resetSearch()
        }
    }

    /** 提交一次搜索（搜索按钮 / 输入法回车 / 历史记录点击共用）；关键词未变则直接重搜，否则经 setQuery 触发新搜索。初值注入不走本函数，由 applyInitialArgs 独立处理并按参数指纹去重。 */
    fun submitSearch(query: String) {
        if (_state.value.query == query) executeSearch() else setQuery(query)
    }

    fun setGenre(newGenre: String?) {
        if (_state.value.genre != newGenre) {
            _state.update { it.copy(genre = newGenre) }
            persistFilters()
            resetSearch()
        }
    }

    fun setSort(newSort: String?) {
        if (_state.value.sort != newSort) {
            _state.update { it.copy(sort = newSort) }
            persistFilters()
            resetSearch()
        }
    }

    /** 设置标签搜索条件（多选）；仅更新状态，由调用方在面板「应用」时统一提交，避免每次勾选都发请求。 */
    fun setTags(newTags: List<String>) {
        _state.update { it.copy(tags = newTags) }
        persistFilters()
    }

    /** 设置广泛匹配开关；与 [setTags] 同样只更新状态，由「应用」动作统一提交。 */
    fun setBroad(newBroad: Boolean) {
        _state.update { it.copy(broad = newBroad) }
        persistFilters()
    }

    /**
     * 点选搜索建议里的标签：该标签作为唯一条件重搜，只发一次请求。
     *
     * 关键词一并清空 —— 建议里的标签本来就是「按输入匹配出来的」，输入内容多半只是标签名的前缀，
     * 再拿它当关键词做 AND 只会搜空；分类 / 日期 / 时长同理，叠加旧条件都不是用户的这次意图。
     */
    fun submitTagSuggestion(tagValue: String) {
        _state.update {
            it.copy(
                query = "",
                genre = null,
                tags = listOf(tagValue),
                broad = false,
                date = "",
                duration = ""
            )
        }
        persistFilters()
        resetSearch()
    }

    /** 设置发布日期筛选值（官网原值，空串 = 全部）；单值筛选，变更后立即提交搜索。 */
    fun setDate(newDate: String) {
        if (_state.value.date != newDate) {
            _state.update { it.copy(date = newDate) }
            persistFilters()
            resetSearch()
        }
    }

    /** 设置时长筛选值（官网原值，空串 = 全部）；单值筛选，变更后立即提交搜索。 */
    fun setDuration(newDuration: String) {
        if (_state.value.duration != newDuration) {
            _state.update { it.copy(duration = newDuration) }
            persistFilters()
            resetSearch()
        }
    }

    /** 一键清除全部筛选条件（分类 / 标签 / 广泛匹配 / 发布日期 / 时长），只发起一次搜索。 */
    fun clearFilters() {
        val current = _state.value
        val hasFilter = current.genre != null || current.tags.isNotEmpty() ||
            current.broad || current.date.isNotEmpty() || current.duration.isNotEmpty()
        if (!hasFilter) return
        _state.update {
            it.copy(genre = null, tags = emptyList(), broad = false, date = "", duration = "")
        }
        persistFilters()
        resetSearch()
    }

    private fun restoreFilters() {
        val stored = filters.load()
        _state.update {
            it.copy(
                genre = stored.genre,
                sort = stored.sort,
                tags = stored.tags,
                broad = stored.broad,
                date = stored.date,
                duration = stored.duration
            )
        }
    }

    private fun persistFilters() {
        val state = _state.value
        filters.save(
            SearchFilterState(
                genre = state.genre,
                sort = state.sort,
                tags = state.tags,
                broad = state.broad,
                date = state.date,
                duration = state.duration
            )
        )
    }

    private fun SearchUiState.toCriteria() = SearchCriteria(
        query = query,
        genre = genre,
        sort = sort,
        tags = tags,
        broad = broad,
        date = date,
        duration = duration
    )

    /** 是否存在任一有效搜索条件（关键词 / 分类 / 排序 / 标签 / 发布日期 / 时长）。 */
    private fun hasAnyCriteria(): Boolean = _state.value.toCriteria().hasAny

    fun resetSearch() {
        _state.update {
            it.copy(
                currentPage = 1,
                totalPages = 1,
                hasMore = true,
                error = null,
                loadMoreError = false
            )
        }
        if (hasAnyCriteria()) {
            // 保留上一次结果直到新结果返回，由界面在顶部显示细进度条，避免列表清空后闪成空 spinner
            executeSearch()
        } else {
            _state.update { it.copy(videos = emptyList()) }
        }
    }

    fun executeSearch() {
        val criteria = _state.value.toCriteria()
        if (!criteria.hasAny) return

        val epoch = ++searchEpoch
        // 同步失效在途 loadMore：新一轮搜索后，旧条件的加载更多结果不得再推进游标或触碰加载态
        loadMoreEpoch++
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _state.update {
            it.copy(
                isLoadingMore = false,
                isLoading = true,
                error = null,
                loadMoreError = false,
                challengeHost = null,
                currentPage = 1,
                totalPages = 1,
                hasMore = true,
                // 滚动位置由界面代数变化回顶；此时旧结果仍显示、顶部细进度条提示加载中
                searchGeneration = it.searchGeneration + 1
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    content.searchVideosWithPagination(
                        query = criteria.query,
                        genre = criteria.genre,
                        sort = criteria.sort,
                        page = 1,
                        tags = criteria.tags,
                        broad = criteria.broad,
                        date = criteria.date,
                        duration = criteria.duration
                    )
                }
                if (epoch != searchEpoch) return@launch
                when (result) {
                    is AppResult.Success -> {
                        val data = result.data
                        _state.update {
                            it.copy(
                                // 去重：同一 id 重复会与 LazyColumn 的 key 冲突并崩溃
                                videos = data.videos.distinctBy { video -> video.id },
                                currentPage = data.currentPage,
                                totalPages = data.totalPages,
                                hasMore = data.hasNextPage
                            )
                        }
                        if (criteria.query.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                library.addSearchHistory(criteria.query)
                            }
                        }
                    }
                    is AppResult.Error -> {
                        val cause = result.exception
                        _state.update {
                            it.copy(
                                videos = emptyList(),
                                challengeHost = (cause as? CloudflareChallengeException)?.host,
                                error = ErrorMapper.message(
                                    context,
                                    cause,
                                    result.code,
                                    context.getString(R.string.common_load_failed)
                                )
                            )
                        }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                // 新一轮搜索取消旧任务时必须原样抛出；加载态由新一轮搜索接管，不在此复位
                throw e
            } catch (e: Exception) {
                // 解析/URL 构建等运行期异常若逃逸出 viewModelScope 会直接崩溃，且会跳过加载态复位
                AppLogger.e("SearchViewModel", "搜索失败: ${e.message}", e)
                if (epoch == searchEpoch) {
                    _state.update {
                        it.copy(
                            videos = emptyList(),
                            challengeHost = (e as? CloudflareChallengeException)?.host,
                            error = ErrorMapper.message(
                                context,
                                e,
                                context.getString(R.string.common_load_failed)
                            )
                        )
                    }
                }
            } finally {
                // 仅当本任务仍是最新一轮搜索时才复位，避免被取消的旧任务误清新一轮的加载态
                if (epoch == searchEpoch) _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadMore() {
        // 新一轮搜索进行中禁止追加：此时 hasMore 已被重置为 true、旧结果仍在屏上，
        // 不设防会用「新条件 + 旧列表位置」发出错页请求，与新搜索第 1 页竞速时会把游标
        // 推进到 2 而列表只剩第 1 页，第 2 页永久丢失
        val current = _state.value
        if (current.isLoading) return
        if (current.isLoadingMore || !current.hasMore) return
        val criteria = current.toCriteria()
        if (!criteria.hasAny) return

        val epoch = ++loadMoreEpoch
        loadMoreJob?.cancel()
        _state.update { it.copy(isLoadingMore = true, loadMoreError = false) }
        // 局部页码：不再共享改写 currentPage。原实现在失败路径 currentPage--，若期间用户
        // 已触发新一轮搜索（resetSearch 把它置 1），这个 -- 就会把页码减成 0，后续每一页都从 0 请求。
        val pageToLoad = current.currentPage + 1

        loadMoreJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    content.searchVideosWithPagination(
                        query = criteria.query,
                        genre = criteria.genre,
                        sort = criteria.sort,
                        page = pageToLoad,
                        tags = criteria.tags,
                        broad = criteria.broad,
                        date = criteria.date,
                        duration = criteria.duration
                    )
                }
                if (epoch != loadMoreEpoch) return@launch
                when (result) {
                    is AppResult.Success -> {
                        val data = result.data
                        _state.update {
                            it.copy(
                                videos = if (data.videos.isEmpty()) {
                                    it.videos
                                } else {
                                    // 分页追加必须去重：站点结果集在两次请求间漂移时会重复返回，重复 key 会抛异常
                                    (it.videos + data.videos).distinctBy { video -> video.id }
                                },
                                currentPage = pageToLoad,
                                totalPages = data.totalPages,
                                hasMore = data.hasNextPage
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _state.update { it.copy(hasMore = true, loadMoreError = true) }
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SearchViewModel", "加载更多失败: ${e.message}", e)
                if (epoch == loadMoreEpoch) {
                    _state.update { it.copy(hasMore = true, loadMoreError = true) }
                }
            } finally {
                if (epoch == loadMoreEpoch) _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            try {
                val captured = _state.value.searchHistory.orEmpty()
                val ok = withContext(Dispatchers.IO) {
                    library.clearSearchHistory()
                }
                if (ok && captured.isNotEmpty()) {
                    AppSnackbar.show(
                        context.getString(R.string.search_history_cleared),
                        context.getString(R.string.common_undo)
                    ) {
                        AppSnackbar.undoScope.launch(Dispatchers.IO) {
                            captured.reversed().forEach { library.addSearchHistory(it) }
                        }
                    }
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
