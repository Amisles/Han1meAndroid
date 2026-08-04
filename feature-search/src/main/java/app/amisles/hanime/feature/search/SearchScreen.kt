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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.ErrorView
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.model.categoryList
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary

val filterTypes = listOf("全部") + categoryList

data class SortOption(val label: String, val apiValue: String)

val sortOptions = listOf(
    SortOption("最新上市", "最新上市"),
    SortOption("最新上传", "最新上傳"),
    SortOption("本日排行", "本日排行"),
    SortOption("本周排行", "本週排行"),
    SortOption("本月排行", "本月排行"),
    SortOption("观看次数", "觀看次數"),
    SortOption("点赞比例", "點讚比例"),
    SortOption("时长最长", "時長最長"),
    SortOption("他们在看", "他們在看")
)

val genreApiValues = mapOf(
    "里番" to "裏番",
    "泡面番" to "泡麵番",
    "Motion Anime" to "Motion Anime",
    "3DCG" to "3DCG",
    "2.5D动画" to "2.5D",
    "2D动画" to "2D動畫",
    "AI生成" to "AI生成",
    "MMD" to "MMD",
    "Cosplay" to "Cosplay",
    "新番预告" to "新番預告"
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
    val query by viewModel.query.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val sortValue by viewModel.sort.collectAsState()
    val genreValue by viewModel.genre.collectAsState()

    val localQuery = remember { mutableStateOf(query) }
    val selectedFilter = remember { mutableStateOf("全部") }
    val selectedSort = remember { mutableStateOf(sortOptions[0]) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

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
            val match = filterTypes.firstOrNull { it.equals(initialGenre, ignoreCase = true) }
            if (match != null && match != "全部") {
                selectedFilter.value = match
                viewModel.setGenre(genreApiValues[match] ?: match)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HanimeBackground)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = localQuery.value,
                onValueChange = {
                    localQuery.value = it
                },
                placeholder = { Text(stringResource(R.string.search_placeholder), color = HanimeTextSecondary) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 15.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black,
                    focusedBorderColor = HanimePrimary,
                    unfocusedBorderColor = HanimeCard,
                    cursorColor = HanimePrimary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    if (localQuery.value.isNotEmpty()) {
                        Text(
                            text = "✕",
                            fontSize = 18.sp,
                            color = HanimeTextSecondary,
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
                    .background(HanimePrimary)
                    .clickable {
                        viewModel.setQuery(localQuery.value)
                        viewModel.executeSearch()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HanimeBackground)
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterTypes) { filter ->
                    val isSelected = selectedFilter.value == filter
                    Text(
                        text = filter,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isSelected) Color.White else HanimeTextPrimary,
                        modifier = Modifier
                            .background(if (isSelected) HanimePrimary else HanimeCard)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedFilter.value = filter
                                val apiValue = if (filter == "全部") null else genreApiValues[filter] ?: filter
                                viewModel.setGenre(apiValue)
                            },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
                    color = HanimeTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                val sortDropdownWidth = 140.dp
                Box {
                    Row(
                        modifier = Modifier
                            .width(sortDropdownWidth)
                            .background(HanimeCard, RoundedCornerShape(16.dp))
                            .clickable { sortMenuExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = selectedSort.value.label,
                            fontSize = 13.sp,
                            color = HanimePrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "选择排序",
                            tint = HanimePrimary,
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
                                        text = option.label,
                                        fontSize = 14.sp,
                                        color = if (isSelected) HanimePrimary else HanimeTextSecondary,
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
                                            tint = HanimePrimary,
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
                CircularProgressIndicator(color = HanimePrimary)
            }
        } else if (error != null && videos.isEmpty()) {
            ErrorView(
                message = error!!,
                onRetry = { viewModel.executeSearch() },
                modifier = Modifier.padding(top = 60.dp)
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
                        color = HanimeTextPrimary
                    )
                    Text(
                        text = "清空",
                        fontSize = 12.sp,
                        color = HanimeTextSecondary,
                        modifier = Modifier.clickable { viewModel.clearSearchHistory() }
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(searchHistory) { historyQuery ->
                        // 单条历史项：搜索图标 + 关键词 + 删除按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HanimeCard, RoundedCornerShape(8.dp))
                                .clickable {
                                    localQuery.value = historyQuery
                                    viewModel.setQuery(historyQuery)
                                    viewModel.executeSearch()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 搜索图标
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = HanimeTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            // 历史关键词
                            Text(
                                text = historyQuery,
                                fontSize = 14.sp,
                                color = HanimeTextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // 删除按钮，保证足够的点击区域
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { viewModel.removeSearchHistory(historyQuery) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除",
                                    tint = HanimeTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                        text = "输入关键词或选择分类开始搜索",
                        fontSize = 14.sp,
                        color = HanimeTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
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
                            .background(HanimeCard, RoundedCornerShape(8.dp))
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
                                color = HanimeTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (video.author.isNotEmpty()) {
                                Text(
                                    text = video.author,
                                    fontSize = 10.sp,
                                    color = HanimePrimary,
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
                                    color = HanimeTextSecondary
                                )
                                Text(
                                    text = video.likeRate,
                                    fontSize = 9.sp,
                                    color = HanimeTextSecondary
                                )
                                Text(
                                    text = video.viewCount,
                                    fontSize = 9.sp,
                                    color = HanimeTextSecondary
                                )
                            }
                        }
                    }
                }

                if (isLoadingMore) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = HanimePrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.search_loading_more),
                                fontSize = 13.sp,
                                color = HanimeTextSecondary
                            )
                        }
                    }
                } else if (hasMore && videos.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "第${currentPage}/${totalPages}页",
                                fontSize = 13.sp,
                                color = HanimeTextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.search_load_more),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = HanimePrimary,
                                modifier = Modifier
                                    .clickable { viewModel.loadMore() }
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else if (videos.isNotEmpty() && totalPages > 1) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_more),
                                fontSize = 13.sp,
                                color = HanimeTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}