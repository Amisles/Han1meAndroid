package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import app.amisles.hanime.core.ui.components.VideoThumbnail
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatWatchedTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)
    return when {
        minutes < 1 -> "刚刚观看"
        minutes < 60 -> "$minutes 分钟前"
        hours < 24 -> "$hours 小时前"
        days < 30 -> "$days 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun HistoryScreen(
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {}
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val context = LocalContext.current
    val history by viewModel.history.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedIds = emptySet()
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
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isSelectionMode) {
                        isSelectionMode = false
                    } else {
                        onBackClick()
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isSelectionMode) "取消" else "返回",
                    tint = HanimeTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isSelectionMode) "已选 ${selectedIds.size} 项" else "观看历史",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = HanimeTextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (!isSelectionMode && history.isNotEmpty()) {
                Text(
                    text = "管理",
                    fontSize = 14.sp,
                    color = HanimePrimary,
                    modifier = Modifier
                        .clickable { isSelectionMode = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else if (isSelectionMode) {
                Text(
                    text = "全选",
                    fontSize = 14.sp,
                    color = if (selectedIds.size == history.size) HanimeTextSecondary else HanimePrimary,
                    modifier = Modifier
                        .clickable {
                            selectedIds = if (selectedIds.size == history.size) {
                                emptySet()
                            } else {
                                history.map { it.id }.toSet()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HanimePrimary)
            }
        } else if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📋",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_empty),
                        fontSize = 16.sp,
                        color = HanimeTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "观看的视频会自动记录在这里",
                        fontSize = 13.sp,
                        color = HanimeTextSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(history) { video ->
                    val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
                    val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }
                    val isSelected = video.id in selectedIds

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 8.dp)
                            .background(if (isSelectionMode && isSelected) HanimePrimary.copy(alpha = 0.1f) else HanimeCard)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) {
                                            selectedIds - video.id
                                        } else {
                                            selectedIds + video.id
                                        }
                                    } else {
                                        onVideoClick(video.videoUrl)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        viewModel.removeHistory(video.id)
                                    }
                                }
                            ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + video.id
                                    } else {
                                        selectedIds - video.id
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = HanimePrimary,
                                    uncheckedColor = HanimeTextSecondary
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        VideoThumbnail(
                            thumbnailUrl = video.thumbnailUrl,
                            emoji = emoji,
                            gradient = gradient,
                            duration = video.duration,
                            likeRate = "",
                            viewCount = "",
                            crop = true,
                            modifier = Modifier
                                .width(110.dp)
                                .height(80.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = video.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HanimeTextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (video.author.isNotEmpty()) {
                                Text(
                                    text = video.author,
                                    fontSize = 11.sp,
                                    color = HanimeTextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text(
                                text = formatWatchedTime(video.watchedAt),
                                fontSize = 10.sp,
                                color = HanimeTextSecondary.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (!isSelectionMode) {
                            IconButton(
                                onClick = { viewModel.removeHistory(video.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (isSelectionMode && selectedIds.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HanimeCard)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选择 ${selectedIds.size} 项",
                    fontSize = 14.sp,
                    color = HanimeTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "删除",
                    fontSize = 14.sp,
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showDeleteConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (!isSelectionMode && history.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HanimeCard)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${history.size} 条记录",
                    fontSize = 14.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "清空",
                    fontSize = 14.sp,
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showDeleteConfirm = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(HanimeCard, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "确认删除",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "确定删除选中的 ${selectedIds.size} 条记录吗？",
                    fontSize = 14.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = HanimeTextSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDeleteConfirm = false }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "删除",
                        fontSize = 14.sp,
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedIds.forEach { videoId ->
                                    viewModel.removeHistory(videoId)
                                }
                                isSelectionMode = false
                                showDeleteConfirm = false
                            }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showClearConfirm = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(HanimeCard, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "确认清空所有观看历史？",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = HanimeTextSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showClearConfirm = false }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "清空",
                        fontSize = 14.sp,
                        color = HanimePrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.clearHistory()
                                showClearConfirm = false
                            }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}