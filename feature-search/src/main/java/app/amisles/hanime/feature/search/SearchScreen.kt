package app.amisles.hanime.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.LayoutModeToggle
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.model.Category
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.preferences.SearchLayoutMode

val allCategory = Category("", R.string.search_all, "")
val filterTypes = listOf(allCategory) + categories

/**
 * 排序选项。
 *
 * [labelRes] 用于界面展示；[label] 与 [apiValue] 都只是**匹配键**（站点简中 / 繁中两套用词），
 * 用于把导航传入的 `initialSort` 映射到具体选项，不参与展示（审查 G8）。
 */
data class SortOption(val label: String, val labelRes: Int, val apiValue: String)

val sortOptions = listOf(
    SortOption("最新上市", R.string.search_sort_new_release, "最新上市"),
    SortOption("最新上传", R.string.search_sort_new_upload, "最新上傳"),
    SortOption("本日排行", R.string.search_sort_daily, "本日排行"),
    SortOption("本周排行", R.string.search_sort_weekly, "本週排行"),
    SortOption("本月排行", R.string.search_sort_monthly, "本月排行"),
    SortOption("观看次数", R.string.search_sort_views, "觀看次數"),
    SortOption("点赞比例", R.string.search_sort_like_ratio, "點讚比例"),
    SortOption("时长最长", R.string.search_sort_longest, "時長最長"),
    SortOption("他们在看", R.string.search_sort_watching, "他們在看")
)

@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit = {},
    initialKeyword: String? = null,
    initialGenre: String? = null,
    initialSort: String? = null,
    onAuthorClick: (String) -> Unit = {}
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val sortValue by viewModel.sort.collectAsStateWithLifecycle()
    val genreValue by viewModel.genre.collectAsStateWithLifecycle()

    val localQuery = remember { mutableStateOf(query) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedFilter = remember { mutableStateOf<Category>(allCategory) }
    val selectedSort = remember { mutableStateOf(sortOptions[0]) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // 布局模式：用户显式选择优先；从未选择过时按宽度档位取默认（Compact 列表 / 平板网格），
    // 这样手机与平板的既有体验都不变，一旦点过切换就完全以用户选择为准（已落盘）
    val sizeInfo = currentWindowSizeInfo()
    val savedLayoutMode by Preferences.searchLayoutModeFlow.collectAsStateWithLifecycle()
    val isGridMode = (savedLayoutMode ?: if (sizeInfo.widthClass == WindowWidthSizeClass.Compact) {
        SearchLayoutMode.LIST
    } else {
        SearchLayoutMode.GRID
    }) == SearchLayoutMode.GRID

    // 滚动接近底部自动加载下一页；网格与列表各有一套滚动状态，跟随当前生效的那一套
    androidx.compose.runtime.LaunchedEffect(isGridMode, videos, isLoadingMore, hasMore) {
        val nearEnd = if (isGridMode) {
            snapshotFlow {
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                totalItems > 0 && lastVisible >= totalItems - 3
            }
        } else {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                totalItems > 0 && lastVisible >= totalItems - 3
            }
        }
        nearEnd.collect { shouldLoad ->
            if (shouldLoad && hasMore && !isLoadingMore && videos.isNotEmpty()) {
                viewModel.loadMore()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialKeyword) {
        if (!initialKeyword.isNullOrEmpty()) {
            val cleanedKeyword = initialKeyword.trimStart('#').trim()
            localQuery.value = cleanedKeyword
            viewModel.submitSearch(cleanedKeyword)
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialGenre) {
        if (!initialGenre.isNullOrBlank()) {
            val match = categories.firstOrNull {
                it.label.equals(initialGenre, ignoreCase = true) || it.apiValue.equals(initialGenre, ignoreCase = true)
            }
            if (match != null) {
                selectedFilter.value = match
                // setGenre 在值变化时会自行触发一次搜索，此处不再重复调用（审查 S2）
                viewModel.setGenre(match.apiValue)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialSort) {
        if (!initialSort.isNullOrBlank()) {
            // Match by label (e.g. "最新上市") or apiValue (e.g. "最新上傳")
            val match = sortOptions.firstOrNull {
                it.label == initialSort || it.apiValue == initialSort
            }
            if (match != null) {
                selectedSort.value = match
                // setSort 在值变化时会自行触发一次搜索，此处不再重复调用（审查 S2）
                viewModel.setSort(match.apiValue)
            }
        }
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
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = localQuery.value,
                onValueChange = {
                    localQuery.value = it
                },
                placeholder = { Text(stringResource(R.string.search_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.submitSearch(localQuery.value)
                        keyboardController?.hide()
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                trailingIcon = {
                    if (localQuery.value.isNotEmpty()) {
                        Text(
                            text = "✕",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                localQuery.value = ""
                                viewModel.setQuery("")
                            }
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        viewModel.submitSearch(localQuery.value)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.common_search),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 15.dp)
            ) {
                items(items = filterTypes, key = { it.displayRes }) { filter ->
                    val isSelected = selectedFilter.value.apiValue == filter.apiValue
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .clickable {
                                selectedFilter.value = filter
                                val apiValue = filter.apiValue.ifEmpty { null }
                                viewModel.setGenre(apiValue)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(filter.displayRes),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_sort),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                val sortDropdownWidth = 140.dp
                Box {
                    Row(
                        modifier = Modifier
                            .width(sortDropdownWidth)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .clickable { sortMenuExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(selectedSort.value.labelRes),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.search_sort),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                        modifier = Modifier.width(sortDropdownWidth)
                    ) {
                        sortOptions.forEach { option ->
                            val isSelected = option == selectedSort.value
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(option.labelRes),
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedSort.value = option
                                    sortMenuExpanded = false
                                    viewModel.setSort(option.apiValue)
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // 布局模式切换：右对齐，与排序下拉同一行；写入即视为用户已显式选择，之后不再回退默认值
                Spacer(modifier = Modifier.weight(1f))
                LayoutModeToggle(
                    isGrid = isGridMode,
                    onToggle = { grid ->
                        Preferences.setSearchLayoutMode(
                            if (grid) SearchLayoutMode.GRID else SearchLayoutMode.LIST
                        )
                    }
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (error != null && videos.isEmpty()) {
            KaomojiErrorView(
                message = error!!,
                onRetry = { viewModel.executeSearch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            )
        } else if (videos.isEmpty() && query.isEmpty() && sortValue == null && genreValue == null) {
            if (searchHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_history),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.common_clear),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 15.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(searchHistory) { index, historyQuery ->
                        // 单条历史项：搜索图标 + 关键词 + 删除按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    localQuery.value = historyQuery
                                    viewModel.submitSearch(historyQuery)
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 搜索图标
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            // 历史关键词
                            Text(
                                text = historyQuery,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // 删除按钮，保证足够的点击区域
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.removeSearchHistory(historyQuery) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        // 项之间用细分割线分隔，最后一项不显示
                        if (index < searchHistory.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_empty_hint),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_no_results),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isGridMode) {
            // 网格模式：列数按「实测可用宽度」算（BoxWithConstraints 拿到的已含 ResponsiveContent 限宽），
            // 而不是只看宽度档位 —— 横屏手机（约 800dp）因此得到 4 列而非 3 列，
            // 分屏 / 折叠屏这种「档位没变但可用宽度变了」的窗口也能正确响应
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = remember(maxWidth) { gridColumnsFor(maxWidth) }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(items = videos, key = { it.id }) { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onVideoClick(video.videoUrl) },
                            onAuthorClick = onAuthorClick
                        )
                    }

                    // 网格的页脚需要横跨整行，故显式指定 span
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchLoadMoreIndicator()
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(items = videos, key = { it.id }) { video ->
                    VideoListItem(
                        video = video,
                        onClick = { onVideoClick(video.videoUrl) },
                        onAuthorClick = onAuthorClick
                    )
                }

                // 加载中指示器（自动触发加载时显示）
                if (isLoadingMore) {
                    item { SearchLoadMoreIndicator() }
                } else if (!hasMore && videos.isNotEmpty() && totalPages > 1) {
                    item { SearchNoMoreHint() }
                }
            }
        }
    }
    }
}

/**
 * 网格列数：按「实测可用宽度」算，而不是只看宽度档位。
 *
 * 取 `floor((W + G) / (T + G))` 后夹在 [2, 5]，让卡片宽度始终落在目标值附近：
 * 手机竖屏（约 330dp 可用）2 列、横屏手机（约 770dp）4 列、平板（约 1050dp）5 列。
 * 下限必须夹到 2 —— `GridCells.Adaptive` 在 330dp 下只会算出 1 列，手机上就退化成单列了。
 */
private fun gridColumnsFor(availableWidth: Dp): Int {
    val width = availableWidth.value
    if (width.isNaN() || width <= 0f) return 2
    val target = 168.dp
    val gap = 12.dp
    return ((availableWidth + gap) / (target + gap)).toInt().coerceIn(2, 5)
}

/** 搜索结果「加载中」页脚：列表与网格共用，网格侧由调用方指定 span 横跨整行。 */
@Composable
private fun SearchLoadMoreIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** 搜索结果「没有更多了」页脚：列表与网格共用。 */
@Composable
private fun SearchNoMoreHint() {
    Text(
        text = stringResource(R.string.search_no_more),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
