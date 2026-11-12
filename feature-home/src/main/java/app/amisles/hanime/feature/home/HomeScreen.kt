package app.amisles.hanime.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.domain.model.WatchLaterVideo
import app.amisles.hanime.domain.model.WatchStats
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.Banner
import app.amisles.hanime.core.ui.components.CategoryScroll
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.ThumbnailPrefetcher
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.data.remote.CloudflareChallenge
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.LocalHomeSectionConfig
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.core.ui.model.homeSectionTitleResMap
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.UiConfig
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PREFETCH_SECTION_COUNT = 2

/** 横滑布局的卡片宽度。 */
private val CAROUSEL_CARD_WIDTH = 200.dp

/** 行内多列布局的项间距、单项水平外边距与缩略图占卡宽比例；换算列宽时须与渲染处一致。 */
private const val LIST_ROW_SPACING_DP = 10f
private const val LIST_ITEM_H_PADDING_DP = 5f
private const val LIST_ROW_THUMB_RATIO = 0.42f

private const val SKELETON_SECTION_COUNT = 3
private const val SKELETON_HERO_SIDE_BLOCKS = 2
private const val SKELETON_CAROUSEL_CARDS = 2
private val SKELETON_SECTION_TITLES = listOf("最新上市", "最新上传", "本日排行")

internal fun skeletonRowCount(itemCount: Int, columns: Int): Int {
    val safeColumns = columns.coerceAtLeast(1)
    return (itemCount + safeColumns - 1) / safeColumns
}

internal fun rowThumbnailWidth(
    cfg: UiConfig,
    widthDp: Int,
    contentMaxWidth: Dp,
    columns: Int,
    isCompactWidth: Boolean
): Dp {
    if (isCompactWidth) return cfg.listThumbnailWidth
    val contentWidth = minOf(widthDp.toFloat(), contentMaxWidth.value)
    val rowWidth = (contentWidth - (columns - 1) * LIST_ROW_SPACING_DP) / columns - 2 * LIST_ITEM_H_PADDING_DP
    return (rowWidth * LIST_ROW_THUMB_RATIO)
        .coerceAtMost(cfg.listThumbnailWidth.value)
        .coerceAtLeast(56f).dp
}

/** AUTO 解析后的渲染形态：由宽度档位与是否分栏共同决定，故需与用户可选的 [HomeSectionLayout] 区分。 */
private enum class SectionRenderMode { LIST_COLUMN, LIST_ROWS, GRID_ROWS, CAROUSEL }

private enum class HomeItemType { HEADER, CATEGORY, SKELETON, ERROR, REFRESH_ERROR, BANNER, SECTION, SPACER }

private data class SectionPlan(
    val section: HomeSection,
    val mode: SectionRenderMode,
    val videos: List<HanimeVideo>,
    val rowVideos: List<List<HanimeVideo>>
)

private fun renderMode(
    configured: HomeSectionLayout,
    widthClass: WindowWidthSizeClass,
    twoColumnSections: Boolean
): SectionRenderMode =
    when (configured) {
        HomeSectionLayout.LIST -> SectionRenderMode.LIST_COLUMN
        HomeSectionLayout.GRID -> SectionRenderMode.GRID_ROWS
        HomeSectionLayout.CAROUSEL -> SectionRenderMode.CAROUSEL
        HomeSectionLayout.AUTO -> when {
            twoColumnSections -> SectionRenderMode.GRID_ROWS
            widthClass == WindowWidthSizeClass.Compact -> SectionRenderMode.LIST_COLUMN
            widthClass == WindowWidthSizeClass.Medium -> SectionRenderMode.LIST_ROWS
            else -> SectionRenderMode.GRID_ROWS
        }
    }

private fun buildPlans(
    sections: List<HomeSection>,
    homeConfig: HomeSectionConfig,
    widthClass: WindowWidthSizeClass,
    columns: Int,
    twoColumnSections: Boolean
): List<SectionPlan> = homeConfig.arrange(sections).map { section ->
    val mode = renderMode(homeConfig.layoutFor(section.title), widthClass, twoColumnSections)
    val videos = section.videos.take(homeConfig.countFor(section.title))
    val rowVideos = when (mode) {
        SectionRenderMode.LIST_ROWS, SectionRenderMode.GRID_ROWS -> videos.chunked(columns)
        SectionRenderMode.LIST_COLUMN, SectionRenderMode.CAROUSEL -> emptyList()
    }
    SectionPlan(section = section, mode = mode, videos = videos, rowVideos = rowVideos)
}

private fun sectionItemKey(plan: SectionPlan, index: Int): String =
    "home-section-$index-" + plan.section.title

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (HomeSection) -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    onComicClick: (() -> Unit)? = null,
    onVideoLongClick: (HanimeVideo) -> Unit = {},
    onOpenWatchStats: () -> Unit = {}
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val watchLater by viewModel.watchLater.collectAsStateWithLifecycle()
    val weeklyReport by viewModel.weeklyReport.collectAsStateWithLifecycle()
    val weeklyReportVisible by viewModel.weeklyReportVisible.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val challengeHost by viewModel.challengeHost.collectAsStateWithLifecycle()
    val siteUrl by LocalAppPreferences.current.baseUrlFlow.collectAsStateWithLifecycle()
    // 换站后回到首页必须重拉：LaunchedEffect 在页面重新进入组合时同样会重跑，是否真的换站由 VM 判定
    LaunchedEffect(siteUrl) { viewModel.refreshIfSiteChanged(siteUrl) }
    // 人机校验通过后自动重拉：否则用户要手动下拉一次才知道已经能用了
    val solvedTick by CloudflareChallenge.solvedTick.collectAsStateWithLifecycle()
    LaunchedEffect(solvedTick) {
        if (solvedTick > 0) viewModel.loadHomeData()
    }

    HomeScreenContent(
        sections = sections,
        continueWatching = continueWatching,
        watchLater = watchLater,
        weeklyReport = weeklyReport,
        weeklyReportVisible = weeklyReportVisible,
        banner = banner,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        error = error,
        challengeHost = challengeHost,
        siteUrl = siteUrl,
        onRefresh = { viewModel.loadHomeData() },
        onVideoClick = onVideoClick,
        onSearchClick = onSearchClick,
        onGenreSearch = onGenreSearch,
        onNavigateToSearch = onNavigateToSearch,
        onAuthorClick = onAuthorClick,
        onViewMore = onViewMore,
        onProfileClick = onProfileClick,
        onComicClick = onComicClick,
        onVideoLongClick = onVideoLongClick,
        onMarkContinueWatched = { id -> viewModel.markContinueItemWatched(id) },
        onRemoveContinueItem = { id -> viewModel.removeContinueItem(id) },
        onConsumeWatchLater = { item -> viewModel.consumeWatchLater(item) },
        onRemoveWatchLater = { id -> viewModel.removeWatchLater(id) },
        onDismissWeeklyReport = { viewModel.dismissWeeklyReport() },
        onOpenWatchStats = onOpenWatchStats
    )
}

@Composable
fun HomeScreenContent(
    sections: List<HomeSection>,
    continueWatching: List<WatchHistory> = emptyList(),
    watchLater: List<WatchLaterVideo> = emptyList(),
    weeklyReport: WatchStats? = null,
    weeklyReportVisible: Boolean = false,
    banner: HanimeBanner?,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    error: String? = null,
    challengeHost: String? = null,
    siteUrl: String? = null,
    onRefresh: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (HomeSection) -> Unit = {},
    onProfileClick: (() -> Unit)? = null,
    onComicClick: (() -> Unit)? = null,
    onVideoLongClick: (HanimeVideo) -> Unit = {},
    onMarkContinueWatched: (String) -> Unit = {},
    onRemoveContinueItem: (String) -> Unit = {},
    onConsumeWatchLater: (WatchLaterVideo) -> Unit = {},
    onRemoveWatchLater: (String) -> Unit = {},
    onDismissWeeklyReport: () -> Unit = {},
    onOpenWatchStats: () -> Unit = {}
) {
    val listState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    val sizeInfo = currentWindowSizeInfo()
    val cfg = LocalUiConfig.current
    val homeConfig = LocalHomeSectionConfig.current
    var selectedCategory by remember { mutableStateOf(categories[0].label) }

    val isCompactWidth = sizeInfo.widthClass == WindowWidthSizeClass.Compact
    val twoColumnSections = remember(sizeInfo) { homeTwoColumnSections(sizeInfo) }
    val columns = remember(sizeInfo, cfg, twoColumnSections) {
        homeSectionColumns(cfg, sizeInfo, twoColumnSections).coerceAtLeast(1)
    }
    val hasSideContent = continueWatching.isNotEmpty() || watchLater.isNotEmpty()
    val heroLayout = remember(sizeInfo, hasSideContent) {
        homeHeroLayout(sizeInfo.widthDp, sizeInfo.heightDp, sizeInfo.contentMaxWidth, hasSideContent)
    }
    val twoColumnHero = heroLayout.twoColumn && banner != null
    val plans = remember(sections, homeConfig, sizeInfo.widthClass, columns, twoColumnSections) {
        buildPlans(sections, homeConfig, sizeInfo.widthClass, columns, twoColumnSections)
    }
    // 多列时缩略图须按列宽等比缩小：固定 150dp 会把文字区压到几 dp。比例取 0.42，
    // 与手机单列下缩略图占卡宽的比例（150 / 340）一致
    val rowThumbnailWidth = remember(cfg.listThumbnailWidth, columns, sizeInfo, isCompactWidth) {
        rowThumbnailWidth(cfg, sizeInfo.widthDp, sizeInfo.contentMaxWidth, columns, isCompactWidth)
    }
    val headKeys = buildList {
        add("home-header")
        if (cfg.homeShowCategoryBar) add("home-category")
        if (error != null && sections.isNotEmpty()) add("home-refresh-error")
        if (twoColumnHero) {
            add("home-hero")
        } else {
            if (banner != null) add("home-banner")
            if (continueWatching.isNotEmpty()) add("home-continue-watching")
            if (watchLater.isNotEmpty()) add("home-watch-later")
        }
        if (weeklyReportVisible && weeklyReport != null) add("home-weekly-report")
    }
    val sectionTitleToIndex: Map<String, Int> = remember(plans, headKeys) {
        buildMap {
            plans.forEachIndexed { index, plan -> put(plan.section.title, headKeys.size + index) }
        }
    }
    // 转屏或分屏改变档位时，item 结构与行高都会变，LazyList 只保留下标，画面会跳到
    // 别的分区。旧布局仍可读的时刻记下首个可见 item 的 key 和它被裁掉的高度，
    // 新布局量完按 key 找回同一个位置；只按下标推断会在两栏与单栏之间错位。
    val itemKeys = remember(headKeys, plans) {
        headKeys + plans.mapIndexed { index, plan -> sectionItemKey(plan, index) }
    }
    val layoutSignature = listOf(sizeInfo.widthDp, sizeInfo.heightDp, columns, twoColumnSections, twoColumnHero)
    var pendingAnchorKey by remember { mutableStateOf<String?>(null) }
    var pendingAnchorOffset by remember { mutableStateOf(0) }
    DisposableEffect(layoutSignature) {
        onDispose {
            val info = listState.layoutInfo.visibleItemsInfo.minByOrNull { it.index }
            val key = info?.key as? String
            if (key != null) {
                pendingAnchorKey = key
                pendingAnchorOffset = info.offset.y
            }
        }
    }
    LaunchedEffect(layoutSignature) {
        val anchor = pendingAnchorKey ?: return@LaunchedEffect
        pendingAnchorKey = null
        val index = itemKeys.indexOf(anchor)
        if (index >= 0) {
            listState.scrollToItem(index, pendingAnchorOffset)
        }
        pendingAnchorOffset = 0
    }

    val context = LocalContext.current
    LaunchedEffect(plans, sectionTitleToIndex, listState, context) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) return@collect
                val lastVisible = listState.layoutInfo.visibleItemsInfo
                    .maxOfOrNull { it.index } ?: return@collect
                val belowUrls = plans.asSequence()
                    .filter { (sectionTitleToIndex[it.section.title] ?: Int.MAX_VALUE) > lastVisible }
                    .take(PREFETCH_SECTION_COUNT)
                    .flatMap { it.videos }
                    .map { it.thumbnailUrl }
                    .toList()
                ThumbnailPrefetcher.prefetch(context, belowUrls)
            }
    }

    PullToRefreshBox(
        // 只响应刷新态：首屏加载由内容区的骨架屏表达，两者不再共用一个标志位
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        ResponsiveContent {
        // 状态栏内边距已由外层 PullToRefreshBox 提供，这里不再叠加，否则顶部留白翻倍
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(if (twoColumnSections) 2 else 1),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(HOME_SECTION_GAP),
            modifier = Modifier
                .fillMaxSize()
        ) {
        item(key = "home-header", contentType = HomeItemType.HEADER, span = StaggeredGridItemSpan.FullLine) {
            Header(
                siteUrl = siteUrl,
                onSearchNavigate = onNavigateToSearch,
                onProfileClick = onProfileClick,
                onComicClick = onComicClick
            )
        }

        if (cfg.homeShowCategoryBar) {
            item(key = "home-category", contentType = HomeItemType.CATEGORY, span = StaggeredGridItemSpan.FullLine) {
                CategoryScroll(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { category ->
                        selectedCategory = category
                        val targetIndex = sectionTitleToIndex[category]
                        if (targetIndex != null) {
                            scope.launch {
                                listState.animateScrollToItem(targetIndex, scrollOffset = -20)
                            }
                        } else {
                            onGenreSearch(category)
                        }
                    }
                )
            }
        }

        if (isLoading) {
            item(key = "home-loading", contentType = HomeItemType.SKELETON, span = StaggeredGridItemSpan.FullLine) {
                HomeSkeletonScreen()
            }
        } else if (error != null && sections.isEmpty()) {
            item(key = "home-error", contentType = HomeItemType.ERROR, span = StaggeredGridItemSpan.FullLine) {
                KaomojiErrorView(
                    message = error,
                    onRetry = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (sizeInfo.heightDp * 0.6f).dp),
                    actionLabel = challengeHost?.let { stringResource(R.string.error_action_verify) },
                    onAction = challengeHost?.let { host -> { CloudflareChallenge.request(host) } }
                )
            }
        } else {
            if (error != null && sections.isNotEmpty()) {
                item(key = "home-refresh-error", contentType = HomeItemType.REFRESH_ERROR, span = StaggeredGridItemSpan.FullLine) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onRefresh() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error.orEmpty(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.common_retry),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            val heroBanner = banner
            if (twoColumnHero) {
                item(key = "home-hero", contentType = HomeItemType.BANNER, span = StaggeredGridItemSpan.FullLine) {
                    HomeHeroRow(
                        banner = heroBanner,
                        layout = heroLayout,
                        continueWatching = continueWatching,
                        watchLater = watchLater,
                        onPlayClick = { onVideoClick(heroBanner.videoUrl) },
                        onSearchClick = onSearchClick,
                        onContinueClick = { history -> onVideoClick(history.videoUrl) },
                        onMarkWatched = { history -> onMarkContinueWatched(history.id) },
                        onRemoveContinue = { history -> onRemoveContinueItem(history.id) },
                        onWatchLaterClick = { item ->
                            onConsumeWatchLater(item)
                            onVideoClick(item.videoUrl)
                        },
                        onRemoveWatchLater = { item -> onRemoveWatchLater(item.id) }
                    )
                }
            } else {
                banner?.let {
                    item(key = "home-banner", contentType = HomeItemType.BANNER, span = StaggeredGridItemSpan.FullLine) {
                        Banner(
                            bannerData = it,
                            onPlayClick = { onVideoClick(it.videoUrl) },
                            onSearchClick = onSearchClick,
                            onTagClick = onSearchClick,
                            height = heroLayout.height.takeIf { height -> height.value > 0f }
                        )
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    item(key = "home-continue-watching", contentType = HomeItemType.BANNER, span = StaggeredGridItemSpan.FullLine) {
                        ContinueWatchingSection(
                            items = continueWatching,
                            onItemClick = { history -> onVideoClick(history.videoUrl) },
                            onMarkWatched = { history -> onMarkContinueWatched(history.id) },
                            onRemove = { history -> onRemoveContinueItem(history.id) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                if (watchLater.isNotEmpty()) {
                    item(key = "home-watch-later", contentType = HomeItemType.BANNER, span = StaggeredGridItemSpan.FullLine) {
                        WatchLaterSection(
                            items = watchLater,
                            onItemClick = { item ->
                                onConsumeWatchLater(item)
                                onVideoClick(item.videoUrl)
                            },
                            onRemove = { item -> onRemoveWatchLater(item.id) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            if (weeklyReportVisible && weeklyReport != null) {
                item(key = "home-weekly-report", contentType = HomeItemType.BANNER, span = StaggeredGridItemSpan.FullLine) {
                    WeeklyReportCard(
                        stats = weeklyReport,
                        onDismiss = onDismissWeeklyReport,
                        onOpenStats = onOpenWatchStats,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            plans.forEachIndexed { index, plan ->
                item(key = sectionItemKey(plan, index), contentType = HomeItemType.SECTION) {
                    SectionBlock(
                        plan = plan,
                        columns = columns,
                        rowThumbnailWidth = rowThumbnailWidth,
                        onVideoClick = onVideoClick,
                        onAuthorClick = onAuthorClick,
                        onVideoLongClick = onVideoLongClick,
                        onViewMore = onViewMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (twoColumnSections) HOME_SIDE_PADDING else 0.dp)
                    )
                }
            }

            item(key = "home-bottom-spacer", contentType = HomeItemType.SPACER, span = StaggeredGridItemSpan.FullLine) {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
    }
    }
}

@Composable
private fun HomeHeroRow(
    banner: HanimeBanner,
    layout: HomeHeroLayout,
    continueWatching: List<WatchHistory>,
    watchLater: List<WatchLaterVideo>,
    onPlayClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    onContinueClick: (WatchHistory) -> Unit,
    onMarkWatched: (WatchHistory) -> Unit,
    onRemoveContinue: (WatchHistory) -> Unit,
    onWatchLaterClick: (WatchLaterVideo) -> Unit,
    onRemoveWatchLater: (WatchLaterVideo) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HOME_SIDE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(HERO_GAP)
    ) {
        Box(modifier = Modifier.width(layout.bannerWidth)) {
            Banner(
                bannerData = banner,
                onPlayClick = onPlayClick,
                onSearchClick = onSearchClick,
                onTagClick = onSearchClick,
                height = layout.height
            )
        }
        Column(modifier = Modifier.width(layout.sideWidth)) {
            if (continueWatching.isNotEmpty()) {
                ContinueWatchingSection(
                    items = continueWatching,
                    onItemClick = onContinueClick,
                    onMarkWatched = onMarkWatched,
                    onRemove = onRemoveContinue
                )
            }
            if (watchLater.isNotEmpty()) {
                WatchLaterSection(
                    items = watchLater,
                    onItemClick = onWatchLaterClick,
                    onRemove = onRemoveWatchLater
                )
            }
        }
    }
}

@Composable
private fun SectionBlock(
    plan: SectionPlan,
    columns: Int,
    rowThumbnailWidth: Dp,
    onVideoClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onVideoLongClick: (HanimeVideo) -> Unit,
    onViewMore: (HomeSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val section = plan.section
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeSectionTitleResMap[section.title]?.let { stringResource(it) } ?: section.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onViewMore(section) }
            ) {
                Text(
                    text = stringResource(R.string.common_view_more),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        when (plan.mode) {
            SectionRenderMode.LIST_COLUMN -> plan.videos.forEach { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video.videoUrl) },
                    onAuthorClick = onAuthorClick,
                    onLongClick = { onVideoLongClick(video) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            SectionRenderMode.LIST_ROWS -> plan.rowVideos.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LIST_ROW_SPACING_DP.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { video ->
                        VideoListItem(
                            video = video,
                            onClick = { onVideoClick(video.videoUrl) },
                            onAuthorClick = onAuthorClick,
                            onLongClick = { onVideoLongClick(video) },
                            thumbnailWidth = rowThumbnailWidth,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = LIST_ITEM_H_PADDING_DP.dp, vertical = 3.dp)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            SectionRenderMode.GRID_ROWS -> plan.rowVideos.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    row.forEach { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onVideoClick(video.videoUrl) },
                            onAuthorClick = onAuthorClick,
                            onLongClick = { onVideoLongClick(video) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            SectionRenderMode.CAROUSEL -> LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(plan.videos, key = { it.videoUrl }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video.videoUrl) },
                        onAuthorClick = onAuthorClick,
                        onLongClick = { onVideoLongClick(video) },
                        targetWidth = CAROUSEL_CARD_WIDTH,
                        modifier = Modifier.width(CAROUSEL_CARD_WIDTH)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

/** 首页骨架屏：加载时模拟首页布局的占位。 */
@Composable
private fun HomeSkeletonScreen() {
    // 闪烁动画：alpha 在 0.3f ~ 0.6f 循环，时长 1000ms
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val shimmerAlpha = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        val cfg = LocalUiConfig.current
        val homeConfig = LocalHomeSectionConfig.current
        val sizeInfo = currentWindowSizeInfo()
        val twoColumnSections = remember(sizeInfo) { homeTwoColumnSections(sizeInfo) }
        val heroLayout = remember(sizeInfo, twoColumnSections) {
            homeHeroLayout(sizeInfo.widthDp, sizeInfo.heightDp, sizeInfo.contentMaxWidth, hasSideContent = twoColumnSections)
        }
        // Banner 骨架占位：宽屏与内容态同样分两栏，避免加载完成时整块换形
        if (heroLayout.twoColumn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HOME_SIDE_PADDING),
                horizontalArrangement = Arrangement.spacedBy(HERO_GAP)
            ) {
                SkeletonHeroBox(
                    shimmerAlpha = shimmerAlpha,
                    modifier = Modifier
                        .width(heroLayout.bannerWidth)
                        .height(heroLayout.height)
                )
                Column(
                    modifier = Modifier.width(heroLayout.sideWidth),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(SKELETON_HERO_SIDE_BLOCKS) {
                        SkeletonHeroBox(
                            shimmerAlpha = shimmerAlpha,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroLayout.height / SKELETON_HERO_SIDE_BLOCKS)
                        )
                    }
                }
            }
        } else {
            SkeletonHeroBox(
                shimmerAlpha = shimmerAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (heroLayout.height.value > 0f) heroLayout.height else cfg.bannerHeight)
            )
        }
        val columns = remember(sizeInfo, cfg, twoColumnSections) {
            homeSectionColumns(cfg, sizeInfo, twoColumnSections).coerceAtLeast(1)
        }
        val modes = remember(homeConfig, sizeInfo.widthClass, twoColumnSections) {
            (SKELETON_SECTION_TITLES + homeConfig.order).distinct()
                .map { renderMode(homeConfig.layoutFor(it), sizeInfo.widthClass, twoColumnSections) }
        }
        val skeletonModes = List(SKELETON_SECTION_COUNT) { index ->
            modes.getOrElse(index) { SectionRenderMode.LIST_COLUMN }
        }
        val skeletonRows = if (twoColumnSections) skeletonModes.chunked(2) else skeletonModes.map { listOf(it) }
        skeletonRows.forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (twoColumnSections) HOME_SIDE_PADDING else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(HOME_SECTION_GAP)
            ) {
                pair.forEach { mode ->
                    SkeletonSection(
                        mode = mode,
                        columns = columns,
                        itemCount = homeConfig.visibleCount,
                        shimmerAlpha = shimmerAlpha,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (twoColumnSections && pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SkeletonHeroBox(shimmerAlpha: State<Float>, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer { alpha = shimmerAlpha.value }
    )
}

@Composable
private fun SkeletonSection(
    mode: SectionRenderMode,
    columns: Int,
    itemCount: Int,
    shimmerAlpha: State<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 15.dp, vertical = 8.dp)
                .size(width = 120.dp, height = 18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .graphicsLayer { alpha = shimmerAlpha.value }
        )
        when (mode) {
            SectionRenderMode.LIST_COLUMN -> repeat(itemCount) {
                SkeletonVideoRow(shimmerAlpha = shimmerAlpha)
            }
            SectionRenderMode.LIST_ROWS -> repeat(skeletonRowCount(itemCount, columns)) {
                SkeletonCompactRow(shimmerAlpha = shimmerAlpha, columns = columns)
            }
            SectionRenderMode.GRID_ROWS -> repeat(skeletonRowCount(itemCount, columns)) {
                SkeletonGridRow(shimmerAlpha = shimmerAlpha, columns = columns)
            }
            SectionRenderMode.CAROUSEL -> SkeletonCarousel(shimmerAlpha = shimmerAlpha)
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

/** 骨架视频列表行：整宽紧凑行，左缩略图按 listThumbnailWidth / thumbnailAspectRatio 取值 + 右两行文字占位，与内容区 VideoListItem 布局一致。 */
@Composable
private fun SkeletonVideoRow(shimmerAlpha: State<Float>) {
    val cfg = LocalUiConfig.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩略图占位
        Box(
            modifier = Modifier
                .width(cfg.listThumbnailWidth)
                .aspectRatio(cfg.thumbnailAspectRatio)
                .clip(RoundedCornerShape(cfg.listThumbnailCornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .graphicsLayer { alpha = shimmerAlpha.value }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            // 标题占位（两行）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer { alpha = shimmerAlpha.value }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer { alpha = shimmerAlpha.value }
            )
        }
    }
}

@Composable
private fun SkeletonCompactRow(shimmerAlpha: State<Float>, columns: Int) {
    val cfg = LocalUiConfig.current
    val sizeInfo = currentWindowSizeInfo()
    val thumbnailWidth = rowThumbnailWidth(
        cfg = cfg,
        widthDp = sizeInfo.widthDp,
        contentMaxWidth = sizeInfo.contentMaxWidth,
        columns = columns,
        isCompactWidth = false
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LIST_ITEM_H_PADDING_DP.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(LIST_ROW_SPACING_DP.dp)
    ) {
        repeat(columns) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(thumbnailWidth)
                        .aspectRatio(cfg.thumbnailAspectRatio)
                        .clip(RoundedCornerShape(cfg.listThumbnailCornerRadius))
                        .background(MaterialTheme.colorScheme.surface)
                        .graphicsLayer { alpha = shimmerAlpha.value }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .graphicsLayer { alpha = shimmerAlpha.value }
                )
            }
        }
    }
}

@Composable
private fun SkeletonGridRow(shimmerAlpha: State<Float>, columns: Int) {
    val cfg = LocalUiConfig.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(columns) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(cfg.thumbnailAspectRatio)
                        .clip(RoundedCornerShape(cfg.cardCornerRadius))
                        .background(MaterialTheme.colorScheme.surface)
                        .graphicsLayer { alpha = shimmerAlpha.value }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .graphicsLayer { alpha = shimmerAlpha.value }
                )
            }
        }
    }
}

@Composable
private fun SkeletonCarousel(shimmerAlpha: State<Float>) {
    val cfg = LocalUiConfig.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(SKELETON_CAROUSEL_CARDS) {
            Column(modifier = Modifier.width(CAROUSEL_CARD_WIDTH)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(cfg.thumbnailAspectRatio)
                        .clip(RoundedCornerShape(cfg.cardCornerRadius))
                        .background(MaterialTheme.colorScheme.surface)
                        .graphicsLayer { alpha = shimmerAlpha.value }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .graphicsLayer { alpha = shimmerAlpha.value }
                )
            }
        }
    }
}
