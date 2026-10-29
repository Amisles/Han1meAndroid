package app.amisles.hanime.feature.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.ui.components.FullScreenOverlayDialog
import app.amisles.hanime.core.ui.components.SearchField
import app.amisles.hanime.core.ui.components.WrappingActionRow
import app.amisles.hanime.core.ui.theme.HanimeDanger
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.R

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
/** 下载页按状态分组 + 按 id 索引的一次性遍历结果，供列表与分组视图共用。 */
private data class TaskGroups(
    val downloading: List<DownloadTask>,
    val completed: List<DownloadTask>,
    val paused: List<DownloadTask>,
    val failed: List<DownloadTask>,
    val byId: Map<Int, DownloadTask>
)

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
    // 点「播放」：交给上层跳转到应用内的本地播放页，不再走外部播放器 Intent
    onPlayLocalVideo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: DownloadViewModel = hiltViewModel()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val gateState by viewModel.gateState.collectAsStateWithLifecycle()
    // 不用 rememberSaveable：面板是一次性的浮层，离开页面再回来不应保持展开
    var showStoragePanel by remember { mutableStateOf(false) }

    LaunchedEffect(showStoragePanel) {
        if (showStoragePanel) viewModel.refreshStorageInfo()
    }

    // 离开下载页时收起面板，避免返回后仍停留在一个过期的浮层上
    DisposableEffect(Unit) {
        onDispose { showStoragePanel = false }
    }

    // 媒体读取权限：卸载重装后要靠它从 MediaStore 找回上一个安装写入的视频
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.rescanDownloads() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 通知权限的弹窗结束后才轮到存储权限：系统同一时刻只处理一个请求，
        // 两个 launch 挨在一起时第二个会被静默丢弃。
        if (ContextCompat.checkSelfPermission(context, mediaPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mediaPermissionLauncher.launch(mediaPermission)
        }
    }

    // 每次进入本页都按当前授权状态决定是否申请：只用一次标志会让被拒后无法重试。
    // 已授权时这里不会弹窗，系统也会自行限制重复询问次数。
    LaunchedEffect(Unit) {
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsNotification) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (ContextCompat.checkSelfPermission(context, mediaPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mediaPermissionLauncher.launch(mediaPermission)
        }
    }

    LaunchedEffect(Unit) { viewModel.rescanDownloads() }

    val migrationExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        // 用户取消时也要清掉内存副本，否则会一直占着几 MB 直到下次导出
        if (uri == null) viewModel.consumeMigrationBytes()
        else viewModel.writeMigrationTo(uri, context)
    }

    val migrationImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.readMigrationFrom(it, context) { count ->
                Toast.makeText(
                    context,
                    context.getString(
                        if (count > 0) R.string.download_migration_import_done
                        else R.string.download_migration_import_failed,
                        count
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val migrationBytes by viewModel.migrationBytes.collectAsStateWithLifecycle()
    LaunchedEffect(migrationBytes) {
        migrationBytes?.let {
            migrationExportLauncher.launch("hanime-downloads-" + System.currentTimeMillis() + ".zip")
        }
    }

    var downloadFilter by remember { mutableStateOf(DownloadFilter.ALL) }
    var downloadSort by remember { mutableStateOf(DownloadSort.ADDED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    // 文件名搜索关键词：纯本地过滤已下载任务，输入即生效，不触发任何网络请求
    var nameQuery by rememberSaveable { mutableStateOf("") }

    // 一次遍历同时完成分组与 id 索引：原来分别做 4 次 filter + 1 次 associateBy，
    // 每次重组都要把整个任务表扫 5 遍；下载进度每 500ms 更新一次，代价随任务数线性增长。
    // 以 tasks 为 key 记忆，只有任务列表真正变化时才重算。
    val groups = remember(tasks) {
        val downloading = ArrayList<DownloadTask>()
        val completed = ArrayList<DownloadTask>()
        val paused = ArrayList<DownloadTask>()
        val failed = ArrayList<DownloadTask>()
        val byId = HashMap<Int, DownloadTask>(tasks.size * 2)
        for (task in tasks) {
            byId[task.id] = task
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> downloading += task
                DownloadStatus.COMPLETED -> completed += task
                DownloadStatus.PAUSED -> paused += task
                DownloadStatus.FAILED -> failed += task
            }
        }
        TaskGroups(downloading, completed, paused, failed, byId)
    }
    val downloadingTasks = groups.downloading
    val completedTasks = groups.completed
    val pausedTasks = groups.paused
    val failedTasks = groups.failed

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
    val visibleTasks = remember(orderedTaskIds, groups) {
        orderedTaskIds.mapNotNull { groups.byId[it] }
    }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.download_migration_export_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                QueueActionButton(
                    label = stringResource(R.string.download_migration_import),
                    onClick = {
                        migrationImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                )
                if (downloadingTasks.isNotEmpty()) {
                    QueueActionButton(
                        label = stringResource(R.string.download_queue_pause_all),
                        onClick = {
                            viewModel.pauseAll()
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.download_queue_toast_paused,
                                    downloadingTasks.size
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                if (pausedTasks.isNotEmpty()) {
                    QueueActionButton(
                        label = stringResource(R.string.download_queue_resume_all),
                        onClick = { viewModel.resumeAll() }
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
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(64.dp)
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
                            actions = DownloadItemActions(
                                onToggleSelection = {
                                    selectedIds = if (task.id in selectedIds) {
                                        selectedIds - task.id
                                    } else {
                                        selectedIds + task.id
                                    }
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
                            actions = DownloadItemActions(
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
                            actions = DownloadItemActions(
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
                            actions = DownloadItemActions(
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
