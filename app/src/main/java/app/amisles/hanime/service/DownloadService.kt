package app.amisles.hanime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.amisles.hanime.MainActivity
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.DownloadStatus

/**
 * 下载前台服务，负责在通知栏显示下载进度，支持后台下载。
 *
 * data 模块的 DownloadManager 以显式类名 Intent 的方式将进度转发到本服务，
 * 从而避免 data 模块直接依赖 app 模块。
 */
class DownloadService : Service() {

    companion object {
        // 通知渠道与通知 ID（单一下载通知）
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1

        // Intent action
        const val ACTION_START = "app.amisles.hanime.service.action.START"
        const val ACTION_STOP = "app.amisles.hanime.service.action.STOP"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"

        // 本服务的完整类名，供 data 模块按类名构造 Intent
        const val SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"

        /**
         * 启动下载前台服务并显示初始进度通知。
         */
        fun startDownload(context: Context, taskId: Int, title: String) {
            val intent = Intent().apply {
                setClassName(context, SERVICE_CLASS_NAME)
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
            }
            context.startForegroundService(intent)
        }

        /**
         * 停止下载前台服务。
         */
        fun stopDownload(context: Context) {
            val intent = Intent().apply {
                setClassName(context, SERVICE_CLASS_NAME)
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // 当前下载标题（跨多次 onStartCommand 保留）
    private var currentTitle: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)
        if (!title.isNullOrEmpty()) currentTitle = title
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val statusStr = intent?.getStringExtra(EXTRA_STATUS)
        val status = statusStr?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
        val action = intent?.action

        when {
            // 显式停止
            action == ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            // 下载完成：移除前台通知，发送普通通知后停止服务
            status == DownloadStatus.COMPLETED -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifyNotification(buildCompletedNotification(currentTitle))
                stopSelf()
            }
            // 下载失败：移除前台通知，发送普通通知后停止服务
            status == DownloadStatus.FAILED -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifyNotification(buildFailedNotification(currentTitle))
                stopSelf()
            }
            // 下载中：更新前台进度通知
            status == DownloadStatus.DOWNLOADING -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildProgressNotification(currentTitle, progress),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
            // 默认：启动前台服务（ACTION_START 或无状态时）
            else -> {
                startForeground(
                    NOTIFICATION_ID,
                    buildProgressNotification(currentTitle, progress),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 创建下载通知渠道，重要性为 LOW 以避免提示音。
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示视频下载进度"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * 以普通通知方式发送（用于完成/失败通知，服务停止后仍保留）。
     */
    private fun notifyNotification(notification: Notification) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(title: String, progress: Int): Notification {
        val displayTitle = title.ifEmpty { "下载" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_downloading))
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildContentIntent())
            .build()
    }

    private fun buildCompletedNotification(title: String): Notification {
        val displayTitle = title.ifEmpty { "下载" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_completed))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent())
            .build()
    }

    private fun buildFailedNotification(title: String): Notification {
        val displayTitle = title.ifEmpty { "下载" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_failed))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent())
            .build()
    }

    /**
     * 构建点击通知后的 PendingIntent，打开 MainActivity。
     */
    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
