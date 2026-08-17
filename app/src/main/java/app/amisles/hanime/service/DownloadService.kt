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
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载前台服务，负责在通知栏显示下载进度，支持后台下载。
 *
 * data 模块的 DownloadManager 以显式类名 Intent 的方式将进度转发到本服务，
 * 从而避免 data 模块直接依赖 app 模块。
 *
 * 设计说明：
 * - 每个任务使用独立的通知 id（NOTIFICATION_ID_BASE + taskId），并发下载互不覆盖（D6 修复）
 * - 初次进入前台调用 startForeground；后续进度更新使用 NotificationManager.notify（避免反复调用 startForeground）
 * - 仅当所有任务都结束才 stopForeground + stopSelf（D6 修复：避免单任务完成误杀其它进行中任务的通知）
 * - 若前台任务恰好完成，自动将另一个仍在进行的任务提升为前台通知
 * - 使用 START_NOT_STICKY：系统杀死服务后不自动重启，由 DownloadManager 按需重新启动
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        // D6：每个任务独立通知 id = BASE + taskId，避免并发互相覆盖
        const val NOTIFICATION_ID_BASE = 1000

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

    // taskId -> (title, progress)，记录仍在进行的下载任务
    private val activeTasks = ConcurrentHashMap<Int, Pair<String, Int>>()
    private var foregroundTaskId = -1
    private var isForegroundStarted = false

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun notificationId(taskId: Int) = NOTIFICATION_ID_BASE + taskId

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: prefs.getString(PREF_LAST_DL_TITLE, null)
        if (!title.isNullOrEmpty()) {
            runCatching { prefs.edit { putString(PREF_LAST_DL_TITLE, title) } }
        }
        val progressExtra = intent?.getIntExtra(EXTRA_PROGRESS, -1)
        val progress = if (progressExtra != null && progressExtra >= 0) progressExtra else 0
        val statusStr = intent?.getStringExtra(EXTRA_STATUS)
        val status = statusStr?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
        val action = intent?.action

        when {
            // 显式停止
            action == ACTION_STOP -> {
                activeTasks.clear()
                if (isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                }
                foregroundTaskId = -1
                stopSelfResult(startId)
            }
            // 下载完成 / 失败：发普通通知（自动消失），且仅当所有任务结束才退出前台
            status == DownloadStatus.COMPLETED -> {
                val safeTitle = title ?: ""
                handleTerminal(taskId, safeTitle, buildCompletedNotification(safeTitle), startId)
            }
            status == DownloadStatus.FAILED -> {
                val safeTitle = title ?: ""
                handleTerminal(taskId, safeTitle, buildFailedNotification(safeTitle), startId)
            }
            // 默认（含 DOWNLOADING / ACTION_START / PAUSED / PENDING / 无状态）：作为进度通知处理
            else -> {
                val safeTitle = title ?: ""
                handleProgress(taskId, safeTitle, progress, status, startId)
            }
        }
        // START_NOT_STICKY：系统杀死服务后不自动重启（由 DownloadManager 按需重启）
        return START_NOT_STICKY
    }

    /**
     * 处理进度更新（含启动）。每个任务有独立通知 id，互不覆盖。
     */
    private fun handleProgress(taskId: Int, title: String, progress: Int, status: DownloadStatus?, startId: Int) {
        if (taskId < 0) {
            stopSelfResult(startId)
            return
        }
        activeTasks[taskId] = title to progress
        // P2-5：将状态传入，使通知文案区分「下载中 xx%」与「已暂停 xx%」
        val notification = buildProgressNotification(title, progress, status)
        if (!isForegroundStarted) {
            startForeground(notificationId(taskId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            isForegroundStarted = true
            foregroundTaskId = taskId
        } else if (foregroundTaskId == taskId) {
            notificationManager.notify(notificationId(taskId), notification)
        } else {
            // 次级任务：前台任务仍在则发独立通知；否则提升为本任务为前台通知
            if (foregroundTaskId >= 0 && activeTasks.containsKey(foregroundTaskId)) {
                notificationManager.notify(notificationId(taskId), notification)
            } else {
                foregroundTaskId = taskId
                startForeground(notificationId(taskId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
        }
    }

    /**
     * 处理完成 / 失败。发普通通知，且仅当所有任务都结束才退出前台并停止服务；
     * 若前台任务恰好是刚结束的，自动提升另一个活动任务为前台通知。
     */
    private fun handleTerminal(taskId: Int, title: String, terminalNotification: Notification, startId: Int) {
        if (taskId >= 0) {
            activeTasks.remove(taskId)
        }
        if (activeTasks.isEmpty()) {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
            }
            foregroundTaskId = -1
            // 退出前台后再发完成/失败普通通知，避免被 stopForeground(REMOVE) 移除
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
            stopSelfResult(startId)
        } else if (foregroundTaskId == taskId || !activeTasks.containsKey(foregroundTaskId)) {
            // 前台任务恰是刚结束的（或前台任务已不存在），提升另一个活动任务为前台通知
            val next = activeTasks.keys.first()
            foregroundTaskId = next
            val (t, p) = activeTasks[next]!!
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
            startForeground(notificationId(next), buildProgressNotification(t, p), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            // 前台仍是其它任务：仅发刚结束任务的普通完成/失败通知
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
        }
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
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(title: String, progress: Int, status: DownloadStatus? = null): Notification {
        val displayTitle = title.ifEmpty { getString(R.string.download_notification_downloading) }
        // P2-5：进度文案体现百分比；暂停态显示「已暂停 xx%」，其余显示「下载中 xx%」
        val contentText = when (status) {
            DownloadStatus.PAUSED -> getString(R.string.download_notification_paused, progress.coerceIn(0, 100))
            else -> getString(R.string.download_notification_progress, progress.coerceIn(0, 100))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setProgress(100, progress.coerceIn(0, 100), false)
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
