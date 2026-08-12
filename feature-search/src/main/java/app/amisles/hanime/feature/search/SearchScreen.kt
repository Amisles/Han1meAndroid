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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.model.Category
import app.amisles.hanime.core.ui.model.categories
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients

val allCategory = Category("", R.string.search_all, "")
val filterTypes = listOf(allCategory) + categories

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

    // 滚动接近底部自动加载下一页
    androidx.compose.runtime.LaunchedEffect(listState, videos, isLoadingMore, hasMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible >= totalItems - 3
        }.collect { shouldLoad ->
            if (shouldLoad && hasMore && !isLoadingMore && videos.isNotEmpty()) {
                viewModel.loadMore()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialKeyword) {
        if (!initialKeyword.isNullOrEmpty()) {
            val cleanedKeyword = initialKeyword.trimStart('#').trim()
            localQuery.value = cleanedKeyword
            viewModel.setQuery(cleanedKeyword)
            viewModel.executeSearch()
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialGenre) {
        if (!initialGenre.isNullOrBlank()) {
            val match = categories.firstOrNull {
                it.label.equals(initialGenre, ignoreCase = true) || it.apiValue.equals(initialGenre, ignoreCase = true)
            }
            if (match != null) {
                selectedFilter.value = match
                viewModel.setGenre(match.apiValue)
                viewModel.executeSearch()
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
                viewModel.setSort(match.apiValue)
                viewModel.executeSearch()
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
                        viewModel.setQuery(localQuery.value)
                        viewModel.executeSearch()
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
                        viewModel.setQuery(localQuery.value)
                        viewModel.executeSearch()
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
                                    viewModel.setQuery(historyQuery)
                                    viewModel.executeSearch()
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
        } else {
            val sizeInfo = currentWindowSizeInfo()
            if (sizeInfo.widthClass == WindowWidthSizeClass.Compact) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(videos) { video ->
                    val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
                    val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                            .clickable { onVideoClick(video.videoUrl) },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoThumbnail(
                            thumbnailUrl = video.thumbnailUrl,
                            emoji = emoji,
                            gradient = gradient,
                            duration = "",
                            likeRate = "",
                            viewCount = "",
                            crop = true,
                            modifier = Modifier
                                .width(120.dp)
                                .height(90.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = video.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (video.author.isNotEmpty()) {
                                Text(
                                    text = video.author,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { onAuthorClick(video.author) }
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = video.duration,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = video.likeRate,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = video.viewCount,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 加载中指示器（自动触发加载时显示）
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (!hasMore && videos.isNotEmpty() && totalPages > 1) {
                    item {
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
                }
            }
            } else {
                // 平板：多列网格，按列数分行渲染 VideoCard
                val columns = sizeInfo.gridColumns
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    videos.chunked(columns).forEach { rowVideos ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowVideos.forEach { video ->
                                    VideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video.videoUrl) },
                                        onAuthorClick = onAuthorClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowVideos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else if (!hasMore && videos.isNotEmpty() && totalPages > 1) {
                        item {
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
                    }
                }
            }
        }
    }
    }
}
