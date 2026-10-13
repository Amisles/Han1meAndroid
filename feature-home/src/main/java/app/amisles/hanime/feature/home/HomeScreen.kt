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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.Banner
import app.amisles.hanime.core.ui.components.CategoryScroll
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.LocalHomeSectionConfig
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.core.ui.model.homeSectionTitleResMap
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import kotlinx.coroutines.launch

/** 横滑布局的卡片宽度。 */
private val CAROUSEL_CARD_WIDTH = 200.dp

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
): List<SectionPlan> = homeConfig.applyOrder(sections).map { section ->
    val mode = renderMode(homeConfig.layoutFor(section.title), isCompactWidth)
    val videos = section.videos.take(homeConfig.visibleCount)
    val rowVideos = when (mode) {
        SectionRenderMode.LIST_ROWS, SectionRenderMode.GRID_ROWS -> videos.chunked(columns)
        SectionRenderMode.LIST_COLUMN, SectionRenderMode.CAROUSEL -> emptyList()
    }
    val rowKeyPrefix = if (mode == SectionRenderMode.GRID_ROWS) "home-grid-row" else "home-row"
    SectionPlan(
        section = section,
        mode = mode,
        videos = videos,
        videoKeys = videos.map { "${section.title}#${it.videoUrl}" },
        rowVideos = rowVideos,
        rowKeys = rowVideos.indices.map { "$rowKeyPrefix-${section.title}-$it" }
    )
}

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (String) -> Unit = {},
    onProfileClick: (() -> Unit)? = null
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val banner by viewModel.banner.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    HomeScreenContent(
        sections = sections,
        banner = banner,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        error = error,
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
    banner: HanimeBanner?,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (String) -> Unit = {},
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
    val sectionTitleToIndex: Map<String, Int> = remember(plans, banner, isLoading, error, sections) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        idx++ // header
        idx++ // category
        if (!isLoading) {
            if (error != null && sections.isNotEmpty()) idx++ // 刷新失败提示行
            if (banner != null) idx++ // banner
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

            plans.forEachIndexed { i, plan ->
                val section = plan.section
                item(key = "home-section-title-${section.title}-$i", contentType = HomeItemType.SECTION_TITLE) {
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
                            modifier = Modifier.clickable { onViewMore(section.title) }
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { video ->
                                        VideoListItem(
                                            video = video,
                                            onClick = { onVideoClick(video.videoUrl) },
                                            onAuthorClick = onAuthorClick,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 5.dp, vertical = 3.dp)
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
                        item(key = "home-carousel-${section.title}-$i", contentType = HomeItemType.CAROUSEL) {
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

                item(key = "home-section-spacer-${section.title}-$i", contentType = HomeItemType.SPACER) {
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
    val alpha by transition.animateFloat(
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
                .alpha(alpha)
        )

        // 区块骨架（共 3 个）：标题 + 紧凑列表行
        repeat(3) {
            // 区块标题骨架
            Box(
                modifier = Modifier
                    .padding(horizontal = 15.dp, vertical = 8.dp)
                    .size(width = 120.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )

            // 紧凑列表行骨架
            repeat(5) {
                SkeletonVideoRow(alpha = alpha)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/** 骨架视频列表行：整宽紧凑行，左缩略图（120x90）+ 右两行文字占位，与内容区 VideoListItem 布局一致。 */
@Composable
private fun SkeletonVideoRow(alpha: Float) {
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
                .alpha(alpha)
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
                    .alpha(alpha)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )
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
