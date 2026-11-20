package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.local.entity.toEntity
import app.amisles.hanime.domain.model.DownloadTask
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 下载任务的落盘队列：按 taskId 只保留最新快照，删除以哨兵覆盖同 id 的在途写入。
 *
 * 进度每 500ms 一条、状态切换一条；批量添加任务时若逐条入库，数据库会反复重写同一行，
 * 而中间态对重启恢复没有意义。合并后无论产生多少条中间态，一个任务最终只落一次盘。
 */
internal class DownloadPersistence(
    private val dao: DownloadDao,
    private val scope: CoroutineScope
) {

    private val pendingWrites = ConcurrentHashMap<Int, DownloadTask>()
    private val pendingDeletes = ConcurrentHashMap.newKeySet<Int>()
    private val pendingLock = Any()
    private val writeMutex = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        scope.launch {
            for (signalValue in signal) {
                runCatching { flush() }.onFailure { e ->
                    AppLogger.logError("DownloadManager", "Failed to persist batch: " + e.message, e)
                }
            }
        }
    }

    fun enqueue(task: DownloadTask) {
        synchronized(pendingLock) {
            pendingDeletes.remove(task.id)
            pendingWrites[task.id] = task
        }
        signal.trySend(Unit)
    }

    fun enqueueDelete(taskId: Int) {
        synchronized(pendingLock) {
            pendingWrites.remove(taskId)
            pendingDeletes.add(taskId)
        }
        signal.trySend(Unit)
    }

    suspend fun flush() = writeMutex.withLock {
        val (deletedIds, batch) = synchronized(pendingLock) {
            val deletes = pendingDeletes.toList()
            pendingDeletes.clear()
            val writes = pendingWrites.values.toList()
            pendingWrites.clear()
            deletes to writes
        }
        deletedIds.forEach { id -> dao.deleteDownload(id) }
        if (batch.isNotEmpty()) {
            dao.upsertAll(batch.map { it.toEntity() })
        }
    }
}
