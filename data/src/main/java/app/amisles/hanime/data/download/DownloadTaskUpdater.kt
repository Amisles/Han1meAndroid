package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

/**
 * 单个任务的原子更新与进度发布：速率采样、落盘节流、跨进程通知节流都在这里收口。
 *
 * 引擎只负责写入字节数与最终状态，通知与持久化策略集中在本类，避免各下载路径自行决定。
 */
internal class DownloadTaskUpdater(
    private val store: DownloadTaskStore,
    private val persistence: DownloadPersistence,
    private val bridge: DownloadServiceBridge
) {

    // 速率采样：字节差 / 时间差的指数滑动平均。update 在 store.lock 内串行执行，
    // 采样读写天然单线程，无需额外同步；清理时机与 lastNotifiedPercent 一致。
    private val speedSamples = ConcurrentHashMap<Int, SpeedSample>()
    private val lastPersistAt = ConcurrentHashMap<Int, Long>()
    private val lastNotifiedPercent = ConcurrentHashMap<Int, Int>()

    private class SpeedSample(val bytes: Long, val atMs: Long, val emaBps: Long)

    fun update(taskId: Int, updater: (DownloadTask) -> DownloadTask) {
        var progressUpdate: ProgressUpdate? = null
        store.lock.withLock {
            val current = store.tasksById[taskId] ?: return
            val newTask = applySpeedSample(taskId, updater(current))
            val statusChanged = newTask.status != current.status
            store.tasksById[taskId] = newTask
            if (statusChanged) store.emit() else store.emitReplacing(newTask)
            // 进度更新走内存态驱动 UI；仅状态切换或达到节流间隔才落盘，降低写放大
            if (statusChanged || shouldPersistProgress(taskId)) {
                lastPersistAt[taskId] = System.currentTimeMillis()
                persistence.enqueue(newTask)
            }
            // 锁内只记录需回调的信息；跨进程回调可能耗时且会重入，必须移出 lock 执行。
            val candidate = when (newTask.status) {
                DownloadStatus.DOWNLOADING -> if (newTask.totalBytes > 0) {
                    ProgressUpdate(
                        taskId,
                        newTask.title,
                        DownloadQueuePolicy.progressPercent(newTask.downloadedBytes, newTask.totalBytes),
                        newTask.status,
                        newTask.speedBps,
                        estimatedRemainingMs(newTask)
                    )
                } else {
                    null
                }
                DownloadStatus.PAUSED -> ProgressUpdate(
                    taskId,
                    newTask.title,
                    DownloadQueuePolicy.progressPercent(newTask.downloadedBytes, newTask.totalBytes),
                    newTask.status
                )
                DownloadStatus.COMPLETED, DownloadStatus.FAILED ->
                    ProgressUpdate(taskId, newTask.title, 100, newTask.status)
                else -> null
            }
            // 通知转发节流：reporter 每 500ms 触发一次 update，若每次转发即约 10 次/秒的 binder
            // 调用与通知重绘，而整数百分比多数未变。状态切换始终转发，保证通知语义不丢。
            if (candidate != null) {
                val last = lastNotifiedPercent[taskId]
                if (DownloadQueuePolicy.shouldForwardProgress(statusChanged, last, candidate.progress)) {
                    lastNotifiedPercent[taskId] = candidate.progress
                    progressUpdate = candidate
                }
            }
        }
        // 跨进程 IPC 回调放在锁外执行，避免在持锁期间做跨进程调用。
        progressUpdate?.let { update ->
            bridge.forwardProgress(
                update.title,
                update.progress,
                update.status,
                update.taskId,
                speedBps = update.speedBps,
                remainingMs = update.remainingMs
            )
        }
        // 完成事件额外携带成品文件路径，通知点击即可直达本地播放页
        val completedPath = progressUpdate
            ?.takeIf { it.status == DownloadStatus.COMPLETED }
            ?.let { store.tasksById[taskId]?.filePath }
            .orEmpty()
        if (completedPath.isNotBlank()) {
            bridge.forwardProgress(
                progressUpdate!!.title,
                100,
                DownloadStatus.COMPLETED,
                taskId,
                completedPath
            )
        }
    }

    /** 闸门关闭时向通知栏说明任务在等待条件，避免用户以为下载卡死。 */
    fun publishQueuedNotice(taskId: Int, title: String, progress: Int) {
        bridge.forwardProgress(title, progress, DownloadStatus.PENDING, taskId)
    }

    fun notifyRemoved(taskId: Int) {
        bridge.forwardRemove(taskId)
    }

    fun forget(taskId: Int) {
        lastNotifiedPercent.remove(taskId)
        lastPersistAt.remove(taskId)
        speedSamples.remove(taskId)
    }

    fun clearTransient() {
        lastNotifiedPercent.clear()
        lastPersistAt.clear()
        speedSamples.clear()
    }

    /**
     * 由最近两次字节快照估算下载速率并写回任务；非 DOWNLOADING 或总量未知时清零并丢弃采样。
     * 采样间隔小于 [SPEED_MIN_SAMPLE_MS] 时沿用上一次均值，避免同毫秒内的连续更新把瞬时值放大。
     */
    private fun applySpeedSample(taskId: Int, newTask: DownloadTask): DownloadTask {
        if (newTask.status != DownloadStatus.DOWNLOADING || newTask.totalBytes <= 0L) {
            speedSamples.remove(taskId)
            return newTask.copy(speedBps = 0L)
        }
        val now = System.currentTimeMillis()
        val prev = speedSamples[taskId]
        val sampled = when {
            prev == null || newTask.downloadedBytes < prev.bytes ->
                SpeedSample(newTask.downloadedBytes, now, 0L)
            else -> {
                val deltaBytes = newTask.downloadedBytes - prev.bytes
                val deltaMs = now - prev.atMs
                if (deltaMs < SPEED_MIN_SAMPLE_MS) {
                    prev
                } else {
                    val instantBps = deltaBytes * 1000L / deltaMs
                    SpeedSample(newTask.downloadedBytes, now, (prev.emaBps * 3L + instantBps) / 4L)
                }
            }
        }
        speedSamples[taskId] = sampled
        if (newTask.speedBps == sampled.emaBps) return newTask
        return newTask.copy(speedBps = sampled.emaBps)
    }

    private fun estimatedRemainingMs(task: DownloadTask): Long {
        if (task.speedBps <= 0L || task.totalBytes <= 0L) return 0L
        val remainingBytes = (task.totalBytes - task.downloadedBytes).coerceAtLeast(0L)
        return remainingBytes * 1000L / task.speedBps
    }

    /**
     * 进度类落盘节流：距上次落盘超过 [PROGRESS_PERSIST_INTERVAL_MS] 才允许写 DB。
     * 状态切换（由调用方判断）不走此节流，始终立即落盘。
     */
    private fun shouldPersistProgress(taskId: Int): Boolean =
        DownloadQueuePolicy.shouldPersistProgress(
            lastPersistAt = lastPersistAt[taskId] ?: 0L,
            now = System.currentTimeMillis(),
            intervalMs = PROGRESS_PERSIST_INTERVAL_MS
        )
}
