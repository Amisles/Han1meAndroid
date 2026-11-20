package app.amisles.hanime.data.download

import android.database.sqlite.SQLiteException
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.local.entity.DownloadEntity
import app.amisles.hanime.domain.model.DownloadErrorCode
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import java.io.File
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 进程启动与备份导入后的任务恢复：从数据库重建内存态、校验已完成文件是否仍在、恢复中断任务。
 *
 * 恢复不负责传输本身，只把持久化事实翻译成内存态并交给调度器。
 */
internal class DownloadRestorer(
    private val dao: DownloadDao,
    private val store: DownloadTaskStore,
    private val persistence: DownloadPersistence,
    private val maintenance: DownloadStorageMaintenance,
    private val updater: DownloadTaskUpdater,
    private val session: DownloadSessionController,
    private val scheduler: DownloadQueueScheduler,
    private val gate: DownloadGateController
) {

    suspend fun restore() = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getAllDownloadsOnce()
            val restoredTasks = entities.map { entity ->
                toTask(
                    entity,
                    status = if (entity.status == DownloadStatus.DOWNLOADING.name) {
                        DownloadStatus.PAUSED
                    } else {
                        runCatching { DownloadStatus.valueOf(entity.status) }
                            .getOrDefault(DownloadStatus.FAILED)
                    }
                )
            }
            // DB 里的 COMPLETED 不代表文件还在（用户清理目录 / 卸载重装 / 存储卡移除）。
            // 不校验会让「已完成」条目点播放黑屏，且 isVideoDownloaded 会禁止重新下载。
            val verifiedTasks = restoredTasks.map { task -> verifyCompleted(task) }
            store.lock.withLock {
                store.tasksById.clear()
                verifiedTasks.forEach { store.tasksById[it.id] = it }
                store.emit()
            }
            // 同步落库：getCompletedDownloadCount() 等接口按库统计，不回写会与内存态长期不一致
            verifiedTasks.zip(restoredTasks)
                .filter { (verified, original) -> verified.status != original.status }
                .forEach { (verified, _) -> persistence.enqueue(verified) }
            val maxId = dao.getMaxId() ?: 0
            store.resetCounter(maxId)
            maintenance.adoptOrphans()
            AppLogger.log(
                "DownloadManager",
                "Restored ${restoredTasks.size} tasks from DB, taskIdCounter=$maxId"
            )
            entities.filter { it.status == DownloadStatus.DOWNLOADING.name }
                .map { it.id }
                .forEach { session.resumeInternal(it) }
            // 避免进程重启后这些任务永久挂起、只能手动恢复。
            scheduler.startNextPendingTask()
        } catch (e: SQLiteException) {
            AppLogger.logError("DownloadManager", "Failed to restore tasks: ${e.message}", e)
        }
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        val restored = dao.getAllDownloadsOnce().map { entity ->
            val status = when (entity.status) {
                DownloadStatus.DOWNLOADING.name, DownloadStatus.PENDING.name -> DownloadStatus.PAUSED
                else -> runCatching { DownloadStatus.valueOf(entity.status) }
                    .getOrDefault(DownloadStatus.FAILED)
            }
            verifyCompleted(toTask(entity, status))
        }
        val maxId = dao.getMaxId() ?: 0
        store.lock.withLock {
            store.tasksById.clear()
            restored.forEach { store.tasksById[it.id] = it }
            store.resetCounter(maxId)
            gate.clearAutoPaused()
            store.clearPauseEpochs()
            updater.clearTransient()
            store.emit()
        }
        restored.forEach { persistence.enqueue(it) }
        AppLogger.log(
            "DownloadManager",
            "已从数据库重建下载任务: " + restored.size + ", taskIdCounter=" + maxId
        )
    }

    private fun toTask(entity: DownloadEntity, status: DownloadStatus): DownloadTask = DownloadTask(
        id = entity.id,
        title = entity.title,
        quality = entity.quality,
        url = entity.url,
        totalBytes = entity.totalBytes,
        downloadedBytes = entity.downloadedBytes,
        status = status,
        filePath = entity.filePath,
        thumbnailUrl = entity.thumbnailUrl,
        videoId = entity.videoId,
        errorMessage = entity.errorMessage
    )

    private fun verifyCompleted(task: DownloadTask): DownloadTask {
        if (task.status != DownloadStatus.COMPLETED) return task
        val file = File(task.filePath)
        if (file.exists() && file.length() > 0) return task
        AppLogger.log(
            "DownloadManager",
            "已完成任务的本地文件缺失，标记为失败: ${task.title} (${task.filePath})"
        )
        return task.copy(status = DownloadStatus.FAILED, errorMessage = DownloadErrorCode.FILE_MISSING.name)
    }
}
