package app.amisles.hanime.feature.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask

internal data class DownloadTaskGroups(
    val downloading: List<DownloadTask>,
    val completed: List<DownloadTask>,
    val paused: List<DownloadTask>,
    val failed: List<DownloadTask>,
    val byId: Map<Int, DownloadTask>
)

/**
 * 筛选 / 排序用的稳定键。
 * tasks 每秒会因进度产生多个新列表；抽出「筛选与排序真正依赖的字段」作不可变键，
 * 进度以外的字段没变时即可复用排序结果，避免每秒重排整表。
 */
internal data class DownloadOrderKey(
    val id: Int,
    val status: DownloadStatus,
    val title: String,
    val totalBytes: Long,
    // 仅「按进度排序」时把进度纳入键；否则进度变化不应引起重排
    val progress: Float
)

internal data class DownloadListProjection(
    val groups: DownloadTaskGroups,
    val visibleTasks: List<DownloadTask>,
    /** 搜索态必须退化为扁平列表：分组视图按状态整段渲染原始列表，不经过关键词过滤。 */
    val grouped: Boolean
) {
    val downloadingTasks: List<DownloadTask> get() = groups.downloading
    val completedTasks: List<DownloadTask> get() = groups.completed
    val pausedTasks: List<DownloadTask> get() = groups.paused
    val failedTasks: List<DownloadTask> get() = groups.failed

    /** 选择模式的全选作用域必须与当前可见条目一致，否则会删掉筛选后看不见的任务。 */
    fun selectableIds(): Set<Int> =
        if (grouped) groups.byId.keys else visibleTasks.mapTo(HashSet()) { it.id }

    fun highlightIndex(taskId: Int, leadingItems: Int): Int? {
        if (grouped) {
            var cursor = leadingItems
            for (group in listOf(downloadingTasks, pausedTasks, failedTasks, completedTasks)) {
                if (group.isEmpty()) continue
                cursor += 1
                val at = group.indexOfFirst { it.id == taskId }
                if (at >= 0) return cursor + at
                cursor += group.size
            }
            return null
        }
        val at = visibleTasks.indexOfFirst { it.id == taskId }
        return if (at >= 0) leadingItems + at else null
    }
}

// 一次遍历同时完成分组与 id 索引：下载进度每 500ms 更新一次，避免每次重组扫五遍任务表。
internal fun groupDownloadTasks(tasks: List<DownloadTask>): DownloadTaskGroups {
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
    return DownloadTaskGroups(downloading, completed, paused, failed, byId)
}

internal fun downloadOrderKeys(tasks: List<DownloadTask>, sort: DownloadSort): List<DownloadOrderKey> =
    tasks.map { task ->
        DownloadOrderKey(
            id = task.id,
            status = task.status,
            title = task.title,
            totalBytes = task.totalBytes,
            progress = if (sort == DownloadSort.PROGRESS && task.totalBytes > 0) {
                task.downloadedBytes.toFloat() / task.totalBytes
            } else {
                0f
            }
        )
    }

internal fun orderDownloadTaskIds(
    keys: List<DownloadOrderKey>,
    filter: DownloadFilter,
    sort: DownloadSort,
    query: String
): List<Int> {
    val byStatus = when (filter) {
        DownloadFilter.ALL -> keys
        DownloadFilter.DOWNLOADING -> keys.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
        }
        DownloadFilter.COMPLETED -> keys.filter { it.status == DownloadStatus.COMPLETED }
        DownloadFilter.FAILED -> keys.filter { it.status == DownloadStatus.FAILED }
        DownloadFilter.PAUSED -> keys.filter { it.status == DownloadStatus.PAUSED }
    }
    val keyword = query.trim()
    val matching = if (keyword.isEmpty()) {
        byStatus
    } else {
        byStatus.filter { it.title.contains(keyword, ignoreCase = true) }
    }
    return when (sort) {
        DownloadSort.ADDED -> matching.sortedByDescending { it.id }
        DownloadSort.NAME -> matching.sortedBy { it.title.lowercase() }
        DownloadSort.SIZE -> matching.sortedByDescending { it.totalBytes }
        DownloadSort.PROGRESS -> matching.sortedByDescending { it.progress }
    }.map { it.id }
}

// 先取筛选与排序真正依赖的稳定键，再按 id 映射回最新任务对象：进度更新不触发整表重排。
@Composable
internal fun rememberDownloadListProjection(
    tasks: List<DownloadTask>,
    filter: DownloadFilter,
    sort: DownloadSort,
    query: String
): DownloadListProjection {
    val groups = remember(tasks) { groupDownloadTasks(tasks) }
    val keys = remember(tasks, sort) { downloadOrderKeys(tasks, sort) }
    val orderedIds = remember(keys, filter, sort, query) {
        orderDownloadTaskIds(keys, filter, sort, query)
    }
    return remember(orderedIds, groups, filter, sort, query) {
        DownloadListProjection(
            groups = groups,
            visibleTasks = orderedIds.mapNotNull { groups.byId[it] },
            grouped = filter == DownloadFilter.ALL &&
                sort == DownloadSort.ADDED &&
                query.isBlank()
        )
    }
}

@Stable
internal class DownloadSelectionState {
    var isActive by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    fun enter() {
        isActive = true
    }

    fun exit() {
        isActive = false
        selectedIds = emptySet()
    }

    fun toggle(id: Int) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun toggleAll(ids: Set<Int>) {
        selectedIds = if (selectedIds.containsAll(ids)) selectedIds - ids else selectedIds + ids
    }
}

@Composable
internal fun rememberDownloadSelectionState(): DownloadSelectionState =
    remember { DownloadSelectionState() }
