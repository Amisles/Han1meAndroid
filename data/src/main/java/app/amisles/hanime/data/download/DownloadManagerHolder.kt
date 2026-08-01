package app.amisles.hanime.data.download

import android.content.Context
import android.content.Intent
import app.amisles.hanime.data.local.database.FavoriteDatabase
import app.amisles.hanime.domain.model.DownloadStatus

object DownloadManagerHolder {
    @Volatile
    private var instance: DownloadManager? = null

    // DownloadService 的完整类名。data 模块不能直接依赖 app 模块，
    // 因此通过显式类名的 Intent 将进度转发给 app 模块中的 DownloadService。
    // 以下常量需与 DownloadService 中的定义保持一致。
    private const val DOWNLOAD_SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
    private const val EXTRA_TITLE = "extra_title"
    private const val EXTRA_PROGRESS = "extra_progress"
    private const val EXTRA_STATUS = "extra_status"

    fun getInstance(context: Context): DownloadManager {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val dao = FavoriteDatabase.getInstance(appContext).downloadDao()
                DownloadManager(appContext, dao).also { manager ->
                    instance = manager
                    // 设置进度回调，将下载进度转发给 DownloadService 更新通知栏
                    manager.onProgressUpdate = { _, title, progress, status ->
                        forwardToService(appContext, title, progress, status)
                    }
                }
            }
        }
    }

    /**
     * 将下载进度转发给 DownloadService。
     * 下载中使用 startForegroundService 以确保服务在前台运行；
     * 完成/失败时服务应已在前台运行，使用 startService 更新最终通知并停止服务。
     */
    private fun forwardToService(
        context: Context,
        title: String,
        progress: Int,
        status: DownloadStatus
    ) {
        try {
            val intent = Intent().apply {
                setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, status.name)
            }
            if (status == DownloadStatus.DOWNLOADING) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // 忽略转发异常，避免影响下载主流程
        }
    }
}
