package app.amisles.hanime.feature.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.ComicSort
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ComicDirectoryResultScreen(
    kind: ComicDirectoryKind,
    title: String,
    url: String,
    onBackClick: () -> Unit,
    onComicClick: (String) -> Unit,
    viewModel: ComicDirectoryResultViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(url) { viewModel.start(url) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                if (total > 0 && last >= total - COMIC_LOAD_MORE_THRESHOLD) viewModel.loadMore()
            }
    }

    var handledSort by rememberSaveable { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        if (sort == handledSort) return@LaunchedEffect
        handledSort = sort
        gridState.scrollToItem(0)
    }

    ResponsiveContent {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 4.dp, end = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            ComicSortBar(selected = sort, onSelect = viewModel::selectSort)

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading && items.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    error != null && items.isEmpty() -> KaomojiErrorView(
                        message = error,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize()
                    )
                    items.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
                                if (kind == ComicDirectoryKind.ARTIST) {
                                    R.string.comic_directory_artist_empty
                                } else {
                                    R.string.comic_directory_tag_empty
                                }
                            ),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val gap = COMIC_GRID_GAP.dp
                        val cardWidth = (maxWidth - COMIC_GRID_PADDING.dp * 2 - gap * (COMIC_GRID_COLUMNS - 1)) / COMIC_GRID_COLUMNS
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(COMIC_GRID_COLUMNS),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = COMIC_GRID_PADDING.dp),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
                        ) {
                            items(items = items, key = { it.id }) { comic ->
                                ComicGridCard(comic, cardWidth, onClick = { onComicClick(comic.id) })
                            }
                            if (isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            } else if (!hasMore && currentPage > 1) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = stringResource(R.string.comic_directory_no_more),
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicSortBar(selected: ComicSort, onSelect: (ComicSort) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "sort_latest") {
            ComicSortChip(
                label = stringResource(R.string.comic_sort_latest),
                selected = selected == ComicSort.LATEST,
                onClick = { onSelect(ComicSort.LATEST) }
            )
        }
        item(key = "sort_popular_caption") {
            Text(
                text = stringResource(R.string.comic_sort_popular),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item(key = "sort_today") {
            ComicSortChip(
                label = stringResource(R.string.comic_sort_today),
                selected = selected == ComicSort.POPULAR_TODAY,
                onClick = { onSelect(ComicSort.POPULAR_TODAY) }
            )
        }
        item(key = "sort_week") {
            ComicSortChip(
                label = stringResource(R.string.comic_sort_week),
                selected = selected == ComicSort.POPULAR_WEEK,
                onClick = { onSelect(ComicSort.POPULAR_WEEK) }
            )
        }
        item(key = "sort_all") {
            ComicSortChip(
                label = stringResource(R.string.comic_sort_all),
                selected = selected == ComicSort.POPULAR,
                onClick = { onSelect(ComicSort.POPULAR) }
            )
        }
    }
}

@Composable
private fun ComicSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}
