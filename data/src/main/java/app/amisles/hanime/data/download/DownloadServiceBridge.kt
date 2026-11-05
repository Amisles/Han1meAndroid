package app.amisles.hanime.data.download

import android.content.Context
import android.content.Intent
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadStatus

internal class DownloadServiceBridge(
    private val context: Context
) {

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
        when (status) {
            DownloadStatus.PENDING, DownloadStatus.DOWNLOADING -> startForegroundServiceSafely(intent)
            DownloadStatus.PAUSED, DownloadStatus.COMPLETED, DownloadStatus.FAILED -> {
                if (!runCatching { context.startService(intent) }.isSuccess) {
                    startForegroundServiceSafely(intent)
                }
            }
        }
    }

    fun forwardRemove(taskId: Int) {
        val intent = Intent().apply {
            setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
            action = ACTION_REMOVE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        runCatching { context.startService(intent) }.onFailure { err ->
            AppLogger.log("DownloadManager", "转发取消通知被系统拒绝（通知可能残留到进程结束）: ${err.message}")
        }
    }

    private fun startForegroundServiceSafely(intent: Intent) {
        runCatching { context.startForegroundService(intent) }.onFailure { err ->
            AppLogger.log("DownloadManager", "转发进度到 DownloadService 被系统拒绝: ${err.message}")
        }
    }
}
