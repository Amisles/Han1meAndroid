package app.amisles.hanime.feature.comic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.paging.Paged
import app.amisles.hanime.core.common.paging.Pager
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.ComicItem
import app.amisles.hanime.domain.model.ComicSort
import app.amisles.hanime.domain.repository.ComicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ComicDirectoryResultViewModel @Inject constructor(
    private val comics: ComicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _sort = MutableStateFlow(ComicSort.LATEST)
    val sort: StateFlow<ComicSort> = _sort.asStateFlow()

    private var baseUrl = ""

    private val pager = Pager<ComicItem>(
        scope = viewModelScope,
        fetch = { page ->
            withContext(Dispatchers.IO) {
                comics.getComicDirectoryResult(baseUrl, _sort.value, page)
            }.map { Paged(it.items, it.hasNextPage) }
        },
        describeError = { throwable ->
            ErrorMapper.message(context, throwable, context.getString(R.string.common_load_failed))
        },
        merge = { previous, incoming -> (previous + incoming).distinctBy { it.id } },
        onFailure = { throwable ->
            AppLogger.logError(
                "ComicDirectoryResultViewModel",
                "漫画目录分页失败: " + throwable?.message,
                throwable
            )
        }
    )

    val items = pager.items
    val isLoading = pager.isRefreshing
    val isLoadingMore = pager.isLoadingMore
    val hasMore = pager.hasMore
    val currentPage = pager.currentPage
    val error = pager.error

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
        if (baseUrl.isBlank()) return
        pager.loadMore()
    }

    private fun reload(sort: ComicSort) {
        _sort.value = sort
        pager.reload()
    }
}
