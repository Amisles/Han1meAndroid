package app.amisles.hanime.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.data.remote.CloudflareChallenge
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.ui.theme.PageLayoutResolver
import app.amisles.hanime.domain.preferences.PageLayoutKey
import app.amisles.hanime.domain.preferences.PageLayoutMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit = {},
    initialKeyword: String? = null,
    initialGenre: String? = null,
    initialSort: String? = null,
    /**
     * 导航意图标识：每次「点大图 / 点分类」都带一个不同值，用于区分
     * 「同参数的新一次点按」与「从详情页返回」。不参与搜索条件。
     */
    initialToken: String? = null,
    onAuthorClick: (String) -> Unit = {},
    onVideoLongClick: (HanimeVideo) -> Unit = {},
    reselectTick: Int = 0
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
    val loadMoreError by viewModel.loadMoreError.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val sortValue by viewModel.sort.collectAsStateWithLifecycle()
    val genreValue by viewModel.genre.collectAsStateWithLifecycle()
    val tagsValue by viewModel.tags.collectAsStateWithLifecycle()
    val broadValue by viewModel.broad.collectAsStateWithLifecycle()
    val dateValue by viewModel.date.collectAsStateWithLifecycle()
    val durationValue by viewModel.duration.collectAsStateWithLifecycle()

    var localQuery by rememberSaveable { mutableStateOf(query) }
    // 上一次已同步给输入框的 query。两者都走 rememberSaveable，随导航条目一起保存。
    //
    // 只在「ViewModel 的 query 真的变了」时才回写输入框：用户可能只输入未提交就点进
    // 详情页，返回时输入框应保留其草稿。若无条件用 query 覆盖，草稿会被上一次已生效的
    // 关键词顶掉；而完全不覆盖，点搜索历史 / 注入初值时输入框又不会更新。
    var lastSyncedQuery by rememberSaveable { mutableStateOf(query) }
    androidx.compose.runtime.LaunchedEffect(query) {
        if (query != lastSyncedQuery) {
            localQuery = query
            lastSyncedQuery = query
        }
    }
    // 分类同样以 ViewModel 的 genre 为准，否则从详情页返回后胶囊回到「全部」、
    // 而请求仍带着旧的 genre 参数，界面与结果再次分裂。
    val selectedFilter = remember(genreValue) {
        mutableStateOf(
            categories.firstOrNull { it.apiValue == genreValue } ?: allCategory
        )
    }
    // 排序以 ViewModel 为准：它同时决定请求参数，本地副本在页面重建后会与请求脱节。
    // 用派生值而非独立 state，避免「界面显示 A、请求实际用 B」的分裂。
    val selectedSort = remember(sortValue) {
        sortOptions.firstOrNull { it.apiValue == sortValue }
    }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showDurationSheet by remember { mutableStateOf(false) }
    // 底部标签面板的本地选择态（存 value，官网原值），「应用」时才提交给 ViewModel
    val selectedTags = remember { mutableStateOf(tagsValue.toSet()) }
    val broadLocal = remember { mutableStateOf(broadValue) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var handledReselectTick by rememberSaveable { mutableIntStateOf(reselectTick) }
    androidx.compose.runtime.LaunchedEffect(reselectTick) {
        if (reselectTick == handledReselectTick) return@LaunchedEffect
        handledReselectTick = reselectTick
        if (gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
        if (listState.layoutInfo.totalItemsCount > 0) listState.scrollToItem(0)
    }

    // 人机校验通过后自动重搜，与首页一致。
    //
    // solvedTick 是**进程级累计值**，不会归零；若只按「>0 就重搜」判断，从详情页返回（本页重新组合、
    // LaunchedEffect 必然重跑）就会再搜一次：既白跑一次请求，又会推进搜索代数、把列表滚回顶部。
    // 故记住本页已处理过的 tick，只响应「本页在场期间新发生的那次通过」。
    val solvedTick by CloudflareChallenge.solvedTick.collectAsStateWithLifecycle()
    var handledSolvedTick by remember { mutableStateOf(solvedTick) }
    androidx.compose.runtime.LaunchedEffect(solvedTick) {
        if (solvedTick == handledSolvedTick) return@LaunchedEffect
        handledSolvedTick = solvedTick
        if (solvedTick > 0) viewModel.resetSearch()
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    // 输入过程中才展开建议；提交 / 清除 / 点选后收起，直到用户再次输入
    var suggestionsOpen by remember { mutableStateOf(false) }
    // 标签目录只随语言变化重建：每次按键都重新解析上百条文案是纯浪费
    val suggestionTags = remember(context) {
        SearchTagCatalog.groups.flatMap { group ->
            group.tags.map { SuggestionTag(it.value, context.getString(it.labelRes)) }
        }
    }
    val matchedHistory = remember(localQuery, searchHistory) {
        SearchSuggestions.history(localQuery, searchHistory.orEmpty())
    }
    val matchedTags = remember(localQuery, suggestionTags) {
        SearchSuggestions.tags(localQuery, suggestionTags)
    }
    val showSuggestions = suggestionsOpen && localQuery.isNotBlank() &&
        (matchedHistory.isNotEmpty() || matchedTags.isNotEmpty())

    // 布局模式：本页布局记忆优先，回落到旧的全局设置，从未选择时按宽度档位取默认
    val sizeInfo = currentWindowSizeInfo()
    val savedLayoutMode by appPrefs.searchLayoutModeFlow.collectAsStateWithLifecycle()
    val pageLayouts by appPrefs.pageLayoutModesFlow.collectAsStateWithLifecycle()
    val isGridMode = PageLayoutResolver.isGrid(
        key = PageLayoutKey.SEARCH,
        modes = pageLayouts,
        legacySearchMode = savedLayoutMode?.let { PageLayoutMode.valueOf(it.name) },
        compactWidth = sizeInfo.widthClass == WindowWidthSizeClass.Compact
    )

    // 滚动接近底部自动加载下一页；网格与列表各有一套滚动状态，跟随当前生效的那一套。
    // key 只保留 isGridMode：videos / isLoadingMore / hasMore 每翻一页都会变，若作为 key
    // 会连带重建整套 snapshotFlow 与收集协程（分页越多重建越频繁，且每次重建都会重新
    // 触发一次「已在底部」的判定）。这三个值改为通过 rememberUpdatedState 读取最新值。
    val videosState = rememberUpdatedState(videos)
    val isLoadingState = rememberUpdatedState(isLoading)
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
            // isLoading 守卫：新一轮搜索进行中（旧结果仍在屏、滚动态残留）不得触发加载更多，
            // 否则会用新条件请求错页并与新搜索第 1 页竞速，造成缺页。VM 侧 loadMore 也有同款守卫。
            if (shouldLoad && hasMoreState.value && !isLoadingState.value &&
                !isLoadingMoreState.value && videosState.value.isNotEmpty()
            ) {
                viewModel.loadMore()
            }
        }
    }

    // 新一轮搜索把列表滚回顶部：换词/换条件后旧滚动态会残留（新结果不足时被 clamp 到末尾），
    // 用户看到的是新结果的尾部而非顶部；残留位置又满足 nearEnd，会连锁触发一次多余的分页请求。
    //
    // 但「每次进入组合都回顶」是错的：从详情页返回时本页会重新组合，LaunchedEffect 必然重跑，
    // 一跑就会把导航条目里恢复出来的 LazyListState 当场抹掉 —— 用户下拉的进度全部丢失。
    // 故用 handledGeneration 记住「本页已经处理过的代数」，只有代数真的前进（确实开了新一轮搜索）
    // 才回顶；首次组合时初值即当前代数（或由 rememberSaveable 恢复），天然跳过，
    // 滚动位置交给 rememberLazyListState 自带的保存 / 恢复。
    val searchGeneration by viewModel.searchGeneration.collectAsStateWithLifecycle()
    // 用 rememberSaveable 而非 remember：若「提交搜索后立刻进详情」，回顶的 effect 可能还没跑，
    // 随导航条目一起保存的旧代数会让返回时补做一次回顶，避免新结果配着旧滚动位置。
    var handledGeneration by rememberSaveable { mutableStateOf(searchGeneration) }
    androidx.compose.runtime.LaunchedEffect(searchGeneration) {
        if (searchGeneration == handledGeneration) return@LaunchedEffect
        handledGeneration = searchGeneration
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }

    // 以导航参数为 key 重新注入，由 ViewModel 按参数内容去重。
    //
    // 为什么不是 LaunchedEffect(Unit)：带参导航命中 launchSingleTop / restoreState 时
    // 会复用同一个 NavBackStackEntry，ViewModel 不重建；若 effect 只跑一次，
    // 再次点大图时新参数就进不来，搜索框不会填充。
    //
    // 为什么 ViewModel 侧还要去重：从详情页返回时本页会重新进入组合，effect 会再跑一次，
    // 参数没变就必须跳过，否则会把用户后来输入的词覆盖回导航参数里的旧值。
    androidx.compose.runtime.LaunchedEffect(initialToken, initialKeyword, initialGenre, initialSort) {
        val genreArg = categories.firstOrNull {
            it.label.equals(initialGenre.orEmpty(), ignoreCase = true) ||
                it.apiValue.equals(initialGenre.orEmpty(), ignoreCase = true)
        }?.apiValue
        val sortArg = sortOptions.firstOrNull {
            it.label == initialSort || it.apiValue == initialSort
        }?.apiValue
        val applied = viewModel.applyInitialArgs(
            keyword = initialKeyword,
            genre = genreArg,
            sort = sortArg,
            token = initialToken
        )
        if (applied) {
            // 只对齐输入框；分类与排序胶囊已由 ViewModel 的 genre / sort 派生，无需手动同步
            localQuery = viewModel.query.value
            lastSyncedQuery = viewModel.query.value
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
        SearchTopBar(
            value = localQuery,
            onValueChange = {
                localQuery = it
                suggestionsOpen = it.isNotBlank()
            },
            onSearch = {
                suggestionsOpen = false
                viewModel.submitSearch(localQuery)
            },
            onClear = {
                suggestionsOpen = false
                viewModel.setQuery("")
            }
        )

        SearchFilterBar(
            genreValue = genreValue,
            tagsValue = tagsValue,
            dateValue = dateValue,
            durationValue = durationValue,
            selectedFilter = selectedFilter.value,
            selectedSort = selectedSort,
            sortMenuExpanded = sortMenuExpanded,
            isGridMode = isGridMode,
            onSortMenuExpandedChange = { sortMenuExpanded = it },
            onSelectFilter = { filter ->
                selectedFilter.value = filter
                viewModel.setGenre(filter.apiValue.ifEmpty { null })
            },
            onSelectSort = { option ->
                sortMenuExpanded = false
                viewModel.setSort(option.apiValue)
            },
            onClearFilters = {
                selectedTags.value = emptySet()
                broadLocal.value = false
                selectedFilter.value = allCategory
                viewModel.clearFilters()
            },
            onOpenTags = { showTagSheet = true },
            onOpenDate = { showDateSheet = true },
            onOpenDuration = { showDurationSheet = true },
            onToggleLayout = { grid ->
                appPrefs.setPageLayoutMode(
                    PageLayoutKey.SEARCH,
                    if (grid) PageLayoutMode.GRID else PageLayoutMode.LIST
                )
            }
        )
        // 已有结果在屏时重新搜索：只在顶部显示细进度条、保留旧列表，避免结果区闪成空 spinner
        if (isLoading && videos.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (showSuggestions) {
            SearchSuggestionPanel(
                history = matchedHistory,
                tags = matchedTags,
                onHistoryClick = { keyword ->
                    keyboardController?.hide()
                    suggestionsOpen = false
                    localQuery = keyword
                    lastSyncedQuery = keyword
                    viewModel.submitSearch(keyword)
                },
                onTagClick = { tag ->
                    keyboardController?.hide()
                    suggestionsOpen = false
                    // 面板内的本地态同步重置，否则筛选胶囊会停留在已被清空的旧条件上
                    selectedTags.value = setOf(tag.value)
                    broadLocal.value = false
                    selectedFilter.value = allCategory
                    viewModel.submitTagSuggestion(tag.value)
                }
            )
        } else if (isLoading && videos.isEmpty()) {
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
                                lastSyncedQuery = keyword
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
        } else {
            SearchResultList(
                videos = videos,
                isGridMode = isGridMode,
                isLoadingMore = isLoadingMore,
                loadMoreError = loadMoreError,
                hasMore = hasMore,
                totalPages = totalPages,
                gridState = gridState,
                listState = listState,
                onVideoClick = onVideoClick,
                onAuthorClick = onAuthorClick,
                onVideoLongClick = onVideoLongClick,
                onLoadMore = { viewModel.loadMore() }
            )
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

