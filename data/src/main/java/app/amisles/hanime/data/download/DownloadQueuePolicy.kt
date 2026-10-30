package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask

internal object DownloadQueuePolicy {

    fun pickPendingTask(
        tasks: Collection<DownloadTask>,
        activeJobIds: Set<Int>,
        exclude: Set<Int>,
        queueOrder: List<Int>,
        activeSlots: Int,
        concurrencyLimit: Int
    ): DownloadTask? {
        val pending = tasks.filter {
            it.status == DownloadStatus.PENDING && it.id !in activeJobIds && it.id !in exclude
        }
        if (pending.isEmpty()) return null
        val next = pending.minWithOrNull(
            compareBy({ queueOrder.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }, { it.id })
        )
        return next?.takeIf { activeSlots < concurrencyLimit }
    }

    fun progressPercent(downloadedBytes: Long, totalBytes: Long): Int =
        if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0

    fun shouldPersistProgress(lastPersistAt: Long, now: Long, intervalMs: Long): Boolean =
        now - lastPersistAt >= intervalMs

    fun shouldForwardProgress(statusChanged: Boolean, lastPercent: Int?, percent: Int): Boolean =
        statusChanged || lastPercent == null || lastPercent != percent
}
