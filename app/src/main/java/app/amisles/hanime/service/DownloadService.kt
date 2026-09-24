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
 * 下载前台服务，负责在通知栏显示下载进度，支持后台下载。
 *
 * data 模块的 DownloadManager 以显式类名 Intent 的方式将进度转发到本服务，
 * 从而避免 data 模块直接依赖 app 模块。
 *
 * 设计说明：
 * - 每个任务使用独立的通知 id（NOTIFICATION_ID_BASE + taskId），并发下载互不覆盖（D6 修复）
 * - **「活动任务」只包含 DOWNLOADING / PENDING**（S1/S2 修复）：PAUSED 与「已取消」都必须从
 *   [activeTasks] 移除，否则该集合永不为空 -> stopForeground/stopSelf 永不执行，
 *   dataSync 前台服务会永久泄漏；同时暂停态改用 setOngoing(false) 的常规通知，用户可自行划掉。
 * - 取消由 DownloadManager 通过 [ACTION_REMOVE_TASK] 显式通知（cancelDownload 会删掉任务记录，
 *   无法走进度通道），本服务据此撤销该任务的通知并重算活动集合。
 * - 初次进入前台调用 startForeground；后续进度更新使用 NotificationManager.notify（避免反复调用 startForeground）
 * - 仅当所有活动任务都结束才 stopForeground + stopSelf
 * - 若前台任务恰好结束/暂停/被移除，自动将另一个仍在进行的任务提升为前台通知
 * - 使用 START_NOT_STICKY：系统杀死服务后不自动重启，由 DownloadManager 按需重新启动
 */
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        // D6：每个任务独立通知 id = BASE + taskId，避免并发互相覆盖
        const val NOTIFICATION_ID_BASE = 1000

        /** P2-7：下载期间 CPU / Wi-Fi 锁的标签。 */
        private const val TRANSFER_LOCK_TAG = "hanime:download"

        /**
         * P2-7：WakeLock 超时（兜底）。锁设为非引用计数，每次进度回调 acquire 即续期；
         * 仅当进度长时间完全停滞（超过此值）时才会自动释放，避免异常情况下永久占锁耗电。
         */
        private const val TRANSFER_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"

        /**
         * 取消/删除任务：撤销该任务的通知并从活动集合移除。
         * 与 EXTRA_TASK_ID 一样，本常量需与 DownloadManager 侧的同名常量手工保持一致
         * （data 模块不能依赖 app 模块）。
         */
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
     * P2-7：进入「有活动下载」状态时持有 CPU 锁与 Wi-Fi 锁。
     *
     * `startForeground` 只保证「进程不被杀 + 通知可见」，并不阻止 CPU 降频与 Wi-Fi 进入省电(PS)模式：
     * 息屏后无线网卡按 beacon 周期唤醒收包，长视频下载常从数十 Mbps 掉到数百 KB/s 甚至间歇为 0，
     * 亮屏（或点一下暂停/继续）又立刻回升 —— 与「持续较长时间后偶发自愈」的现象一致。
     *
     * 两个锁都设置 `setReferenceCounted(false)`：重复 acquire 只是刷新超时时间，
     * 不会因忘记配对释放而泄漏（WakeLock 的超时兜底为 [TRANSFER_LOCK_TIMEOUT_MS]，
     * 由后续每次进度回调续期）。
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
     * P2-7：创建 Wi-Fi 锁。
     *
     * 注：部分 OEM / 新版系统会将 WifiLock 视为建议（不再强制阻止 PS 模式），
     * 因此创建或 acquire 失败时静默降级 —— 该锁是「锦上添花」，不应影响下载主流程。
     * 使用 HIGH_PERF 而非 LOW_LATENCY：前者抑制省电模式（批量下载需要的正是持续吞吐），
     * 后者面向实时低延迟场景。
     */
    @Suppress("DEPRECATION")
    private fun createWifiLockCompat(): WifiManager.WifiLock? {
        val wm = wifiManager ?: return null
        return wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TRANSFER_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
    }

    /** P2-7：活动下载集合已空时释放锁。幂等，可重复调用。 */
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
            // S1：任务被取消/删除 —— 撤销通知并重算活动集合。必须置于状态分支之前：
            // 该 Intent 不带 EXTRA_STATUS，否则会被当作进度更新处理。
            action == ACTION_REMOVE_TASK -> handleRemove(taskId, startId)
            // S2：暂停不算「活动下载」，否则前台服务与通知永不释放
            status == DownloadStatus.PAUSED -> handlePaused(taskId, title ?: "", progress, startId)
            // 下载完成 / 失败：发普通通知（自动消失），且仅当所有任务结束才退出前台
            status == DownloadStatus.COMPLETED -> handleTerminal(taskId, buildCompletedNotification(title ?: ""), startId)
            status == DownloadStatus.FAILED -> handleTerminal(taskId, buildFailedNotification(title ?: ""), startId)
            // 默认（含 DOWNLOADING / 无状态）：作为进度通知处理
            else -> handleProgress(taskId, title ?: "", progress, status, startId)
        }
        // P2-7：活动任务集合已空（完成/失败/暂停/取消后的收尾路径）即释放锁。
        // 放在这里而非各分支内部：任何分支（含参数异常提前返回）漏放都会被这一次兜住。
        if (activeTasks.isEmpty()) releaseTransferLocks()
        // START_NOT_STICKY：系统杀死服务后不自动重启（由 DownloadManager 按需重启）
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // P2-7：服务生命周期终点是锁释放的最后一道兜底
        releaseTransferLocks()
        super.onDestroy()
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
        // P2-7：只要还有在途任务就续期 CPU / Wi-Fi 锁
        acquireTransferLocks()
        // P2-5：将状态传入，使通知文案区分「下载中 xx%」与「已暂停 xx%」
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

    /**
     * S2：处理暂停。暂停态不再计入活动任务，并改用可划掉的常规通知：
     * - 已无进行中任务 -> 退出前台并停止服务（否则「已暂停」会永久占着 dataSync 前台服务）；
     * - 暂停的恰是前台任务 -> 把其它进行中任务提升为前台。
     */
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
     * S1：处理取消/删除任务。DownloadManager.cancelDownload 会直接删掉任务记录，
     * 无法再走进度通道，因此必须由本方法撤销其通知，否则该通知（ongoing 且不可划掉）会永久残留。
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

    /**
     * 创建下载通知渠道，重要性为 LOW 以避免提示音。
     */
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

    /**
     * S2：暂停态通知改用常规通知（可划掉、点击自动消失）。
     * 若继续用 setOngoing(true)，暂停后该通知将无法由用户自行清除。
     */
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
