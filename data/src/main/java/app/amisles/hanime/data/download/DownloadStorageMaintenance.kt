package app.amisles.hanime.data.download

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.parser.ParserUtils
import app.amisles.hanime.domain.model.DownloadRenameResult
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadStorageInfo
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.repository.DownloadSettings
import java.io.File
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载目录与文件登记：目录解析、卸载重装后的成品认领、存储统计与孤儿位图清理。
 *
 * 这些操作只读写文件系统与任务存储，与传输过程无关，故独立于调度与引擎。
 */
internal class DownloadStorageMaintenance(
    private val context: Context,
    private val store: DownloadTaskStore,
    private val persistence: DownloadPersistence,
    private val settings: DownloadSettings
) {

    private val appContext = context.applicationContext ?: context

    /** 默认下载目录：公共 Download/Hanime（见 [resolveDefaultDownloadDir]），卸载应用不会被清掉。 */
    private val defaultDownloadDir: File by lazy {
        resolveDefaultDownloadDir(appContext.getExternalFilesDir(null))
    }

    /**
     * 解析实际下载目录：空自定义路径 → 默认目录；非空自定义路径需可写，否则回退默认。
     * 每次新建下载时实时读取设置，使设置页变更立即对后续新下载生效。
     */
    fun resolveDownloadDir(): File {
        val custom = settings.downloadStoragePath
        if (custom.isNotBlank()) {
            val dir = File(custom)
            if (ensureDirWritable(dir)) return dir
            AppLogger.log("DownloadManager", "自定义存储路径不可写，回退默认目录: $custom")
        }
        return defaultDownloadDir
    }

    /**
     * 把下载目录里「有文件、无任务」的成品视频登记为已完成任务。
     *
     * 卸载重装后数据库随应用数据一起被清除，但 Download/Hanime 是公共目录、文件仍在。
     * 不重建任务的话，这些文件既不出现在下载页，也会被当成孤儿数据。
     */
    fun adoptOrphans(): Int {
        val dir = resolveDownloadDir()
        val known = store.lock.withLock { store.tasksById.values.mapTo(mutableSetOf()) { File(it.filePath).name } }
        val discovered = DownloadStorageInspector
            .scanDownloadedFiles(dir, known)
            .ifEmpty { scanViaMediaStore(known) }
        if (discovered.isEmpty()) return 0
        val adopted = mutableListOf<DownloadTask>()
        store.lock.withLock {
            discovered.forEach { item ->
                val nextId = store.nextId()
                val task = DownloadTask(
                    id = nextId,
                    title = item.title,
                    quality = item.quality,
                    url = "",
                    totalBytes = item.totalBytes,
                    downloadedBytes = item.totalBytes,
                    status = DownloadStatus.COMPLETED,
                    filePath = item.filePath,
                    thumbnailUrl = ParserUtils.generatePlaceholderThumbnail(item.videoId),
                    videoId = item.videoId
                )
                store.tasksById[nextId] = task
                adopted += task
            }
            store.emit()
        }
        adopted.forEach { persistence.enqueue(it) }
        AppLogger.log("DownloadManager", "Adopted ${adopted.size} existing files from ${dir.absolutePath}")
        return adopted.size
    }

    /**
     * 目录直读为空时改走 MediaStore。
     *
     * 分区存储下本应用看不到「上一个安装」写入的文件，但 MediaStore 仍为它们建了索引；
     * 这是卸载重装后能认出历史下载的唯一途径。
     */
    private fun scanViaMediaStore(knownNames: Set<String>): List<DiscoveredDownload> {
        val videos = MediaStoreVideoScanner.queryDownloads(appContext, DEFAULT_DOWNLOAD_SUBDIR)
        return videos
            .filter { it.displayName !in knownNames }
            .mapNotNull { item ->
                val parsed = DownloadStorageInspector.parseDownloadedFileName(item.displayName)
                    ?: return@mapNotNull null
                DiscoveredDownload(
                    videoId = parsed.first,
                    title = parsed.second,
                    quality = parsed.third,
                    filePath = item.absolutePath,
                    totalBytes = item.size
                )
            }
    }

    suspend fun storageInfo(): DownloadStorageInfo = withContext(Dispatchers.IO) {
        val dir = DownloadStorageInspector.currentDirectory(
            appContext.getExternalFilesDir(null),
            settings.downloadStoragePath
        )
        val activePaths = store.tasks.value.mapTo(HashSet()) { it.filePath }
        DownloadStorageInspector.inspect(dir, activePaths)
    }

    suspend fun clearOrphanPartmaps(): Long = withContext(Dispatchers.IO) {
        val dir = DownloadStorageInspector.currentDirectory(
            appContext.getExternalFilesDir(null),
            settings.downloadStoragePath
        )
        val activePaths = store.tasks.value.mapTo(HashSet()) { it.filePath }
        DownloadStorageInspector.clearOrphanPartmaps(dir, activePaths)
    }

    suspend fun rescanExistingDownloads(): Int = withContext(Dispatchers.IO) {
        adoptOrphans()
    }

    /**
     * 重命名已完成任务：磁盘文件与库内标题一起改。
     *
     * 站点标题多为日文 + 繁体中文，落盘名冗长且顺序凌乱，用户需要自行整理；而文件名尾部
     * `_画质_<videoId>` 是卸载重装后认领文件的依据，故只替换标题段，尾部原样保留。
     */
    suspend fun rename(taskId: Int, newTitle: String): DownloadRenameResult = withContext(Dispatchers.IO) {
        val task = store.tasksById[taskId] ?: return@withContext DownloadRenameResult.FAILED
        if (task.status != DownloadStatus.COMPLETED) return@withContext DownloadRenameResult.NOT_COMPLETED
        val title = newTitle.trim().replace(INVALID_FILE_NAME_CHARS, "_")
        if (title.isEmpty()) return@withContext DownloadRenameResult.EMPTY_NAME
        if (title == task.title) return@withContext DownloadRenameResult.SUCCESS

        val current = File(task.filePath)
        if (!current.isFile) return@withContext DownloadRenameResult.FILE_MISSING
        val suffix = current.name.removeSuffix(".mp4").substringAfterLast('_')
        val baseName = "${title}_${task.quality}".replace(INVALID_FILE_NAME_CHARS, "_")
        val target = File(current.parentFile, "${clampFileNameBase(baseName, suffix)}_$suffix.mp4")
        val renamed = when {
            target.absolutePath == current.absolutePath -> current
            target.exists() -> return@withContext DownloadRenameResult.NAME_TAKEN
            !runCatching { current.renameTo(target) }.getOrDefault(false) ->
                return@withContext DownloadRenameResult.FAILED
            else -> target
        }

        val updated = task.copy(title = title, filePath = renamed.absolutePath)
        val applied = store.lock.withLock {
            val latest = store.tasksById[taskId]
            if (latest == null || latest.status != DownloadStatus.COMPLETED) {
                false
            } else {
                store.tasksById[taskId] = updated
                store.emit()
                true
            }
        }
        if (!applied) {
            // 改名期间任务被删除或重新下载：把文件名退回去，避免留下一条没人引用的文件
            if (renamed != current) runCatching { renamed.renameTo(current) }
            return@withContext DownloadRenameResult.FAILED
        }
        persistence.enqueue(updated)
        AppLogger.log("DownloadManager", "重命名任务 $taskId: ${task.title} -> $title")
        DownloadRenameResult.SUCCESS
    }
}

private val INVALID_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")
