package app.amisles.hanime.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.domain.model.BatchVideoItem
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimePrimary
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadScreen(
    onBackClick: () -> Unit
) {
    val viewModel: BatchDownloadViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = HanimeBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HanimeBackground)
            )
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
            SearchSection(
                authorIdInput = state.authorIdInput,
                isSearching = state.isSearching,
                onInputChange = { viewModel.updateAuthorIdInput(it) },
                onSearch = { viewModel.searchAuthor() }
            )

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
                    totalVideos = state.videos.size,
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
private fun SearchSection(
    authorIdInput: String,
    isSearching: Boolean,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = authorIdInput,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.batch_author_id_placeholder), color = Color.Gray) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = HanimePrimary,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onSearch,
            enabled = !isSearching,
            colors = ButtonDefaults.buttonColors(containerColor = HanimePrimary)
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        }
    }
}

@Composable
private fun AuthorInfoSection(
    authorName: String,
    authorId: String,
    totalVideos: Int,
    currentPage: Int,
    totalPages: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.batch_author_name, authorName),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ID: $authorId",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(R.string.batch_loading_page, currentPage),
                color = HanimePrimary,
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
            .then(
                if (isDisabled) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDisabled -> Color(0xFF2A2A2A)
                video.isSelected -> Color(0xFF1A3A1A)
                else -> Color(0xFF2A2A2A)
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
                        video.isDownloaded -> HanimePrimary
                        isDisabled -> Color.Gray
                        video.isSelected -> HanimePrimary
                        else -> Color.Gray
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
                        color = if (isDisabled) Color.Gray else Color.White,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.duration.isNotEmpty()) {
                        Text(
                            text = video.duration,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    // 下载状态显示
                    when {
                        video.isDownloaded -> {
                            Text(
                                text = stringResource(R.string.batch_status_downloaded),
                                color = HanimePrimary,
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
                                color = HanimePrimary,
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
                                        color = HanimePrimary,
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.batch_loading_qualities),
                                        color = HanimePrimary,
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
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                        video.qualities.getOrNull(video.selectedQualityIndex)?.let { quality ->
                                            Text(
                                                text = quality.quality,
                                                color = HanimePrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.batch_qualities_available, video.qualities.size),
                                        color = Color.Gray,
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
                            tint = HanimePrimary,
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
        title = { Text(stringResource(R.string.batch_select_quality), color = Color.White) },
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
                            color = if (index == selectedIndex) HanimePrimary else Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = HanimePrimary)
            }
        },
        containerColor = Color(0xFF2A2A2A)
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
            CircularProgressIndicator(color = HanimePrimary)
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HanimePrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("加载更多")
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onSelectAll,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(stringResource(R.string.common_select_all))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.search_selected_count, selectedCount),
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDownload,
                enabled = selectedCount > 0 && !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedCount > 0) HanimePrimary else Color.Gray
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Color.Red,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_close), tint = Color.White)
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
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.batch_intro_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.batch_intro_input_id),
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}