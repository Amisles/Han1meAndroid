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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.Banner
import app.amisles.hanime.core.ui.components.CategoryScroll
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onGenreSearch: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onViewMore: (String) -> Unit = {}
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val sections by viewModel.sections.collectAsState()
    val banner by viewModel.banner.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

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
        onViewMore = onViewMore
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
    onViewMore: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    val sectionTitleToIndex: Map<String, Int> = remember(sections, banner, isLoading) {
        val map = mutableMapOf<String, Int>()
        var idx = 0
        idx++
        idx++
        if (!isLoading) {
            if (banner != null) idx++
            sections.forEach { section ->
                map[section.title] = idx
                idx++
                idx++
            }
            idx++
        } else {
            idx++
        }
        map
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
            .statusBarsPadding()
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
            .statusBarsPadding()
    ) {
        item(key = "home-header") {
            Header(onSearchNavigate = onNavigateToSearch)
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
                        onInfoClick = { onVideoClick(it.videoUrl) },
                        onSearchClick = onSearchClick
                    )
                }
            }

            sections.forEachIndexed { i, section ->
                item(key = "home-section-title-${section.title}-$i") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 15.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = section.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HanimeTextPrimary
                        )
                        Text(
                            text = "${stringResource(R.string.common_view_more)} →",
                            fontSize = 13.sp,
                            color = HanimeTextSecondary,
                            modifier = Modifier.clickable { onViewMore(section.title) }
                        )
                    }
                }

                item(key = "home-section-row-${section.title}-$i") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(section.videos.take(8), key = { it.videoUrl }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video.videoUrl) },
                                onAuthorClick = onAuthorClick
                            )
                        }
                    }
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
            .background(HanimeBackground)
    ) {
        // Banner 骨架占位：260dp 高度，全宽，圆角
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HanimeCard)
                .alpha(alpha)
        )

        // 区块骨架（共 3 个）
        repeat(3) {
            // 区块标题骨架
            Box(
                modifier = Modifier
                    .padding(horizontal = 15.dp, vertical = 15.dp)
                    .size(width = 120.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HanimeCard)
                    .alpha(alpha)
            )

            // 横向视频卡片骨架列表
            LazyRow(
                contentPadding = PaddingValues(horizontal = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(4) {
                    SkeletonVideoCard(alpha = alpha)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * 骨架视频卡片：宽 200dp，高 210dp，包含图片占位（3:2）与两行文字占位
 */
@Composable
private fun SkeletonVideoCard(alpha: Float) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .height(210.dp)
    ) {
        // 图片占位（3:2 比例：200 x 133）
        Box(
            modifier = Modifier
                .size(width = 200.dp, height = 133.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HanimeCard)
                .alpha(alpha)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 第一行文字占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HanimeCard)
                .alpha(alpha)
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 第二行文字占位
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(HanimeCard)
                .alpha(alpha)
        )
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