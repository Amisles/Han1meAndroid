package app.amisles.hanime.core.common.paging

import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 一次分页请求的结果：本页条目 + 站点是否还有下一页。 */
data class Paged<T>(
    val items: List<T>,
    val hasNextPage: Boolean,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val meta: Map<String, String> = emptyMap()
)

/**
 * 分页列表的状态机：首屏加载、加载更多、页码游标、失效取消、失败提示各自只实现一次。
 *
 * [merge] 只决定"下一页追加到已有列表"的方式（去重键因实体而异）；[describeError] 把异常
 * 映射为界面文案，分页本身不感知 Android 资源。条件变化（换排序、换搜索词）时调用
 * [reload] 即可作废旧响应的写回。
 */
class Pager<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> AppResult<Paged<T>>,
    private val describeError: (Throwable?) -> String,
    private val merge: (previous: List<T>, incoming: List<T>) -> List<T> = { previous, incoming -> previous + incoming },
    private val onFailure: (Throwable?) -> Unit = {},
    private val onPage: (Paged<T>) -> Unit = {},
    private val keepItemsOnReload: Boolean = false,
    private val resolveHasMore: (Paged<T>, previous: List<T>, merged: List<T>) -> Boolean =
        { page, _, _ -> page.hasNextPage && page.items.isNotEmpty() }
) {

    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loadMoreError = MutableStateFlow(false)
    val loadMoreError: StateFlow<Boolean> = _loadMoreError.asStateFlow()

    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private var generationCounter = 0
    private var refreshJob: Job? = null
    private var moreJob: Job? = null
    private var reloadSuccess: ((Paged<T>) -> Unit)? = null

    fun reload(onSuccess: (Paged<T>) -> Unit = {}) {
        reloadSuccess = onSuccess
        refreshJob?.cancel()
        moreJob?.cancel()
        val gen = ++generationCounter
        _generation.value = gen
        if (!keepItemsOnReload) _items.value = emptyList()
        _hasMore.value = false
        _currentPage.value = if (keepItemsOnReload) 1 else 0
        _totalPages.value = 0
        _isLoadingMore.value = false
        _loadMoreError.value = false
        _error.value = null
        _isRefreshing.value = true
        refreshJob = scope.launch {
            try {
                val result = fetch(1)
                if (gen != generationCounter) return@launch
                when (result) {
                    is AppResult.Success -> apply(result.data, 1)
                    is AppResult.Error -> {
                        onFailure(result.exception)
                        _error.value = describeError(result.exception)
                    }
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generationCounter) {
                    onFailure(e)
                    _error.value = describeError(e)
                }
            } finally {
                if (gen == generationCounter) {
                    _isRefreshing.value = false
                    reloadSuccess = null
                }
            }
        }
    }

    fun loadMore() {
        if (_isRefreshing.value || _isLoadingMore.value || !_hasMore.value) return
        val requested = _currentPage.value + 1
        val gen = generationCounter
        _isLoadingMore.value = true
        _loadMoreError.value = false
        moreJob = scope.launch {
            try {
                val result = fetch(requested)
                if (gen != generationCounter) return@launch
                when (result) {
                    is AppResult.Success -> apply(result.data, requested)
                    is AppResult.Error -> {
                        onFailure(result.exception)
                        _error.value = describeError(result.exception)
                        _loadMoreError.value = true
                    }
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generationCounter) {
                    onFailure(e)
                    _error.value = describeError(e)
                    _loadMoreError.value = true
                }
            } finally {
                if (gen == generationCounter) _isLoadingMore.value = false
            }
        }
    }

    fun clear() {
        refreshJob?.cancel()
        moreJob?.cancel()
        generationCounter++
        _generation.value = generationCounter
        _items.value = emptyList()
        _currentPage.value = 0
        _totalPages.value = 0
        _hasMore.value = false
        _error.value = null
        _loadMoreError.value = false
        _isRefreshing.value = false
        _isLoadingMore.value = false
    }

    private fun apply(page: Paged<T>, requested: Int) {
        val previous = _items.value
        val merged = if (requested > 1) merge(previous, page.items) else page.items
        _items.value = merged
        _currentPage.value = if (page.currentPage > 0) page.currentPage else requested
        _totalPages.value = page.totalPages
        _hasMore.value = resolveHasMore(page, previous, merged)
        _loadMoreError.value = false
        _error.value = null
        onPage(page)
        if (requested == 1) {
            reloadSuccess?.invoke(page)
            reloadSuccess = null
        }
    }
}
