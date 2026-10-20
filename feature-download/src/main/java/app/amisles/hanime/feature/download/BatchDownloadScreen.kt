package app.amisles.hanime.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.components.SearchField
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.domain.model.BatchVideoItem
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadScreen(
    onBackClick: () -> Unit
) {
    val viewModel: BatchDownloadViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // 一次性提示事件：用 Unit 作为 key，避免随状态变化重启收集；每条事件恰好消费一次
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 应用开启了 edge-to-edge，自绘 topBar（非 TopAppBar）必须自己让出状态栏高度，
            // 否则标题与搜索框会顶到状态栏下面被截断
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    // 搜索框固定在下方一行，标题独占标题行剩余宽度
                    Text(
                        text = stringResource(R.string.batch_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }

                // 搜索框：与搜索页同款，固定在标题下方一行并占满整行，点击不位移、不展开；
                // 它与下方内容（空态提示 / 作者信息 / 视频列表）之间统一留 8dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 15.dp, end = 15.dp, bottom = BATCH_SEARCH_BOTTOM_GAP)
                ) {
                    BatchSearchBar(
                        value = state.authorIdInput,
                        isSearching = state.isSearching,
                        onInputChange = { viewModel.updateAuthorIdInput(it) },
                        onSearch = {
                            if (state.authorIdInput.isNotBlank()) viewModel.searchAuthor()
                        },
                        onClear = { viewModel.updateAuthorIdInput("") }
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        bottomBar = {
            if (state.videos.isNotEmpty()) {
                BottomActionBar(
                    selectedCount = state.selectedCount,
                    isDownloading = state.isDownloading,
                    onSelectAll = { viewModel.toggleAllSelection() },
                    onDownload = { viewModel.startBatchDownload() },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { viewModel.clearError() }
                )
            }

            if (state.authorName.isNotEmpty()) {
                AuthorInfoSection(
                    authorName = state.authorName,
                    authorId = state.authorId,
                    currentPage = state.currentPage,
                    totalPages = state.totalPages
                )
            }

            if (state.videos.isNotEmpty()) {
                VideoListSection(
                    videos = state.videos,
                    onLoadMore = { viewModel.loadMore() },
                    hasNextPage = state.hasNextPage,
                    isLoadMore = state.isLoadMore,
                    onVideoClick = { viewModel.toggleVideoSelection(it) },
                    onQualityChange = { videoId, qualityIndex ->
                        viewModel.updateVideoQuality(videoId, qualityIndex)
                    },
                    downloadingVideoIds = state.downloadingVideoIds
                )
            } else if (!state.isSearching && state.authorIdInput.isEmpty()) {
                EmptyStateHint()
            }
        }
    }
}

@Composable
private fun AuthorInfoSection(
    authorName: String,
    authorId: String,
    currentPage: Int,
    totalPages: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.batch_author_name, authorName),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ID: $authorId",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.batch_loading_page, currentPage),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun VideoListSection(
    videos: List<BatchVideoItem>,
    onLoadMore: () -> Unit,
    hasNextPage: Boolean,
    isLoadMore: Boolean,
    onVideoClick: (String) -> Unit,
    onQualityChange: (String, Int) -> Unit,
    downloadingVideoIds: Set<String>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.videoId }) { video ->
            VideoItem(
                video = video,
                isDownloading = downloadingVideoIds.contains(video.videoId),
                onClick = { onVideoClick(video.videoId) },
                onQualitySelected = { qualityIndex ->
                    onQualityChange(video.videoId, qualityIndex)
                }
            )
        }

        if (hasNextPage) {
            item {
                LoadMoreButton(
                    isLoading = isLoadMore,
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun VideoItem(
    video: BatchVideoItem,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onQualitySelected: (Int) -> Unit
) {
    var showQualityDialog by remember { mutableStateOf(false) }

    val isDisabled = video.isDownloaded || video.isDownloading || isDownloading

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .surfaceEdge(RoundedCornerShape(12.dp))
            .then(
                if (isDisabled) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDisabled -> MaterialTheme.colorScheme.surface
                video.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        video.isDownloaded -> Icons.Default.CheckCircle
                        isDisabled -> Icons.Default.Download
                        video.isSelected -> Icons.Default.CheckCircle
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = when {
                        video.isDownloaded -> MaterialTheme.colorScheme.primary
                        isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        video.isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = video.title,
                        color = if (isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.duration.isNotEmpty()) {
                        Text(
                            text = video.duration,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    // 下载状态显示
                    when {
                        video.isDownloaded -> {
                            Text(
                                text = stringResource(R.string.batch_status_downloaded),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        video.isDownloading -> {
                            Text(
                                text = stringResource(R.string.batch_status_downloading),
                                color = Color(0xFF4CAF50),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        isDownloading -> {
                            Text(
                                text = stringResource(R.string.batch_status_added),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        video.isSelected && !isDisabled -> {
                            if (video.isLoadingQualities) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.batch_loading_qualities),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp
                                    )
                                }
                            } else if (video.qualities.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { showQualityDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.batch_quality_label) + ": ",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                        video.qualities.getOrNull(video.selectedQualityIndex)?.let { quality ->
                                            Text(
                                                text = quality.quality,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.batch_qualities_available, video.qualities.size),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 已下载遮罩层
            if (video.isDownloaded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.batch_status_downloaded),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // 画质选择对话框
    if (showQualityDialog && video.qualities.isNotEmpty()) {
        QualitySelectionDialog(
            qualities = video.qualities,
            selectedIndex = video.selectedQualityIndex,
            onDismiss = { showQualityDialog = false },
            onSelect = { index ->
                onQualitySelected(index)
                showQualityDialog = false
            }
        )
    }
}

@Composable
private fun QualitySelectionDialog(
    qualities: List<app.amisles.hanime.domain.model.DownloadQuality>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_select_quality), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                qualities.forEachIndexed { index, quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = quality.quality,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun LoadMoreButton(
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.search_load_more))
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    selectedCount: Int,
    isDownloading: Boolean,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSelectAll,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(stringResource(R.string.common_select_all))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.search_selected_count, selectedCount),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDownload,
                enabled = selectedCount > 0 && !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.batch_start_download))
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                // errorContainer 背景上的前景色应为 onErrorContainer，用 error 对比度不足
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun EmptyStateHint() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.batch_intro_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.batch_intro_input_id),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

/** 搜索框与下方内容（错误提示 / 作者信息 / 视频列表）之间的统一间距。 */
private val BATCH_SEARCH_BOTTOM_GAP = 8.dp

/**
 * 批量下载页的搜索框：与搜索页同款 [SearchField]，并叠加本页需要的状态。
 * 回车即提交（[SearchField] 把 IME 搜索键映射到 onSearch），因此不再单独放搜索按钮。
 */
@Composable
private fun BatchSearchBar(
    value: String,
    isSearching: Boolean,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        SearchField(
            value = value,
            onValueChange = onInputChange,
            placeholder = stringResource(R.string.batch_search_author_placeholder),
            onSearch = onSearch,
            onClear = onClear,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isSearching) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
