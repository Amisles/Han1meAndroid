package app.amisles.hanime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.amisles.hanime.MainActivity
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.DownloadStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载前台服务：在通知栏显示下载进度，支持后台下载。
 *
 * 核心约定：[activeTasks] 仅包含 DOWNLOADING / PENDING 任务；暂停与取消的任务必须移出该集合，
 * 否则集合永不为空，stopForeground / stopSelf 永不执行，导致 dataSync 前台服务泄漏。
 * 服务随任务结束由 DownloadManager 按需重启（START_NOT_STICKY）。
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        // 每个任务独立通知 id = BASE + taskId，避免并发互相覆盖
        const val NOTIFICATION_ID_BASE = 1000

        /** 下载期间 CPU / Wi-Fi 锁的标签。 */
        private const val TRANSFER_LOCK_TAG = "hanime:download"

        /** WakeLock 超时兜底：每次进度回调 acquire 即续期，进度完全停滞超时后自动释放，避免永久占锁耗电。 */
        private const val TRANSFER_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"

        /** 取消/删除任务：撤销通知并从活动集合移除。需与 DownloadManager 侧同名常量手工保持一致。 */
        const val ACTION_REMOVE_TASK = "app.amisles.hanime.service.action.REMOVE_TASK"

        const val SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
    }

    // taskId -> (title, progress)，记录仍在进行的下载任务（仅 DOWNLOADING/PENDING）
    private val activeTasks = ConcurrentHashMap<Int, Pair<String, Int>>()
    private var foregroundTaskId = -1
    private var isForegroundStarted = false

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val wifiManager by lazy {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * 有活动下载时持有 CPU 锁与 Wi-Fi 锁。
     *
     * startForeground 只保证进程不被杀，不阻止 CPU 降频与 Wi-Fi 进入省电模式，
     * 息屏后长视频下载会显著降速。两把锁均设为非引用计数：重复 acquire 只刷新超时时间，无需配对释放。
     */
    private fun acquireTransferLocks() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                TRANSFER_LOCK_TAG
            ).apply { setReferenceCounted(false) }
        }
        runCatching { wakeLock?.acquire(TRANSFER_LOCK_TIMEOUT_MS) }
        if (wifiLock == null) {
            wifiLock = runCatching { createWifiLockCompat() }.getOrNull()
        }
        runCatching { wifiLock?.acquire() }
    }

    /**
     * 创建 Wi-Fi 锁。部分系统只将其视为建议，创建或 acquire 失败时静默降级，不影响下载主流程。
     * 使用 HIGH_PERF 抑制省电模式，满足批量下载所需的持续吞吐。
     */
    @Suppress("DEPRECATION")
    private fun createWifiLockCompat(): WifiManager.WifiLock? {
        val wm = wifiManager ?: return null
        return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TRANSFER_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
    }

    /** 活动下载集合已空时释放锁。幂等，可重复调用。 */
    private fun releaseTransferLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
    }

    private fun notificationId(taskId: Int) = NOTIFICATION_ID_BASE + taskId

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1
        val title = intent?.getStringExtra(EXTRA_TITLE)
        val progressExtra = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1
        val progress = if (progressExtra >= 0) progressExtra else 0
        val statusStr = intent?.getStringExtra(EXTRA_STATUS)
        val status = statusStr?.let { runCatching { DownloadStatus.valueOf(it) }.getOrNull() }
        val action = intent?.action

        when {
            // 取消/删除任务 —— 该 Intent 不带 EXTRA_STATUS，必须置于状态分支之前，否则会被当作进度更新
            action == ACTION_REMOVE_TASK -> handleRemove(taskId, startId)
            // 暂停不计入活动下载，否则前台服务与通知永不释放
            status == DownloadStatus.PAUSED -> handlePaused(taskId, title ?: "", progress, startId)
            // 完成 / 失败：发普通通知，且仅当所有任务结束才退出前台
            status == DownloadStatus.COMPLETED -> handleTerminal(taskId, buildCompletedNotification(title ?: ""), startId)
            status == DownloadStatus.FAILED -> handleTerminal(taskId, buildFailedNotification(title ?: ""), startId)
            // 默认（含 DOWNLOADING / 无状态）：按进度通知处理
            else -> handleProgress(taskId, title ?: "", progress, status, startId)
        }
        // 活动任务集合已空即释放锁；统一放在此处，任何分支提前返回漏放都会被兜住
        if (activeTasks.isEmpty()) releaseTransferLocks()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 服务生命周期终点是锁释放的最后一道兜底
        releaseTransferLocks()
        super.onDestroy()
    }

    /** 处理进度更新（含启动）。每个任务有独立通知 id，互不覆盖。 */
    private fun handleProgress(taskId: Int, title: String, progress: Int, status: DownloadStatus?, startId: Int) {
        if (taskId < 0) {
            stopSelfResult(startId)
            return
        }
        activeTasks[taskId] = title to progress
        // 仍有在途任务即续期 CPU / Wi-Fi 锁
        acquireTransferLocks()
        val notification = buildProgressNotification(title, progress, status)
        if (!isForegroundStarted) {
            startForeground(notificationId(taskId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            isForegroundStarted = true
            foregroundTaskId = taskId
        } else if (foregroundTaskId == taskId) {
            notificationManager.notify(notificationId(taskId), notification)
        } else {
            // 次级任务：前台任务仍在则发独立通知；否则提升本任务为前台通知
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
    private fun handleTerminal(taskId: Int, terminalNotification: Notification, startId: Int) {
        if (taskId >= 0) {
            activeTasks.remove(taskId)
        }
        // 前台任务恰是刚结束的（或前台任务已不存在）-> 需要重新选出前台任务
        val needPromote = foregroundTaskId == taskId || !activeTasks.containsKey(foregroundTaskId)
        val next = if (needPromote) activeTasks.keys.firstOrNull() else null
        if (needPromote && next == null) {
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
        } else if (next != null) {
            foregroundTaskId = next
            val (t, p) = activeTasks[next]!!
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
            startForeground(
                notificationId(next),
                buildProgressNotification(t, p, DownloadStatus.DOWNLOADING),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            // 前台仍是其它任务：仅发刚结束任务的普通完成/失败通知
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
        }
    }

    /** 处理暂停：暂停态不计入活动任务并改用可划掉的常规通知；
     *  已无进行中任务则退出前台并停止服务，暂停的恰是前台任务则提升其它任务为前台。 */
    private fun handlePaused(taskId: Int, title: String, progress: Int, startId: Int) {
        if (taskId < 0) {
            stopSelfResult(startId)
            return
        }
        val wasForeground = foregroundTaskId == taskId
        activeTasks.remove(taskId)
        val notification = buildPausedNotification(title, progress)
        if (activeTasks.isEmpty()) {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
            }
            foregroundTaskId = -1
            // 退出前台后再发暂停通知，避免被 stopForeground(REMOVE) 一并移除
            notificationManager.notify(notificationId(taskId), notification)
            stopSelfResult(startId)
        } else if (wasForeground || !activeTasks.containsKey(foregroundTaskId)) {
            val next = activeTasks.keys.first()
            foregroundTaskId = next
            val (t, p) = activeTasks[next]!!
            notificationManager.notify(notificationId(taskId), notification)
            startForeground(
                notificationId(next),
                buildProgressNotification(t, p, DownloadStatus.DOWNLOADING),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            notificationManager.notify(notificationId(taskId), notification)
        }
    }

    /**
     * 处理取消/删除任务。cancelDownload 会直接删掉任务记录，无法再走进度通道，
     * 因此必须主动撤销其通知，否则该 ongoing 通知会永久残留。
     */
    private fun handleRemove(taskId: Int, startId: Int) {
        if (taskId < 0) {
            stopSelfResult(startId)
            return
        }
        val wasForeground = foregroundTaskId == taskId
        activeTasks.remove(taskId)
        notificationManager.cancel(notificationId(taskId))
        val next = activeTasks.keys.firstOrNull()
        if (next == null) {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
            }
            foregroundTaskId = -1
            stopSelfResult(startId)
        } else if (wasForeground || !activeTasks.containsKey(foregroundTaskId)) {
            foregroundTaskId = next
            val (t, p) = activeTasks[next]!!
            startForeground(
                notificationId(next),
                buildProgressNotification(t, p, DownloadStatus.DOWNLOADING),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        // 其余情况：前台仍是别的任务，被移除任务的通知已 cancel，无需额外处理
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 创建下载通知渠道，重要性 LOW 以避免提示音。 */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notify_download_progress)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(title: String, progress: Int, status: DownloadStatus? = null): Notification {
        val displayTitle = title.ifEmpty { getString(R.string.download_notification_downloading) }
        // 暂停态显示「已暂停 xx%」，其余显示「下载中 xx%」
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

    /** 暂停态通知用常规通知（可划掉、点击自动消失）；若沿用 setOngoing(true)，用户将无法自行清除。 */
    private fun buildPausedNotification(title: String, progress: Int): Notification {
        val displayTitle = title.ifEmpty { getString(R.string.download_notification_downloading) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_paused, progress.coerceIn(0, 100)))
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(false)
            .setAutoCancel(true)
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

    /** 构建点击通知后的 PendingIntent，打开 MainActivity。 */
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
