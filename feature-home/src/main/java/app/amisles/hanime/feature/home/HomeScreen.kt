package app.amisles.hanime.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.Banner
import app.amisles.hanime.core.ui.components.CategoryScroll
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.model.homeSectionTitleResMap
import kotlinx.coroutines.launch

/**
 * 每个首页分区在纵向列表里展示的视频条数（紧凑列表行，替代原横滑大卡片）
 */
private const val HOME_SECTION_VISIBLE_COUNT = 6

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
    val error by viewModel.error.collectAsStateWithLifecycle()

    HomeScreenContent(
        sections = sections,
        banner = banner,
        isLoading = isLoading,
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

    val sectionTitleToIndex: Map<String, Int> = remember(sections, banner, isLoading) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        idx++ // header
        idx++ // category
        if (!isLoading) {
            if (banner != null) idx++ // banner
            sections.forEach { section ->
                map[section.title] = idx // 标题 item 的索引
                idx += 1 // 标题
                idx += section.videos.take(HOME_SECTION_VISIBLE_COUNT).size // 视频行
                idx += 1 // 分区之间的 spacer
            }
            idx++ // bottom spacer
        } else {
            idx++ // skeleton
        }
        map
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        item(key = "home-header") {
            Header(
                onSearchNavigate = onNavigateToSearch,
                onProfileClick = onProfileClick
            )
        }

        item(key = "home-category") {
            CategoryScroll(
                onCategorySelected = { category ->
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
            item(key = "home-loading") {
                HomeSkeletonScreen()
            }
        } else if (error != null && sections.isEmpty()) {
            item(key = "home-error") {
                KaomojiErrorView(
                    message = error,
                    onRetry = onRefresh,
                    modifier = Modifier.fillParentMaxSize()
                )
            }
        } else {
            banner?.let {
                item(key = "home-banner") {
                    Banner(
                        bannerData = it,
                        onPlayClick = { onVideoClick(it.videoUrl) },
                        onSearchClick = onSearchClick
                    )
                }
            }

            sections.forEachIndexed { i, section ->
                val visibleVideos = section.videos.take(HOME_SECTION_VISIBLE_COUNT)
                item(key = "home-section-title-${section.title}-$i") {
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

                items(visibleVideos, key = { it.videoUrl }) { video ->
                    VideoListItem(
                        video = video,
                        onClick = { onVideoClick(video.videoUrl) },
                        onAuthorClick = onAuthorClick,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                item(key = "home-section-spacer-${section.title}-$i") {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            item(key = "home-bottom-spacer") {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
    }
}

/**
 * 首页骨架屏：在加载时模拟首页布局的占位
 */
@Composable
private fun HomeSkeletonScreen() {
    // 闪烁动画：alpha 在 0.3f 到 0.6f 之间循环，时长 1000ms
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
        // Banner 骨架占位：260dp 高度，全宽，圆角
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )

        // 区块骨架（共 3 个），与内容布局一致：标题 + 紧凑列表行
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

/**
 * 骨架视频列表行：整宽紧凑行，左缩略图（120x90）+ 右两行文字占位，
 * 与内容区的 VideoListItem 布局一致。
 */
@Composable
private fun SkeletonVideoRow(alpha: Float) {
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
                .size(width = 120.dp, height = 90.dp)
                .clip(RoundedCornerShape(6.dp))
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

    val avgSize = visibleItems.map { it.size }.average().toDouble().toInt()
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
