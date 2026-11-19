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
data class Paged<T>(val items: List<T>, val hasNextPage: Boolean)

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
    private val onFailure: (Throwable?) -> Unit = {}
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var generation = 0
    private var refreshJob: Job? = null
    private var moreJob: Job? = null

    fun reload() {
        refreshJob?.cancel()
        moreJob?.cancel()
        val gen = ++generation
        _items.value = emptyList()
        _hasMore.value = false
        _currentPage.value = 0
        _isLoadingMore.value = false
        _error.value = null
        _isRefreshing.value = true
        refreshJob = scope.launch {
            try {
                val result = fetch(1)
                if (gen != generation) return@launch
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
                if (gen == generation) {
                    onFailure(e)
                    _error.value = describeError(e)
                }
            } finally {
                if (gen == generation) _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        if (_isRefreshing.value || _isLoadingMore.value || !_hasMore.value) return
        val requested = _currentPage.value + 1
        val gen = generation
        _isLoadingMore.value = true
        moreJob = scope.launch {
            try {
                val result = fetch(requested)
                if (gen != generation) return@launch
                when (result) {
                    is AppResult.Success -> apply(result.data, requested)
                    is AppResult.Error -> {
                        onFailure(result.exception)
                        _error.value = describeError(result.exception)
                    }
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    onFailure(e)
                    _error.value = describeError(e)
                }
            } finally {
                if (gen == generation) _isLoadingMore.value = false
            }
        }
    }

    private fun apply(page: Paged<T>, requested: Int) {
        _items.value = if (requested > 1) merge(_items.value, page.items) else page.items
        _currentPage.value = requested
        _hasMore.value = page.hasNextPage && page.items.isNotEmpty()
        _error.value = null
    }
}
