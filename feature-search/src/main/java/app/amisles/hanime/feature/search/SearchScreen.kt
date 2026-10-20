package app.amisles.hanime.feature.search

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Popup
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.LayoutModeToggle
import app.amisles.hanime.core.ui.components.SearchField
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.model.Category
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.domain.preferences.SearchLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit = {},
    initialKeyword: String? = null,
    initialGenre: String? = null,
    initialSort: String? = null,
    onAuthorClick: (String) -> Unit = {}
) {
    val appPrefs = LocalAppPreferences.current
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
    val tagsValue by viewModel.tags.collectAsStateWithLifecycle()
    val broadValue by viewModel.broad.collectAsStateWithLifecycle()
    val dateValue by viewModel.date.collectAsStateWithLifecycle()
    val durationValue by viewModel.duration.collectAsStateWithLifecycle()

    var localQuery by rememberSaveable { mutableStateOf(query) }
    val selectedFilter = remember { mutableStateOf<Category>(allCategory) }
    // 未选择排序时为 null：此时请求不带 sort 参数，界面显示「默认排序」，避免下拉宣称一个未生效的值
    val selectedSort = remember { mutableStateOf<SortOption?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showDurationSheet by remember { mutableStateOf(false) }
    // 底部标签面板的本地选择态（存 value，官网原值），「应用」时才提交给 ViewModel
    val selectedTags = remember { mutableStateOf(tagsValue.toSet()) }
    val broadLocal = remember { mutableStateOf(broadValue) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // 布局模式：用户显式选择优先；从未选择过时按宽度档位取默认（Compact 列表 / 平板网格）
    val sizeInfo = currentWindowSizeInfo()
    val savedLayoutMode by appPrefs.searchLayoutModeFlow.collectAsStateWithLifecycle()
    val isGridMode = (savedLayoutMode ?: if (sizeInfo.widthClass == WindowWidthSizeClass.Compact) {
        SearchLayoutMode.LIST
    } else {
        SearchLayoutMode.GRID
    }) == SearchLayoutMode.GRID

    // 滚动接近底部自动加载下一页；网格与列表各有一套滚动状态，跟随当前生效的那一套。
    // key 只保留 isGridMode：videos / isLoadingMore / hasMore 每翻一页都会变，若作为 key
    // 会连带重建整套 snapshotFlow 与收集协程（分页越多重建越频繁，且每次重建都会重新
    // 触发一次「已在底部」的判定）。这三个值改为通过 rememberUpdatedState 读取最新值。
    val videosState = rememberUpdatedState(videos)
    val isLoadingMoreState = rememberUpdatedState(isLoadingMore)
    val hasMoreState = rememberUpdatedState(hasMore)
    androidx.compose.runtime.LaunchedEffect(isGridMode) {
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
            if (shouldLoad && hasMoreState.value && !isLoadingMoreState.value &&
                videosState.value.isNotEmpty()
            ) {
                viewModel.loadMore()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialKeyword) {
        if (!initialKeyword.isNullOrEmpty()) {
            val cleanedKeyword = initialKeyword.trimStart('#').trim()
            localQuery = cleanedKeyword
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
                // setGenre 在值变化时会自行触发搜索，此处不再重复调用
                viewModel.setGenre(match.apiValue)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialSort) {
        if (!initialSort.isNullOrBlank()) {
            // 按 label（如 "最新上市"）或 apiValue（如 "最新上傳"）匹配
            val match = sortOptions.firstOrNull {
                it.label == initialSort || it.apiValue == initialSort
            }
            if (match != null) {
                selectedSort.value = match
                // setSort 在值变化时会自行触发搜索，此处不再重复调用
                viewModel.setSort(match.apiValue)
            }
        }
    }

    ResponsiveContent {
        Box(modifier = Modifier.fillMaxSize()) {
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
                .padding(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                value = localQuery,
                onValueChange = {
                    localQuery = it
                },
                placeholder = stringResource(R.string.search_placeholder),
                onSearch = {
                    viewModel.submitSearch(localQuery)
                },
                onClear = { viewModel.setQuery("") },
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 15.dp, end = 15.dp, bottom = 6.dp)
        ) {
            // 是否存在任何已生效的筛选条件（决定是否显示「清除筛选」入口）
            val hasActiveFilter = genreValue != null || tagsValue.isNotEmpty() ||
                dateValue.isNotEmpty() || durationValue.isNotEmpty()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 15.dp)
            ) {
                // 一键清除全部筛选：仅在确有筛选生效时出现
                if (hasActiveFilter) {
                    item(key = "clear_filters_chip") {
                        val clearShape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
                        Box(
                            modifier = Modifier
                                .clip(clearShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = clearShape
                                )
                                .clickable {
                                    // 先同步 UI 本地态，再让 ViewModel 一次性清空并只发一次请求
                                    selectedTags.value = emptySet()
                                    broadLocal.value = false
                                    selectedFilter.value = allCategory
                                    viewModel.clearFilters()
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.search_filter_clear_all),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 筛选入口组：点击打开底部选择面板，带下拉箭头
                item(key = "tag_filter_chip") {
                    val count = tagsValue.size
                    FilterEntryChip(
                        label = if (count > 0) {
                            stringResource(R.string.search_filter_tag_count, count)
                        } else {
                            stringResource(R.string.search_filter_tag)
                        },
                        selected = count > 0,
                        onClick = { showTagSheet = true }
                    )
                }
                item(key = "date_filter_chip") {
                    FilterEntryChip(
                        label = if (dateValue.isNotEmpty()) {
                            dateChipLabel(dateValue)
                        } else {
                            stringResource(R.string.search_filter_date)
                        },
                        selected = dateValue.isNotEmpty(),
                        onClick = { showDateSheet = true }
                    )
                }
                item(key = "duration_filter_chip") {
                    FilterEntryChip(
                        label = if (durationValue.isNotEmpty()) {
                            durationChipLabel(durationValue)
                        } else {
                            stringResource(R.string.search_filter_duration)
                        },
                        selected = durationValue.isNotEmpty(),
                        onClick = { showDurationSheet = true }
                    )
                }
                // 组间分隔线：分开「筛选入口」与「分类」单选
                item(key = "filter_group_divider") {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
                items(items = filterTypes, key = { it.displayRes }) { filter ->
                    val isSelected = selectedFilter.value.apiValue == filter.apiValue
                    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = shape
                            )
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
                    .padding(top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_sort),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 按钮与下拉菜单拼成一个整体矩形：展开时按钮去下圆角、菜单去上圆角，
                // 菜单紧贴按钮底部，且容器色、描边与按钮一致
                val sortRadius = LocalUiConfig.current.cardCornerRadius
                val sortWidth = 116.dp
                val closedShape = RoundedCornerShape(sortRadius)
                val buttonShape = if (sortMenuExpanded) {
                    RoundedCornerShape(topStart = sortRadius, topEnd = sortRadius)
                } else {
                    closedShape
                }
                val menuShape = RoundedCornerShape(bottomStart = sortRadius, bottomEnd = sortRadius)
                val arrowRotation by animateFloatAsState(
                    targetValue = if (sortMenuExpanded) 180f else 0f,
                    label = "sortArrow"
                )
                // 用 MutableTransitionState 驱动进出动画：收起时需等动画结束再移除 Popup
                val menuTransition = remember { MutableTransitionState(false) }
                menuTransition.targetState = sortMenuExpanded
                Box {
                    Row(
                        modifier = Modifier
                            .width(sortWidth)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = buttonShape
                            )
                            .clip(buttonShape)
                            .clickable { sortMenuExpanded = !sortMenuExpanded }
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedSort.value?.let { stringResource(it.labelRes) }
                                ?: stringResource(R.string.search_sort_default),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.search_sort),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = arrowRotation }
                        )
                    }
                    // 锚定在按钮下方，用垂直展开代替菜单默认的中心缩放，
                    // 视觉上是从按钮底边向下拉出；退出同样朝向按钮收起
                    SortMenuPopup(
                        transition = menuTransition,
                        width = sortWidth,
                        shape = menuShape,
                        selected = selectedSort.value,
                        onSelect = { option ->
                            selectedSort.value = option
                            sortMenuExpanded = false
                            viewModel.setSort(option.apiValue)
                        },
                        onDismiss = { sortMenuExpanded = false }
                    )
                }

                // 布局模式切换：右对齐，与排序下拉同一行；写入即视为用户已显式选择
                Spacer(modifier = Modifier.weight(1f))
                LayoutModeToggle(
                    isGrid = isGridMode,
                    onToggle = { grid ->
                        appPrefs.setSearchLayoutMode(
                            if (grid) SearchLayoutMode.GRID else SearchLayoutMode.LIST
                        )
                    }
                )
            }
        }

        // 已有结果在屏时重新搜索：只在顶部显示细进度条、保留旧列表，避免结果区闪成空 spinner
        if (isLoading && videos.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (isLoading && videos.isEmpty()) {
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
        } else if (videos.isEmpty() && query.isEmpty() && sortValue == null && genreValue == null && tagsValue.isEmpty() && dateValue.isEmpty() && durationValue.isEmpty()) {
            // null = 历史仍在读库，此时什么都不渲染：否则会先弹空态提示、再切成历史列表
            val history = searchHistory
            if (history != null) {
                if (history.isEmpty()) {
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
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                        Spacer(modifier = Modifier.height(8.dp))
                        SearchHistoryChips(
                            history = history,
                            onSearch = { keyword ->
                                localQuery = keyword
                                viewModel.submitSearch(keyword)
                            },
                            onRemove = { viewModel.removeSearchHistory(it) }
                        )
                    }
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

                    // 网格页脚需横跨整行，故显式指定 span
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
        if (showTagSheet) {
            TagFilterSheet(
                initialTags = selectedTags.value,
                initialBroad = broadLocal.value,
                onDismiss = { showTagSheet = false },
                onApply = { appliedTags, appliedBroad ->
                    selectedTags.value = appliedTags
                    broadLocal.value = appliedBroad
                    showTagSheet = false
                    viewModel.setTags(appliedTags.toList())
                    viewModel.setBroad(appliedBroad)
                    viewModel.resetSearch()
                }
            )
        }

        if (showDateSheet) {
            DateFilterSheet(
                initialDate = dateValue,
                onDismiss = { showDateSheet = false },
                onApply = { appliedDate ->
                    showDateSheet = false
                    viewModel.setDate(appliedDate)
                }
            )
        }

        if (showDurationSheet) {
            DurationFilterSheet(
                initialDuration = durationValue,
                onDismiss = { showDurationSheet = false },
                onApply = { appliedDuration ->
                    showDurationSheet = false
                    viewModel.setDuration(appliedDuration)
                }
            )
        }
    }
    }
}

