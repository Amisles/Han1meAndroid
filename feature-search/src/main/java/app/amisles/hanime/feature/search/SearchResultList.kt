package app.amisles.hanime.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.HanimeVideo

@Composable
internal fun SearchResultList(
    videos: List<HanimeVideo>,
    isGridMode: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: Boolean,
    hasMore: Boolean,
    totalPages: Int,
    gridState: LazyGridState,
    listState: LazyListState,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onVideoLongClick: (HanimeVideo) -> Unit,
    onLoadMore: () -> Unit
) {
    if (isGridMode) {
        // 网格列数按「实测可用宽度」算而非只看宽度档位，横屏手机 / 分屏 / 折叠屏都能正确响应
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cfg = LocalUiConfig.current
            val columns = remember(maxWidth, cfg) { cfg.gridColumnsFor(maxWidth) }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                items(items = videos, key = { it.id }) { video ->
                    VideoGridCard(
                        video = video,
                        onClick = { onVideoClick(video.videoUrl) },
                        onAuthorClick = onAuthorClick,
                        onLongClick = { onVideoLongClick(video) }
                    )
                }

                // 网格页脚需横跨整行，故显式指定 span
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchLoadMoreIndicator()
                    }
                } else if (loadMoreError) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchLoadMoreRetry(onRetry = { onLoadMore() })
                    }
                } else if (!hasMore && videos.isNotEmpty() && totalPages > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchNoMoreHint()
                    }
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(items = videos, key = { it.id }) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video.videoUrl) },
                    onAuthorClick = onAuthorClick,
                    onLongClick = { onVideoLongClick(video) }
                )
            }

            // 加载中指示器（自动触发加载时显示）
            if (isLoadingMore) {
                item { SearchLoadMoreIndicator() }
            } else if (loadMoreError) {
                item { SearchLoadMoreRetry(onRetry = { onLoadMore() }) }
            } else if (!hasMore && videos.isNotEmpty() && totalPages > 1) {
                item { SearchNoMoreHint() }
            }
        }
    }
}
