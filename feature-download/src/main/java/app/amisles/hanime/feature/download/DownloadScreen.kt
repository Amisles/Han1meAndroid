package app.amisles.hanime.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import app.amisles.hanime.domain.model.DownloadRenameResult
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.ui.components.SearchField
import app.amisles.hanime.core.ui.components.HintBanner
import app.amisles.hanime.core.ui.components.WrappingActionRow
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.components.ConfirmDeleteDialog
import app.amisles.hanime.core.ui.components.BottomActionBar
import app.amisles.hanime.core.ui.components.EmptyState
import app.amisles.hanime.core.ui.components.RefreshableContent
import app.amisles.hanime.core.ui.components.BottomBarAction
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.common.AppFeatures
import app.amisles.hanime.core.ui.util.AppSnackbar

enum class DownloadFilter {
    ALL, DOWNLOADING, COMPLETED, FAILED, PAUSED
}

enum class DownloadSort {
    ADDED, NAME, SIZE, PROGRESS
}

/**
 * 下载页文件名搜索框。
 *
 * 复用 core/ui 的 [SearchField]，与搜索页完全同款：微圆角弱描边、搜索图标内嵌、回车收起键盘。
 * 唯一差异是 singleLine —— 表头高度需固定，长文件名不应把表头撑高。
 */
@Composable
private fun DownloadSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.download_search_placeholder),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun DownloadScreen(
    highlightTaskId: Int = -1,
    // 点「播放」：交给上层跳转到应用内的本地播放页，不再走外部播放器 Intent
    onPlayLocalVideo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appPrefs = LocalAppPreferences.current
    val viewModel: DownloadViewModel = hiltViewModel()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val gateState by viewModel.gateState.collectAsStateWithLifecycle()
    // 不用 rememberSaveable：面板是一次性的浮层，离开页面再回来不应保持展开
    var showStoragePanel by remember { mutableStateOf(false) }
    var showQuickDownloadHint by remember { mutableStateOf(AppFeatures.ONBOARDING_ENABLED && !appPrefs.hints.downloadQuickHintDismissed) }

    LaunchedEffect(showStoragePanel) {
        if (showStoragePanel) viewModel.refreshStorageInfo()
    }

    // 离开下载页时收起面板，避免返回后仍停留在一个过期的浮层上
    DisposableEffect(Unit) {
        onDispose { showStoragePanel = false }
    }

    val downloadLaunchers = rememberDownloadScreenLaunchers(viewModel)

    var downloadFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var downloadSort by remember { mutableStateOf(DownloadSort.ADDED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    // 文件名搜索关键词：纯本地过滤已下载任务，输入即生效，不触发任何网络请求
    var nameQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val projection = rememberDownloadListProjection(tasks, downloadFilter, downloadSort, nameQuery)
    val downloadingTasks = projection.downloadingTasks
    val completedTasks = projection.completedTasks
    val pausedTasks = projection.pausedTasks
    val failedTasks = projection.failedTasks
    val useGroupedView = projection.grouped
    val visibleTasks = projection.visibleTasks

    LaunchedEffect(highlightTaskId, tasks.size, useGroupedView) {
        if (highlightTaskId < 0) return@LaunchedEffect
        val bannerCount = if (gateState.blocked && downloadingTasks.isNotEmpty()) 1 else 0
        val index = projection.highlightIndex(highlightTaskId, bannerCount) ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
    }

    val selection = rememberDownloadSelectionState()
    val isSelectionMode = selection.isActive
    val selectedIds = selection.selectedIds
    val selectableIds = remember(projection) { projection.selectableIds() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<Int?>(null) }
    var renameTarget by remember { mutableStateOf<DownloadTask?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    RefreshableContent(
        onRefresh = {
            viewModel.rescanDownloads()
            viewModel.refreshStorageInfo()
        },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                            onClick = { selection.exit() },
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
                                .clickable { selection.toggleAll(selectableIds) }
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

            // 导入入口始终可用：换机后列表为空正是最需要导入的时候
            if (!isSelectionMode) {
                WrappingActionRow(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 2.dp)
                ) {
                    if (tasks.isNotEmpty()) {
                        QueueActionButton(
                            label = stringResource(R.string.download_migration_export),
                            onClick = {
                                viewModel.exportMigration {
                                    AppSnackbar.show(
                                        context.getString(R.string.download_migration_export_empty)
                                    )
                                }
                            }
                        )
                    }
                    QueueActionButton(
                        label = stringResource(R.string.download_migration_import),
                        onClick = {
                            downloadLaunchers.importMigration()
                        }
                    )
                    if (downloadingTasks.isNotEmpty()) {
                        QueueActionButton(
                            label = stringResource(R.string.download_queue_pause_all),
                            onClick = {
                                viewModel.pauseAll()
                                AppSnackbar.show(
                                    context.getString(
                                        R.string.download_queue_toast_paused,
                                        downloadingTasks.size
                                    )
                                )
                            }
                        )
                    }
                    if (pausedTasks.isNotEmpty()) {
                        QueueActionButton(
                            label = stringResource(R.string.download_queue_resume_all),
                            onClick = { viewModel.resumeAll() }
                        )
                    }
                    if (downloadLaunchers.notificationPermissionMissing) {
                        QueueActionButton(
                            label = stringResource(R.string.permission_notification_title),
                            onClick = downloadLaunchers.requestNotificationPermission
                        )
                    }
                    if (downloadLaunchers.mediaPermissionMissing) {
                        QueueActionButton(
                            label = stringResource(R.string.download_permission_media_action),
                            onClick = downloadLaunchers.requestMediaPermission
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
                                imageVector = Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = stringResource(R.string.download_sort_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showQuickDownloadHint) {
                        HintBanner(
                            text = stringResource(R.string.download_quick_download_hint),
                            onDismiss = {
                                appPrefs.hints.dismissDownloadQuickHint()
                                showQuickDownloadHint = false
                            },
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.download_empty),
                            hint = stringResource(R.string.download_empty_hint)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (gateState.blocked && downloadingTasks.isNotEmpty()) {
                        item(key = "download-gate-banner") {
                            DownloadGateBanner(gateState)
                        }
                    }
                    if (useGroupedView && downloadingTasks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = stringResource(R.string.download_downloading),
                                isSelectionMode = isSelectionMode,
                                tasks = downloadingTasks,
                                selectedIds = selectedIds,
                                onToggleAll = {
                                    selection.toggleAll(downloadingTasks.mapTo(HashSet()) { it.id })
                                }
                            )
                        }
                        items(downloadingTasks, key = { it.id }) { task ->
                            DownloadTaskItem(
                                task = task,
                                isSelectionMode = isSelectionMode,
                                isSelected = task.id in selectedIds,
                                actions = DownloadItemActions(
                                    onToggleSelection = {
                                        selection.toggle(task.id)
                                    },
                                    onPauseClick = { viewModel.pauseDownload(task.id) },
                                    onCancelClick = { viewModel.cancelDownload(task.id) },
                                    onMoveToFront = { viewModel.moveToFront(task.id) }
                                )
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
                                onToggleAll = {
                                    selection.toggleAll(pausedTasks.mapTo(HashSet()) { it.id })
                                }
                            )
                        }
                        items(pausedTasks, key = { it.id }) { task ->
                            DownloadTaskItem(
                                task = task,
                                isSelectionMode = isSelectionMode,
                                isSelected = task.id in selectedIds,
                                actions = DownloadItemActions(
                                    onToggleSelection = {
                                        selection.toggle(task.id)
                                    },
                                    onResumeClick = { viewModel.resumeDownload(task.id) },
                                    onCancelClick = { viewModel.cancelDownload(task.id) }
                                )
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
                                onToggleAll = {
                                    selection.toggleAll(failedTasks.mapTo(HashSet()) { it.id })
                                }
                            )
                        }
                        items(failedTasks, key = { it.id }) { task ->
                            DownloadTaskItem(
                                task = task,
                                isSelectionMode = isSelectionMode,
                                isSelected = task.id in selectedIds,
                                actions = DownloadItemActions(
                                    onToggleSelection = {
                                        selection.toggle(task.id)
                                    },
                                    onRetryClick = { viewModel.resumeDownload(task.id) },
                                    onCancelClick = { viewModel.cancelDownload(task.id) }
                                )
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
                                onToggleAll = {
                                    selection.toggleAll(completedTasks.mapTo(HashSet()) { it.id })
                                }
                            )
                        }
                        items(completedTasks, key = { it.id }) { task ->
                            DownloadTaskItem(
                                task = task,
                                isSelectionMode = isSelectionMode,
                                isSelected = task.id in selectedIds,
                                actions = DownloadItemActions(
                                    onToggleSelection = {
                                        selection.toggle(task.id)
                                    },
                                    onPlayClick = { onPlayLocalVideo(task.filePath) },
                                    onRenameClick = { renameTarget = task },
                                    onDeleteClick = {
                                        // 删除已完成任务会连带删除本地文件，先确认
                                        deleteTargetId = task.id
                                        showDeleteConfirm = true
                                    }
                                )
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
                                    actions = DownloadItemActions(
                                        onToggleSelection = { selection.toggle(task.id) },
                                        onPauseClick = { viewModel.pauseDownload(task.id) },
                                        onResumeClick = { viewModel.resumeDownload(task.id) },
                                        onCancelClick = { viewModel.cancelDownload(task.id) },
                                        onRetryClick = { viewModel.resumeDownload(task.id) },
                                        onPlayClick = { onPlayLocalVideo(task.filePath) },
                                        onRenameClick = { renameTarget = task },
                                        onDeleteClick = {
                                            // 删除已完成任务会连带删除本地文件，先确认
                                            deleteTargetId = task.id
                                            showDeleteConfirm = true
                                        }
                                    )
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
    }

    if (isSelectionMode && selectedIds.isNotEmpty()) {
        BottomActionBar(
            label = stringResource(R.string.search_selected_count, selectedIds.size)
        ) {
            BottomBarAction(
                text = stringResource(R.string.common_delete),
                color = MaterialTheme.colorScheme.error,
                onClick = {
                    deleteTargetId = null
                    showDeleteConfirm = true
                }
            )
        }
    }

    if (!isSelectionMode && tasks.isNotEmpty()) {
        BottomActionBar(
            label = stringResource(R.string.download_task_count, tasks.size),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            if (failedTasks.isNotEmpty()) {
                BottomBarAction(
                    text = stringResource(R.string.download_retry_all),
                    horizontalPadding = 12.dp,
                    onClick = {
                        viewModel.retryAllFailed()
                        AppSnackbar.show(
                            context.getString(R.string.download_retry_all_toast, failedTasks.size)
                        )
                    }
                )
            }
            BottomBarAction(
                text = stringResource(R.string.common_manage),
                onClick = { selection.enter() }
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.download_delete_title),
            message = if (deleteTargetId != null) {
                stringResource(R.string.download_delete_message)
            } else {
                stringResource(R.string.download_delete_message_multiple, selectedIds.size)
            },
            onDismiss = {
                deleteTargetId = null
                showDeleteConfirm = false
            },
            onConfirm = {
                if (deleteTargetId != null) {
                    viewModel.cancelDownload(deleteTargetId!!)
                } else {
                    selectedIds.forEach { taskId ->
                        viewModel.cancelDownload(taskId)
                    }
                    selection.exit()
                }
                deleteTargetId = null
                showDeleteConfirm = false
            },
            messageTextAlign = TextAlign.Center
        )
    }

    val renameTask = renameTarget
    if (renameTask != null) {
        DownloadRenameDialog(
            currentTitle = renameTask.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                renameTarget = null
                viewModel.renameDownload(renameTask.id, newTitle) { result ->
                    AppSnackbar.show(
                        when (result) {
                            DownloadRenameResult.SUCCESS ->
                                context.getString(R.string.download_rename_done, newTitle)
                            DownloadRenameResult.EMPTY_NAME ->
                                context.getString(R.string.download_rename_empty)
                            DownloadRenameResult.NAME_TAKEN ->
                                context.getString(R.string.download_rename_taken)
                            DownloadRenameResult.FILE_MISSING ->
                                context.getString(R.string.download_file_not_exist)
                            DownloadRenameResult.NOT_COMPLETED, DownloadRenameResult.FAILED ->
                                context.getString(R.string.download_rename_failed)
                        }
                    )
                }
            }
        )
    }

    // 面板展开时铺一层透明遮罩：点击空白处即收起。
    // 遮罩不拦截内容的滚动（只是吞掉点击），所以列表仍可正常滑动。
    if (showStoragePanel) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showStoragePanel = false }
        )
    }

    // 存储面板入口：悬浮在内容之上，不占用列表空间；点开后以气泡形式展示用量
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 86.dp)
    ) {
        if (showStoragePanel) {
            storageInfo?.let { info ->
                DownloadStorageBubble(
                    info = info,
                    onClearOrphans = {
                        viewModel.clearOrphanPartmaps { viewModel.refreshStorageInfo() }
                    },
                    onDismiss = { showStoragePanel = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 56.dp)
                        .widthIn(max = 300.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = { showStoragePanel = !showStoragePanel },
            containerColor = if (showStoragePanel) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (showStoragePanel) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(
                imageVector = Icons.Outlined.Storage,
                contentDescription = stringResource(R.string.download_storage_title),
                modifier = Modifier.size(22.dp)
            )
        }
    }
    }
}

@Composable
private fun QueueActionButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    Text(
        text = label,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
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
