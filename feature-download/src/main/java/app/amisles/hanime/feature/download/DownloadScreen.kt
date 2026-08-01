package app.amisles.hanime.feature.download

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import app.amisles.hanime.data.download.DownloadManagerHolder
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.ui.components.Header
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun DownloadScreen(
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val downloadManager = DownloadManagerHolder.getInstance(context)
    val tasks by downloadManager.tasks.collectAsState()

    val downloadingTasks = tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
    val completedTasks = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val pausedTasks = tasks.filter { it.status == DownloadStatus.PAUSED }
    val failedTasks = tasks.filter { it.status == DownloadStatus.FAILED }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            if (isSelectionMode) {
                IconButton(
                    onClick = { isSelectionMode = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "取消",
                        tint = HanimeTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "已选 ${selectedIds.size} 项",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "全选",
                    fontSize = 14.sp,
                    color = if (selectedIds.size == tasks.size) HanimeTextSecondary else HanimePrimary,
                    modifier = Modifier
                        .clickable {
                            selectedIds = if (selectedIds.size == tasks.size) {
                                emptySet()
                            } else {
                                tasks.map { it.id }.toSet()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Header(title = "下载管理", onSearchNavigate = { onNavigate("search") })
            }
        }

        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "📥",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "暂无下载任务",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = HanimeTextSecondary
                )
                Text(
                    text = "在视频详情页点击下载按钮开始下载",
                    fontSize = 12.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                if (downloadingTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "下载中",
                            isSelectionMode = isSelectionMode,
                            tasks = downloadingTasks,
                            selectedIds = selectedIds,
                            onToggleAll = { allSelected ->
                                selectedIds = if (allSelected) {
                                    selectedIds - downloadingTasks.map { it.id }.toSet()
                                } else {
                                    selectedIds + downloadingTasks.map { it.id }.toSet()
                                }
                            }
                        )
                    }
                    items(downloadingTasks) { task ->
                        DownloadTaskItem(
                            task = task,
                            isSelectionMode = isSelectionMode,
                            isSelected = task.id in selectedIds,
                            onToggleSelection = {
                                selectedIds = if (task.id in selectedIds) {
                                    selectedIds - task.id
                                } else {
                                    selectedIds + task.id
                                }
                            },
                            onPauseClick = { downloadManager.pauseDownload(task.id) },
                            onCancelClick = { downloadManager.cancelDownload(task.id) }
                        )
                    }
                }

                if (pausedTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "已暂停",
                            isSelectionMode = isSelectionMode,
                            tasks = pausedTasks,
                            selectedIds = selectedIds,
                            onToggleAll = { allSelected ->
                                selectedIds = if (allSelected) {
                                    selectedIds - pausedTasks.map { it.id }.toSet()
                                } else {
                                    selectedIds + pausedTasks.map { it.id }.toSet()
                                }
                            }
                        )
                    }
                    items(pausedTasks) { task ->
                        DownloadTaskItem(
                            task = task,
                            isSelectionMode = isSelectionMode,
                            isSelected = task.id in selectedIds,
                            onToggleSelection = {
                                selectedIds = if (task.id in selectedIds) {
                                    selectedIds - task.id
                                } else {
                                    selectedIds + task.id
                                }
                            },
                            onResumeClick = { downloadManager.resumeDownload(task.id) },
                            onCancelClick = { downloadManager.cancelDownload(task.id) }
                        )
                    }
                }

                if (failedTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "下载失败",
                            isSelectionMode = isSelectionMode,
                            tasks = failedTasks,
                            selectedIds = selectedIds,
                            onToggleAll = { allSelected ->
                                selectedIds = if (allSelected) {
                                    selectedIds - failedTasks.map { it.id }.toSet()
                                } else {
                                    selectedIds + failedTasks.map { it.id }.toSet()
                                }
                            }
                        )
                    }
                    items(failedTasks) { task ->
                        DownloadTaskItem(
                            task = task,
                            isSelectionMode = isSelectionMode,
                            isSelected = task.id in selectedIds,
                            onToggleSelection = {
                                selectedIds = if (task.id in selectedIds) {
                                    selectedIds - task.id
                                } else {
                                    selectedIds + task.id
                                }
                            },
                            onRetryClick = { downloadManager.resumeDownload(task.id) },
                            onCancelClick = { downloadManager.cancelDownload(task.id) }
                        )
                    }
                }

                if (completedTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "已完成",
                            isSelectionMode = isSelectionMode,
                            tasks = completedTasks,
                            selectedIds = selectedIds,
                            onToggleAll = { allSelected ->
                                selectedIds = if (allSelected) {
                                    selectedIds - completedTasks.map { it.id }.toSet()
                                } else {
                                    selectedIds + completedTasks.map { it.id }.toSet()
                                }
                            }
                        )
                    }
                    items(completedTasks) { task ->
                        DownloadTaskItem(
                            task = task,
                            isSelectionMode = isSelectionMode,
                            isSelected = task.id in selectedIds,
                            onToggleSelection = {
                                selectedIds = if (task.id in selectedIds) {
                                    selectedIds - task.id
                                } else {
                                    selectedIds + task.id
                                }
                            },
                            onPlayClick = { playVideoFile(context, task.filePath) },
                            onDeleteClick = { downloadManager.cancelDownload(task.id) }
                        )
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
                if (!isSelectionMode && tasks.isNotEmpty()) {
                    Text(
                        text = "管理",
                        fontSize = 14.sp,
                        color = HanimePrimary,
                        modifier = Modifier
                            .clickable { isSelectionMode = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
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

    if (!isSelectionMode && tasks.isNotEmpty()) {
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
                    text = "${tasks.size} 个任务",
                    fontSize = 14.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "管理",
                    fontSize = 14.sp,
                    color = HanimePrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { isSelectionMode = true }
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
                    text = "确定删除选中的 ${selectedIds.size} 个任务吗？\n已下载的文件将被删除。",
                    fontSize = 14.sp,
                    color = HanimeTextSecondary,
                    textAlign = TextAlign.Center,
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
                                selectedIds.forEach { taskId ->
                                    downloadManager.cancelDownload(taskId)
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
}

@Composable
private fun SectionHeader(
    title: String,
    isSelectionMode: Boolean,
    tasks: List<DownloadTask>,
    selectedIds: Set<Int>,
    onToggleAll: (Boolean) -> Unit
) {
    val allSelected = tasks.all { it.id in selectedIds }
    val someSelected = tasks.any { it.id in selectedIds } && !allSelected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { onToggleAll(allSelected) },
                colors = CheckboxDefaults.colors(
                    checkedColor = HanimePrimary,
                    uncheckedColor = HanimeTextSecondary
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = HanimeTextPrimary
        )
    }
}

@Composable
fun DownloadTaskItem(
    task: DownloadTask,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val progress = if (task.totalBytes > 0) {
        task.downloadedBytes.toFloat() / task.totalBytes.toFloat()
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 4.dp)
            .background(
                if (isSelectionMode && isSelected) HanimePrimary.copy(alpha = 0.1f) else HanimeCard,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
            .clickable(enabled = isSelectionMode) { onToggleSelection() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = HanimePrimary,
                    uncheckedColor = HanimeTextSecondary
                ),
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (task.status == DownloadStatus.COMPLETED && task.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = task.thumbnailUrl,
                    contentDescription = task.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = task.quality,
                    fontSize = 11.sp,
                    color = HanimePrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = task.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HanimeTextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (task.status == DownloadStatus.DOWNLOADING) {
                Text(
                    text = "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(task.totalBytes)}",
                    fontSize = 11.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .background(HanimeBackground, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(HanimePrimary, RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = "下载中",
                    fontSize = 10.sp,
                    color = HanimePrimary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            } else if (task.status == DownloadStatus.COMPLETED) {
                Text(
                    text = formatFileSize(task.totalBytes),
                    fontSize = 11.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "已完成",
                    fontSize = 10.sp,
                    color = HanimePrimary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            } else if (task.status == DownloadStatus.PENDING) {
                Text(
                    text = "等待下载...",
                    fontSize = 11.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (task.status == DownloadStatus.FAILED) {
                Text(
                    text = formatFileSize(task.downloadedBytes) + " / " + formatFileSize(task.totalBytes),
                    fontSize = 11.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "下载失败",
                    fontSize = 10.sp,
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier.padding(top = 3.dp)
                )
            } else if (task.status == DownloadStatus.PAUSED) {
                Text(
                    text = "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(task.totalBytes)}",
                    fontSize = 11.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "已暂停",
                    fontSize = 10.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        if (!isSelectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    task.status == DownloadStatus.DOWNLOADING -> {
                        IconButton(
                            onClick = onPauseClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停",
                                tint = HanimeTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    task.status == DownloadStatus.PAUSED -> {
                        IconButton(
                            onClick = onResumeClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "继续",
                                tint = HanimePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    task.status == DownloadStatus.FAILED -> {
                        IconButton(
                            onClick = onRetryClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "重试",
                                tint = HanimePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    task.status == DownloadStatus.COMPLETED -> {
                        IconButton(
                            onClick = onPlayClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = HanimePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (task.status == DownloadStatus.COMPLETED) {
                            onDeleteClick()
                        } else {
                            onCancelClick()
                        }
                    },
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
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun playVideoFile(context: android.content.Context, filePath: String) {
    val file = File(filePath)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "选择播放器").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}