package app.amisles.hanime.feature.comic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.paging.Paged
import app.amisles.hanime.core.common.paging.Pager
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.ComicSection
import app.amisles.hanime.domain.repository.ComicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class ComicViewModel @Inject constructor(
    private val comics: ComicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val pager = Pager<ComicSection>(
        scope = viewModelScope,
        fetch = { page ->
            withContext(Dispatchers.IO) { comics.getComics(page) }
                .map { result ->
                    Paged(
                        items = result.sections.filter { it.items.isNotEmpty() },
                        hasNextPage = result.hasNextPage,
                        currentPage = result.currentPage,
                        totalPages = result.totalPages
                    )
                }
        },
        describeError = { throwable ->
            ErrorMapper.message(context, throwable, context.getString(R.string.common_load_failed))
        },
        merge = ::mergeSections,
        onFailure = { throwable ->
            AppLogger.logError("ComicViewModel", "加载漫画失败: " + throwable?.message, throwable)
        },
        keepItemsOnReload = true,
        resolveHasMore = { page, previous, merged ->
            page.hasNextPage && page.items.isNotEmpty() &&
                (page.currentPage <= 1 || merged.itemCount() > previous.itemCount())
        }
    )

    val sections = pager.items
    val isLoading = pager.isRefreshing
    val isLoadingMore = pager.isLoadingMore
    val error = pager.error
    val currentPage = pager.currentPage
    val hasMore = pager.hasMore

    fun load() {
        if (pager.isRefreshing.value) return
        pager.reload()
    }

    fun loadMore() {
        pager.loadMore()
    }
}

private fun List<ComicSection>.itemCount(): Int = sumOf { it.items.size }

private fun mergeSections(old: List<ComicSection>, incoming: List<ComicSection>): List<ComicSection> {
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
