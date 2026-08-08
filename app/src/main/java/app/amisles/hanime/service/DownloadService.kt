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
import androidx.core.content.edit
import app.amisles.hanime.MainActivity
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.DownloadStatus

/**
 * 下载前台服务，负责在通知栏显示下载进度，支持后台下载。
 *
 * data 模块的 DownloadManager 以显式类名 Intent 的方式将进度转发到本服务，
 * 从而避免 data 模块直接依赖 app 模块。
 *
 * 设计说明：
 * - 初次进入前台调用 startForeground；后续进度更新使用 NotificationManager.notify（避免反复调用startForeground）
 * - 使用 START_NOT_STICKY：系统杀死服务后不自动重启，由 DownloadManager 按需重新启动
 * - stopDownload 使用 stopService 以规避 Android 8+ 后台 startService 限制
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1

        // Intent action
        const val ACTION_START = "app.amisles.hanime.service.action.START"
        const val ACTION_STOP = "app.amisles.hanime.service.action.STOP"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"

        const val SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"

        private const val PREFS_NAME = "hanime_app_prefs"
        private const val PREF_LAST_DL_TITLE = "last_download_title"
        private const val PREF_LAST_DL_PROGRESS = "last_download_progress"

        /**
         * 启动下载前台服务并显示初始进度通知。
         */
        fun startDownload(context: Context, taskId: Int, title: String) {
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                    putString(PREF_LAST_DL_TITLE, title)
                    putInt(PREF_LAST_DL_PROGRESS, 0)
                }
            }
            val intent = Intent().apply {
                setClassName(context, SERVICE_CLASS_NAME)
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
            }
            context.startForegroundService(intent)
        }

        /**
         * 停止下载前台服务。使用 stopService 直接停止，避免后台 startService 限制。
         */
        fun stopDownload(context: Context) {
            runCatching {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                    remove(PREF_LAST_DL_TITLE)
                    remove(PREF_LAST_DL_PROGRESS)
                }
            }
            val intent = Intent().apply {
                setClassName(context, SERVICE_CLASS_NAME)
                action = ACTION_STOP
            }
            // 直接 stopService：若服务未运行系统会忽略，避免后台 startService 限制
            context.stopService(intent)
        }
    }

    private var currentTitle: String = ""
    private var currentProgress: Int = 0
    private var isForegroundStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: prefs.getString(PREF_LAST_DL_TITLE, null)
        if (!title.isNullOrEmpty()) {
            currentTitle = title
        }
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, -1)
            ?: prefs.getInt(PREF_LAST_DL_PROGRESS, -1)
        if (progress >= 0) {
            currentProgress = progress
            runCatching {
                prefs.edit { putInt(PREF_LAST_DL_PROGRESS, currentProgress) }
            }
        }
        val statusStr = intent?.getStringExtra(EXTRA_STATUS)
        val status = statusStr?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
        val action = intent?.action

        when {
            // 显式停止
            action == ACTION_STOP -> {
                if (isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                }
                stopSelfResult(startId)
            }
            // 下载完成：移除前台通知，发送普通通知后停止服务
            status == DownloadStatus.COMPLETED -> {
                if (isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                }
                notifyNotification(buildCompletedNotification(currentTitle))
                stopSelfResult(startId)
            }
            // 下载失败：移除前台通知，发送普通通知后停止服务
            status == DownloadStatus.FAILED -> {
                if (isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                }
                notifyNotification(buildFailedNotification(currentTitle))
                stopSelfResult(startId)
            }
            // 下载中：首次进入前台调用 startForeground，后续用 notify 更新
            status == DownloadStatus.DOWNLOADING -> {
                val notification = buildProgressNotification(currentTitle, currentProgress)
                if (!isForegroundStarted) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                    isForegroundStarted = true
                } else {
                    notifyNotification(notification)
                }
            }
            // 默认：启动前台服务（ACTION_START、PAUSED/PENDING 或无状态时）
            else -> {
                val notification = buildProgressNotification(currentTitle, currentProgress)
                if (!isForegroundStarted) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                    isForegroundStarted = true
                } else {
                    notifyNotification(notification)
                }
            }
        }
        // START_NOT_STICKY：系统杀死服务后不自动重启（由 DownloadManager 按需重启）
        return START_NOT_STICKY
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
     * 以普通通知方式发送（用于完成/失败通知或前台后的进度更新）。
     */
    private fun notifyNotification(notification: Notification) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(title: String, progress: Int): Notification {
        val displayTitle = title.ifEmpty { getString(R.string.download_notification_downloading) }
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
        val displayTitle = title.ifEmpty { getString(R.string.download_notification_completed) }
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
        val displayTitle = title.ifEmpty { getString(R.string.download_notification_failed) }
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
