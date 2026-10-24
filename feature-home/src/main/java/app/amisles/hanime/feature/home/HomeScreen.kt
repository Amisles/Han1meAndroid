package app.amisles.hanime.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.Banner
import app.amisles.hanime.core.ui.components.CategoryScroll
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.components.KaomojiErrorView
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
import kotlinx.coroutines.launch

/** 横滑布局的卡片宽度。 */
private val CAROUSEL_CARD_WIDTH = 200.dp

/** 行内多列布局的项间距、单项水平外边距与缩略图占卡宽比例；换算列宽时须与渲染处一致。 */
private const val LIST_ROW_SPACING_DP = 10f
private const val LIST_ITEM_H_PADDING_DP = 5f
private const val LIST_ROW_THUMB_RATIO = 0.42f

private const val SKELETON_SECTION_COUNT = 3
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

/** 分区在 LazyColumn 中的实际渲染形态；[HomeSectionLayout.AUTO] 由窗口宽度决定，故需与用户可选模式区分。 */
private enum class SectionRenderMode { LIST_COLUMN, LIST_ROWS, GRID_ROWS, CAROUSEL }

private enum class HomeItemType { HEADER, CATEGORY, SKELETON, ERROR, REFRESH_ERROR, BANNER, SECTION_TITLE, VIDEO_LIST, VIDEO_ROW, GRID_ROW, CAROUSEL, SPACER }

private data class SectionPlan(
    val section: HomeSection,
    val mode: SectionRenderMode,
    val videos: List<HanimeVideo>,
    val videoKeys: List<String>,
    val rowVideos: List<List<HanimeVideo>>,
    val rowKeys: List<String>
) {
    /** 该分区在 LazyColumn 中占用的视频 item 数，索引映射与渲染共用，避免两处漂移。 */
    val itemCount: Int
        get() = when (mode) {
            SectionRenderMode.LIST_COLUMN -> videos.size
            SectionRenderMode.LIST_ROWS, SectionRenderMode.GRID_ROWS -> rowVideos.size
            SectionRenderMode.CAROUSEL -> 1
        }
}

private fun renderMode(configured: HomeSectionLayout, isCompactWidth: Boolean): SectionRenderMode =
    when (configured) {
        HomeSectionLayout.LIST -> SectionRenderMode.LIST_COLUMN
        HomeSectionLayout.GRID -> SectionRenderMode.GRID_ROWS
        HomeSectionLayout.CAROUSEL -> SectionRenderMode.CAROUSEL
        HomeSectionLayout.AUTO ->
            if (isCompactWidth) SectionRenderMode.LIST_COLUMN else SectionRenderMode.LIST_ROWS
    }

private fun buildPlans(
    sections: List<HomeSection>,
    homeConfig: HomeSectionConfig,
    isCompactWidth: Boolean,
    columns: Int
): List<SectionPlan> = homeConfig.applyOrder(sections).mapIndexed { index, section ->
    val mode = renderMode(homeConfig.layoutFor(section.title), isCompactWidth)
    val videos = section.videos.take(homeConfig.visibleCount)
    val rowVideos = when (mode) {
        SectionRenderMode.LIST_ROWS, SectionRenderMode.GRID_ROWS -> videos.chunked(columns)
        SectionRenderMode.LIST_COLUMN, SectionRenderMode.CAROUSEL -> emptyList()
    }
    val rowKeyPrefix = if (mode == SectionRenderMode.GRID_ROWS) "home-grid-row" else "home-row"
    val sectionKey = sectionKey(section, index)
    SectionPlan(
        section = section,
        mode = mode,
        videos = videos,
        videoKeys = videos.map { "$sectionKey#${it.videoUrl}" },
        rowVideos = rowVideos,
        rowKeys = rowVideos.indices.map { "$rowKeyPrefix-$sectionKey-$it" }
    )
}

/** LazyColumn 的 item key 前缀：标题不足以区分同名分区，追加下标保证全局唯一。 */
private fun sectionKey(section: HomeSection, index: Int): String = "${section.title}-$index"

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (HomeSection) -> Unit = {},
    onProfileClick: (() -> Unit)? = null
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val siteUrl by LocalAppPreferences.current.baseUrlFlow.collectAsStateWithLifecycle()
    // 人机校验通过后自动重拉：否则用户要手动下拉一次才知道已经能用了
    val solvedTick by CloudflareChallenge.solvedTick.collectAsStateWithLifecycle()
    LaunchedEffect(solvedTick) {
        if (solvedTick > 0) viewModel.loadHomeData()
    }

    HomeScreenContent(
        sections = sections,
        continueWatching = continueWatching,
        banner = banner,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        error = error,
        siteUrl = siteUrl,
        onRefresh = { viewModel.loadHomeData() },
        onVideoClick = onVideoClick,
        onSearchClick = onSearchClick,
        onGenreSearch = onGenreSearch,
        onNavigateToSearch = onNavigateToSearch,
        onAuthorClick = onAuthorClick,
        onViewMore = onViewMore,
        onProfileClick = onProfileClick
    )
}

@Composable
fun HomeScreenContent(
    sections: List<HomeSection>,
    continueWatching: List<WatchHistory> = emptyList(),
    banner: HanimeBanner?,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    error: String? = null,
    siteUrl: String? = null,
    onRefresh: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (HomeSection) -> Unit = {},
    onProfileClick: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    val sizeInfo = currentWindowSizeInfo()
    val cfg = LocalUiConfig.current
    val homeConfig = LocalHomeSectionConfig.current
    var selectedCategory by remember { mutableStateOf(categories[0].label) }

    // 与渲染分支共用同一个判定，避免两处条件漂移
    val isCompactWidth = sizeInfo.widthClass == WindowWidthSizeClass.Compact
    val columns = sizeInfo.gridColumns(cfg).coerceAtLeast(1)
    val plans = remember(sections, homeConfig, isCompactWidth, columns) {
        buildPlans(sections, homeConfig, isCompactWidth, columns)
    }
    // 多列时缩略图须按列宽等比缩小：固定 150dp 会把文字区压到几 dp。比例取 0.42，
    // 与手机单列下缩略图占卡宽的比例（150 / 340）一致
    val rowThumbnailWidth = remember(cfg.listThumbnailWidth, columns, sizeInfo, isCompactWidth) {
        rowThumbnailWidth(cfg, sizeInfo.widthDp, sizeInfo.contentMaxWidth, columns, isCompactWidth)
    }
    val sectionTitleToIndex: Map<String, Int> = remember(plans, banner, isLoading, error, sections) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        idx++ // header
        idx++ // category
        if (!isLoading) {
            if (error != null && sections.isNotEmpty()) idx++ // 刷新失败提示行
            if (banner != null) idx++ // banner
            if (continueWatching.isNotEmpty()) idx++ // 继续观看
            plans.forEach { plan ->
                map[plan.section.title] = idx // 标题 item 的索引
                // 标题 + 视频区（条数由 plan.itemCount 给出，与渲染分支同源）+ 分区之间的 spacer
                idx += 1 + plan.itemCount + 1
            }
            idx++ // bottom spacer
        } else {
            idx++ // skeleton
        }
        map
    }

    PullToRefreshBox(
        // 只响应刷新态：首屏加载由内容区的骨架屏表达，两者不再共用一个标志位
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ResponsiveContent {
        // 状态栏内边距已由外层 PullToRefreshBox 提供，这里不再叠加，否则顶部留白翻倍
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        item(key = "home-header", contentType = HomeItemType.HEADER) {
            Header(
                siteUrl = siteUrl,
                onSearchNavigate = onNavigateToSearch,
                onProfileClick = onProfileClick
            )
        }

        item(key = "home-category", contentType = HomeItemType.CATEGORY) {
            CategoryScroll(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = category
                    val targetIndex = sectionTitleToIndex[category]
                    if (targetIndex != null) {
                        scope.launch {
                            listState.smoothScrollToItem(
                                index = targetIndex,
                                offset = -20,
                                durationMs = 700
                            )
                        }
                    } else {
                        onGenreSearch(category)
                    }
                }
            )
        }

        if (isLoading) {
            item(key = "home-loading", contentType = HomeItemType.SKELETON) {
                HomeSkeletonScreen()
            }
        } else if (error != null && sections.isEmpty()) {
            item(key = "home-error", contentType = HomeItemType.ERROR) {
                KaomojiErrorView(
                    message = error,
                    onRetry = onRefresh,
                    modifier = Modifier.fillParentMaxSize()
                )
            }
        } else {
            if (error != null && sections.isNotEmpty()) {
                item(key = "home-refresh-error", contentType = HomeItemType.REFRESH_ERROR) {
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

            banner?.let {
                item(key = "home-banner", contentType = HomeItemType.BANNER) {
                    Banner(
                        bannerData = it,
                        onPlayClick = { onVideoClick(it.videoUrl) },
                        onSearchClick = onSearchClick
                    )
                }
            }

            if (continueWatching.isNotEmpty()) {
                item(key = "home-continue-watching", contentType = HomeItemType.BANNER) {
                    ContinueWatchingSection(
                        items = continueWatching,
                        onItemClick = { history -> onVideoClick(history.videoUrl) },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            plans.forEachIndexed { i, plan ->
                val section = plan.section
                item(key = "home-section-title-${sectionKey(section, i)}", contentType = HomeItemType.SECTION_TITLE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = homeSectionTitleResMap[section.title]?.let { stringResource(it) }
                                ?: section.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${stringResource(R.string.common_view_more)} →",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onViewMore(section) }
                        )
                    }
                }

                when (plan.mode) {
                    SectionRenderMode.LIST_COLUMN -> {
                        // key 必须整条 LazyColumn 全局唯一：同一部影片会同时出现在「最新上市 / 本日排行」
                        // 等排序型分区里，只用 videoUrl 作 key 会触发「Key was already used」崩溃。
                        // 分区标题在 ViewModel 中已按标题去重，故「标题#url」在列表中唯一。
                        items(
                            count = plan.videos.size,
                            key = { plan.videoKeys[it] },
                            contentType = { HomeItemType.VIDEO_LIST }
                        ) { index ->
                            val video = plan.videos[index]
                            VideoListItem(
                                video = video,
                                onClick = { onVideoClick(video.videoUrl) },
                                onAuthorClick = onAuthorClick,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    SectionRenderMode.LIST_ROWS -> {
                        // 按列数分行渲染，避免单列在大屏下空旷
                        plan.rowVideos.forEachIndexed { rowIndex, row ->
                            // 与单列分支同理：行 item 也必须带分区前缀，避免跨分区撞 key
                            item(key = plan.rowKeys[rowIndex], contentType = HomeItemType.VIDEO_ROW) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(LIST_ROW_SPACING_DP.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { video ->
                                        VideoListItem(
                                            video = video,
                                            onClick = { onVideoClick(video.videoUrl) },
                                            onAuthorClick = onAuthorClick,
                                            thumbnailWidth = rowThumbnailWidth,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(
                                                    horizontal = LIST_ITEM_H_PADDING_DP.dp,
                                                    vertical = 3.dp
                                                )
                                        )
                                    }
                                    repeat(columns - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    SectionRenderMode.GRID_ROWS -> {
                        plan.rowVideos.forEachIndexed { rowIndex, row ->
                            item(key = plan.rowKeys[rowIndex], contentType = HomeItemType.GRID_ROW) {
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
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(columns - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    SectionRenderMode.CAROUSEL -> {
                        // 独立 LazyRow，key 只需在本行内唯一
                        item(key = "home-carousel-${sectionKey(section, i)}", contentType = HomeItemType.CAROUSEL) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(plan.videos, key = { it.videoUrl }) { video ->
                                    VideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video.videoUrl) },
                                        onAuthorClick = onAuthorClick,
                                        targetWidth = CAROUSEL_CARD_WIDTH,
                                        modifier = Modifier.width(CAROUSEL_CARD_WIDTH)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "home-section-spacer-${sectionKey(section, i)}", contentType = HomeItemType.SPACER) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            item(key = "home-bottom-spacer", contentType = HomeItemType.SPACER) {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
    }
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Banner 骨架占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .graphicsLayer { alpha = shimmerAlpha.value }
        )

        val cfg = LocalUiConfig.current
        val homeConfig = LocalHomeSectionConfig.current
        val sizeInfo = currentWindowSizeInfo()
        val isCompactWidth = sizeInfo.widthClass == WindowWidthSizeClass.Compact
        val columns = sizeInfo.gridColumns(cfg).coerceAtLeast(1)
        val modes = remember(homeConfig, isCompactWidth) {
            (SKELETON_SECTION_TITLES + homeConfig.order).distinct()
                .map { renderMode(homeConfig.layoutFor(it), isCompactWidth) }
        }

        repeat(SKELETON_SECTION_COUNT) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 15.dp, vertical = 8.dp)
                    .size(width = 120.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer { alpha = shimmerAlpha.value }
            )

            val mode = modes.getOrElse(index) { SectionRenderMode.LIST_COLUMN }
            val items = homeConfig.visibleCount
            when (mode) {
                SectionRenderMode.LIST_COLUMN -> repeat(items) {
                    SkeletonVideoRow(shimmerAlpha = shimmerAlpha)
                }
                SectionRenderMode.LIST_ROWS -> repeat(skeletonRowCount(items, columns)) {
                    SkeletonCompactRow(shimmerAlpha = shimmerAlpha, columns = columns)
                }
                SectionRenderMode.GRID_ROWS -> repeat(skeletonRowCount(items, columns)) {
                    SkeletonGridRow(shimmerAlpha = shimmerAlpha, columns = columns)
                }
                SectionRenderMode.CAROUSEL -> SkeletonCarousel(shimmerAlpha = shimmerAlpha)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/** 骨架视频列表行：整宽紧凑行，左缩略图（120x90）+ 右两行文字占位，与内容区 VideoListItem 布局一致。 */
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

private suspend fun LazyListState.smoothScrollToItem(
    index: Int,
    offset: Int = 0,
    durationMs: Int = 700,
    easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing
) {
    require(index >= 0) { "index must be >= 0, but was $index" }
    require(durationMs > 0) { "durationMs must be > 0, but was $durationMs" }

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        animateScrollToItem(index, offset)
        return
    }

    val firstVisible = visibleItems.first()
    val lastVisible = visibleItems.last()
    val targetInVisibleRange = index in firstVisible.index..lastVisible.index

    if (targetInVisibleRange) {
        val targetItem = visibleItems.firstOrNull { it.index == index } ?: run {
            animateScrollToItem(index, offset)
            return
        }
        val deltaPixels = (targetItem.offset + offset).toFloat()
        if (deltaPixels != 0f) {
            animateScrollBy(deltaPixels, tween(durationMs, easing = easing))
        }
        return
    }

    val avgSize = visibleItems.map { it.size }.average().toInt()
    if (avgSize <= 0) {
        animateScrollToItem(index, offset)
        return
    }

    val firstIndex = firstVisible.index
    val firstOffset = firstVisible.offset
    val estimatedDistancePixels =
        ((index - firstIndex) * avgSize + firstOffset + offset).toFloat()

    if (estimatedDistancePixels != 0f) {
        animateScrollBy(estimatedDistancePixels, tween(durationMs, easing = easing))
    }

    val visibleAfter = layoutInfo.visibleItemsInfo
    val target = visibleAfter.firstOrNull { it.index == index }
    if (target != null) {
        val fineTune = (target.offset + offset).toFloat()
        if (fineTune != 0f) {
            animateScrollBy(
                fineTune,
                tween(
                    durationMillis = (durationMs * 0.2).toInt().coerceAtLeast(80),
                    easing = easing
                )
            )
        }
    } else {
        animateScrollToItem(index, offset)
    }
}
