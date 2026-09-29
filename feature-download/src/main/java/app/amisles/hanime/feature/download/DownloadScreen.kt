package app.amisles.hanime.feature.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Sort
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.ui.components.FullScreenOverlayDialog
import app.amisles.hanime.core.ui.theme.HanimeDanger
import app.amisles.hanime.core.ui.R
import coil3.compose.AsyncImage

enum class DownloadFilter {
    ALL, DOWNLOADING, COMPLETED, FAILED, PAUSED
}

enum class DownloadSort {
    ADDED, NAME, SIZE, PROGRESS
}

/**
 * 筛选 / 排序用的稳定键。
 * tasks 每秒会因进度产生多个新列表；抽出「筛选与排序真正依赖的字段」作不可变键，
 * 进度以外的字段没变时即可复用排序结果，避免每秒重排整表。
 */
private data class TaskOrderKey(
    val id: Int,
    val status: DownloadStatus,
    val title: String,
    val totalBytes: Long,
    val progress: Float
)

/**
 * 下载页文件名搜索框。
 *
 * 样式与搜索页 SearchScreen 的搜索框逐项对齐：24dp 圆角并裁剪、surface 容器色、
 * 聚焦时 primary 描边、15sp 正文、右侧 ✕ 清除、输入法回车键为「搜索」。
 * 唯一差异是 singleLine —— 表头高度需固定，长文件名不应把表头撑高。
 */
@Composable
private fun DownloadSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.download_search_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // 提示文案必须单行省略：否则搜索框被压窄时它会折行，把表头撑高
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            // 本地过滤随输入实时生效，回车仅收起键盘
            onSearch = { keyboardController?.hide() }
        ),
        shape = RoundedCornerShape(24.dp),
        textStyle = TextStyle(
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
            if (query.isNotEmpty()) {
                Text(
                    text = "✕",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onQueryChange("") }
                )
            }
        }
    )
}

@Composable
fun DownloadScreen(
    // 点「播放」：交给上层跳转到应用内的本地播放页，不再走外部播放器 Intent
    onPlayLocalVideo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: DownloadViewModel = hiltViewModel()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝仅导致通知不可见，无需额外处理 */ }

    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!notificationPermissionRequested) {
            notificationPermissionRequested = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var downloadFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var downloadSort by remember { mutableStateOf(DownloadSort.ADDED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    // 文件名搜索关键词：纯本地过滤已下载任务，输入即生效，不触发任何网络请求
    var nameQuery by remember { mutableStateOf("") }

    val downloadingTasks = tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
    val completedTasks = tasks.filter { it.status == DownloadStatus.COMPLETED }
    val pausedTasks = tasks.filter { it.status == DownloadStatus.PAUSED }
    val failedTasks = tasks.filter { it.status == DownloadStatus.FAILED }

    // 筛选 + 排序后的可见任务：先取排序/筛选依据的稳定键，再按 id 映射回最新 Task 对象，
    // 这样进度更新不会触发整表重排，而界面进度仍是实时的
    val taskOrderKeys = remember(tasks, downloadSort) {
        tasks.map { task ->
            TaskOrderKey(
                id = task.id,
                status = task.status,
                title = task.title,
                totalBytes = task.totalBytes,
                // 仅「按进度排序」时把进度纳入键；否则进度变化不应引起重排
                progress = if (downloadSort == DownloadSort.PROGRESS && task.totalBytes > 0) {
                    task.downloadedBytes.toFloat() / task.totalBytes
                } else {
                    0f
                }
            )
        }
    }
    val orderedTaskIds = remember(taskOrderKeys, downloadFilter, downloadSort, nameQuery) {
        val byStatus = when (downloadFilter) {
            DownloadFilter.ALL -> taskOrderKeys
            DownloadFilter.DOWNLOADING -> taskOrderKeys.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            }
            DownloadFilter.COMPLETED -> taskOrderKeys.filter { it.status == DownloadStatus.COMPLETED }
            DownloadFilter.FAILED -> taskOrderKeys.filter { it.status == DownloadStatus.FAILED }
            DownloadFilter.PAUSED -> taskOrderKeys.filter { it.status == DownloadStatus.PAUSED }
        }
        // 文件名搜索：按子串匹配且忽略大小写；关键词为空白时不参与过滤
        val keyword = nameQuery.trim()
        val base = if (keyword.isEmpty()) {
            byStatus
        } else {
            byStatus.filter { it.title.contains(keyword, ignoreCase = true) }
        }
        when (downloadSort) {
            DownloadSort.ADDED -> base.sortedByDescending { it.id }
            DownloadSort.NAME -> base.sortedBy { it.title.lowercase() }
            DownloadSort.SIZE -> base.sortedByDescending { it.totalBytes }
            DownloadSort.PROGRESS -> base.sortedByDescending { it.progress }
        }.map { it.id }
    }
    val tasksById = tasks.associateBy { it.id }
    val visibleTasks = orderedTaskIds.mapNotNull { tasksById[it] }
    // 搜索态必须退化为扁平列表：分组视图按状态整段渲染原始列表，不经过关键词过滤
    val useGroupedView = downloadFilter == DownloadFilter.ALL &&
        downloadSort == DownloadSort.ADDED &&
        nameQuery.isBlank()

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<Int?>(null) }

    // 选择模式下「全选 / 取消全选」的作用域必须与当前可见条目一致。
    // 否则筛选后再进入选择模式，会出现「选中了屏幕上看不见的任务 → 删除时连带删掉不可见项」。
    val selectableIds = remember(useGroupedView, tasks, visibleTasks) {
        (if (useGroupedView) tasks else visibleTasks).mapTo(HashSet()) { it.id }
    }

    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) {
            selectedIds = emptySet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 标题与搜索框同行。英文 "Download Manager"、日文 "ダウンロード管理" 这类长标题在
        // 大字号下会几乎吃满整行，把搜索框压到极窄 —— 其提示文案随之折行，表头被撑高。
        // 因此给标题设「最多占内容区一半」的上限（超出走省略号），剩余宽度全部让给搜索框；
        // 标题与提示文案都限单行省略，表头高度便不再随语言与字号变化。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                // 原先此处的 padding 与 Header 组件内部的 padding 叠加成了 30/20，
                // 现收敛为单层：顶部 12dp 与搜索页表头对齐，底部收到 2dp 以缩短与下方内容的间距
                .padding(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 2.dp)
        ) {
            // 常规字号下英文标题约 150dp，不足一半宽度，此上限不生效；只有标题确实要吃掉
            // 半行以上（大字号 / 窄屏）时才截断，保证搜索框始终拿到一半以上的宽度
            val titleMaxWidth = maxWidth * 0.5f
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    IconButton(
                        onClick = { isSelectionMode = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_cancel),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.search_selected_count, selectedIds.size),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.common_select_all),
                        fontSize = 14.sp,
                        color = if (selectedIds.containsAll(selectableIds)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                selectedIds = if (selectedIds.containsAll(selectableIds)) {
                                    emptySet()
                                } else {
                                    selectableIds
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.download_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = titleMaxWidth)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    DownloadSearchField(
                        query = nameQuery,
                        onQueryChange = { nameQuery = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 筛选 + 排序控制栏（非选择模式且有任务时展示）
        if (!isSelectionMode && tasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DownloadFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = downloadFilter == filter,
                        onClick = { downloadFilter = filter },
                        label = { Text(stringResource(downloadFilterLabel(filter))) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box {
                    Row(
                        modifier = Modifier
                            .clickable { sortMenuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sort,
                            contentDescription = stringResource(R.string.download_sort_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(downloadSortLabel(downloadSort)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DownloadSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(stringResource(downloadSortLabel(sort))) },
                                onClick = {
                                    downloadSort = sort
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
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
                    text = stringResource(R.string.download_empty),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.download_empty_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                if (useGroupedView && downloadingTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.download_downloading),
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
                    items(downloadingTasks, key = { it.id }) { task ->
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
                            onPauseClick = { viewModel.pauseDownload(task.id) },
                            onCancelClick = { viewModel.cancelDownload(task.id) }
                        )
                    }
                }

                if (useGroupedView && pausedTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.download_paused),
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
                    items(pausedTasks, key = { it.id }) { task ->
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
                            onResumeClick = { viewModel.resumeDownload(task.id) },
                            onCancelClick = { viewModel.cancelDownload(task.id) }
                        )
                    }
                }

                if (useGroupedView && failedTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.download_failed),
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
                    items(failedTasks, key = { it.id }) { task ->
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
                            onRetryClick = { viewModel.resumeDownload(task.id) },
                            onCancelClick = { viewModel.cancelDownload(task.id) }
                        )
                    }
                }

                if (useGroupedView && completedTasks.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.download_completed),
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
                    items(completedTasks, key = { it.id }) { task ->
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
                            onPlayClick = { onPlayLocalVideo(task.filePath) },
                            onDeleteClick = {
                                // 删除已完成任务会连带删除本地文件，先确认
                                deleteTargetId = task.id
                                showDeleteConfirm = true
                            }
                        )
                    }
                }

                // 非分组视图（已筛选 / 已排序）：扁平渲染可见列表，状态图标由 DownloadTaskItem 内部按状态处理
                if (!useGroupedView) {
                    if (visibleTasks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(
                                        if (nameQuery.isBlank()) {
                                            R.string.download_filter_empty
                                        } else {
                                            R.string.download_search_empty
                                        }
                                    ),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(visibleTasks, key = { it.id }) { task ->
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
                                onPauseClick = { viewModel.pauseDownload(task.id) },
                                onResumeClick = { viewModel.resumeDownload(task.id) },
                                onCancelClick = { viewModel.cancelDownload(task.id) },
                                onRetryClick = { viewModel.resumeDownload(task.id) },
                                onPlayClick = { onPlayLocalVideo(task.filePath) },
                                onDeleteClick = {
                                    // 删除已完成任务会连带删除本地文件，先确认
                                    deleteTargetId = task.id
                                    showDeleteConfirm = true
                                }
                            )
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
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_selected_count, selectedIds.size),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.common_delete),
                    fontSize = 14.sp,
                    color = HanimeDanger,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            deleteTargetId = null // 批量删除标记
                            showDeleteConfirm = true
                        }
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
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.download_task_count, tasks.size),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (failedTasks.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.download_retry_all),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable {
                                viewModel.retryAllFailed()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.download_retry_all_toast, failedTasks.size),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.common_manage),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { isSelectionMode = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        FullScreenOverlayDialog(
            onDismiss = {
                deleteTargetId = null
                showDeleteConfirm = false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.download_delete_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = if (deleteTargetId != null) {
                        stringResource(R.string.download_delete_message)
                    } else {
                        stringResource(R.string.download_delete_message_multiple, selectedIds.size)
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                deleteTargetId = null
                                showDeleteConfirm = false
                            }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.common_delete),
                        fontSize = 14.sp,
                        color = HanimeDanger,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (deleteTargetId != null) {
                                    // 单条删除：已完成任务会连带删除本地文件
                                    viewModel.cancelDownload(deleteTargetId!!)
                                } else {
                                    selectedIds.forEach { taskId ->
                                        viewModel.cancelDownload(taskId)
                                    }
                                    isSelectionMode = false
                                }
                                deleteTargetId = null
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
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
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
            .shadow(
                elevation = if (isSystemInDarkTheme()) 0.dp else 2.dp,
                shape = RoundedCornerShape(8.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.18f)
            )
            .background(
                if (isSelectionMode && isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
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
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                // quality 为官网画质全称（可能较长），限制两行 + 省略号并居中，避免撑破缩略图位
                Text(
                    text = task.quality,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
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
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (task.status == DownloadStatus.DOWNLOADING) {
                Text(
                    text = "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(task.totalBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
                Text(
                    text = stringResource(R.string.download_downloading),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            } else if (task.status == DownloadStatus.COMPLETED) {
                Text(
                    text = formatFileSize(task.totalBytes),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.download_completed),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            } else if (task.status == DownloadStatus.PENDING) {
                Text(
                    text = stringResource(R.string.download_pending),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (task.status == DownloadStatus.FAILED) {
                Text(
                    text = formatFileSize(task.downloadedBytes) + " / " + formatFileSize(task.totalBytes),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.download_failed),
                    fontSize = 10.sp,
                    color = HanimeDanger,
                    modifier = Modifier.padding(top = 3.dp)
                )
                // 展示细分失败原因（如网络超时、HTTP 4xx/5xx）
                if (task.errorMessage.isNotBlank()) {
                    Text(
                        text = task.errorMessage,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else if (task.status == DownloadStatus.PAUSED) {
                Text(
                    text = "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(task.totalBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.download_paused),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                contentDescription = stringResource(R.string.download_pause),
                                tint = MaterialTheme.colorScheme.onBackground,
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
                                contentDescription = stringResource(R.string.download_resume),
                                tint = MaterialTheme.colorScheme.primary,
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
                                contentDescription = stringResource(R.string.common_retry),
                                tint = MaterialTheme.colorScheme.primary,
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
                                contentDescription = stringResource(R.string.common_play),
                                tint = MaterialTheme.colorScheme.primary,
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
                        contentDescription = stringResource(R.string.common_delete),
                        tint = HanimeDanger,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun downloadFilterLabel(filter: DownloadFilter): Int = when (filter) {
    DownloadFilter.ALL -> R.string.download_filter_all
    DownloadFilter.DOWNLOADING -> R.string.download_filter_downloading
    DownloadFilter.COMPLETED -> R.string.download_filter_completed
    DownloadFilter.FAILED -> R.string.download_filter_failed
    DownloadFilter.PAUSED -> R.string.download_filter_paused
}

private fun downloadSortLabel(sort: DownloadSort): Int = when (sort) {
    DownloadSort.ADDED -> R.string.download_sort_added
    DownloadSort.NAME -> R.string.download_sort_name
    DownloadSort.SIZE -> R.string.download_sort_size
    DownloadSort.PROGRESS -> R.string.download_sort_progress
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