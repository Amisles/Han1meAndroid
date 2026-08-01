package app.amisles.hanime.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.core.ui.components.Banner
import app.amisles.hanime.core.ui.components.CategoryScroll
import app.amisles.hanime.core.ui.components.ErrorView
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.theme.HanimeBackground
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
    val viewModel: HomeViewModel = viewModel()
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
        if (error != null && sections.isEmpty() && !isLoading) {
            ErrorView(
                message = error,
                onRetry = onRefresh
            )
            return@PullToRefreshBox
        }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HanimePrimary)
                }
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
                            text = "查看更多 →",
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