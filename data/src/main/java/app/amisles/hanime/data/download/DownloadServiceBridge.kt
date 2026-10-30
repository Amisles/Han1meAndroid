package app.amisles.hanime.data.download

import android.content.Context
import android.content.Intent
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadStatus
import java.util.concurrent.atomic.AtomicBoolean

internal class DownloadServiceBridge(
    private val context: Context,
    private val hasActiveDownload: () -> Boolean
) {

    private val serviceStarted = AtomicBoolean(false)

    fun forwardProgress(
        title: String,
        progress: Int,
        status: DownloadStatus,
        taskId: Int,
        filePath: String = ""
    ) {
        val intent = Intent().apply {
            setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_STATUS, status.name)
            if (filePath.isNotBlank()) putExtra(EXTRA_FILE_PATH, filePath)
        }
        val firstTime = serviceStarted.compareAndSet(false, true)
        try {
            if (firstTime) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            serviceStarted.set(false)
            runCatching { context.startForegroundService(intent) }.onFailure { se ->
                AppLogger.log(
                    "DownloadManager",
                    "转发进度到 DownloadService 被系统拒绝: ${e.message} / ${se.message}"
                )
            }
        } catch (e: SecurityException) {
            serviceStarted.set(false)
        }
        if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED ||
            status == DownloadStatus.PAUSED
        ) {
            if (!hasActiveDownload()) serviceStarted.set(false)
        }
    }

    fun forwardRemove(taskId: Int) {
        val intent = Intent().apply {
            setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
            action = ACTION_REMOVE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        runCatching { context.startService(intent) }.onFailure { e ->
            AppLogger.log("DownloadManager", "转发取消通知被系统拒绝（通知可能残留到进程结束）: ${e.message}")
        }
        if (!hasActiveDownload()) serviceStarted.set(false)
    }
}
