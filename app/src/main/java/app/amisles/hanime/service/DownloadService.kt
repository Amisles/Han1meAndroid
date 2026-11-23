package app.amisles.hanime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.amisles.hanime.MainActivity
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.formatBytes
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.data.download.DownloadServiceContract
import app.amisles.hanime.domain.repository.AppearanceSettings
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.model.DownloadEta
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import javax.inject.Inject

/**
 * 下载前台服务：在通知栏显示下载进度，支持后台下载。
 *
 * 核心约定：[activeTasks] 仅包含 DOWNLOADING / PENDING 任务；暂停与取消的任务必须移出该集合，
 * 否则集合永不为空，stopForeground / stopSelf 永不执行，导致 dataSync 前台服务泄漏。
 * 服务随任务结束由 DownloadManager 按需重启（START_NOT_STICKY）。
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloads: DownloadRepository

    @Inject
    lateinit var appearance: AppearanceSettings

    companion object {
        const val CHANNEL_ID = DownloadServiceContract.CHANNEL_ID
        // 每个任务独立通知 id = BASE + taskId，避免并发互相覆盖。
        // 取高段位：taskId 随历史任务单调递增（DB 最大 id 恢复），低位段（1000/2001）
        // 会被长期累积的任务 id 撞上，与播放通知（2001）、订阅提醒（3001）互相覆盖。
        private const val NOTIFICATION_ID_BASE = 1_000_000

        /** 尚未绑定到具体任务（taskId 非法）时的前台占位通知 id，与 BASE + taskId 不冲突。 */
        private const val NOTIFICATION_ID_PLACEHOLDER = NOTIFICATION_ID_BASE - 1

        /** 下载期间 CPU / Wi-Fi 锁的标签。 */
        private const val TRANSFER_LOCK_TAG = "hanime:download"

        /** WakeLock 超时兜底：每次进度回调 acquire 即续期，进度完全停滞超时后自动释放，避免永久占锁耗电。 */
        private const val TRANSFER_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        const val EXTRA_TASK_ID = DownloadServiceContract.EXTRA_TASK_ID
        const val EXTRA_TITLE = DownloadServiceContract.EXTRA_TITLE
        const val EXTRA_PROGRESS = DownloadServiceContract.EXTRA_PROGRESS
        const val EXTRA_STATUS = DownloadServiceContract.EXTRA_STATUS

        /** 下载速率（字节/秒）与预计剩余毫秒数；须与 DownloadManager 侧同名常量手工保持一致。 */
        const val EXTRA_SPEED_BPS = DownloadServiceContract.EXTRA_SPEED_BPS
        const val EXTRA_REMAINING_MS = DownloadServiceContract.EXTRA_REMAINING_MS

        /** 下载完成事件携带的成品文件绝对路径，用于通知点击直达本地播放页。 */
        const val EXTRA_FILE_PATH = DownloadServiceContract.EXTRA_FILE_PATH

        /** 取消/删除任务：撤销通知并从活动集合移除。需与 DownloadManager 侧同名常量手工保持一致。 */
        const val ACTION_REMOVE_TASK = DownloadServiceContract.ACTION_REMOVE_TASK

        const val ACTION_PAUSE_ALL = DownloadServiceContract.ACTION_PAUSE_ALL
        const val ACTION_RESUME_TASK = DownloadServiceContract.ACTION_RESUME_TASK
        const val ACTION_CANCEL_TASK = DownloadServiceContract.ACTION_CANCEL_TASK
        const val ACTION_RETRY_FAILED = DownloadServiceContract.ACTION_RETRY_FAILED

        const val SERVICE_CLASS_NAME = DownloadServiceContract.SERVICE_CLASS_NAME
    }

    /**
     * 活动任务快照（仅 DOWNLOADING / PENDING）。
     *
     * 必须连状态一起记：只有真正在传输（DOWNLOADING）时才该持 CPU / Wi-Fi 锁。等待闸门的
     * PENDING 任务可能挂上数小时，而无超时的 WIFI_MODE_FULL_HIGH_PERF 会一直抑制省电模式。
     */
    private data class ActiveTask(
        val title: String,
        val progress: Int,
        val status: DownloadStatus?,
        val speedBps: Long = 0L,
        val remainingMs: Long = 0L
    )

    private val activeTasks = ConcurrentHashMap<Int, ActiveTask>()
    /**
     * 当前占据前台通知的任务 id；-1 表示「服务未处于前台」。
     *
     * 这是前台状态的**唯一事实来源**。此前另有 isForegroundStarted 布尔与之并存，两者在
     * handleTerminal / handlePaused / handleRemove 的分支上会失去同步：服务仍在别的任务上
     * 处于前台，而 foregroundTaskId 已被置回 -1，此后任何进度回调都会走错分支 —— 该提升
     * 为前台的没提升，或把刚发出的终态通知又拉回前台。
     * 「是否已进入前台」与「foregroundTaskId >= 0」本就是同一件事，不该维护两份。
     */
    private var foregroundTaskId = -1


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

    /**
     * 按当前活动任务同步锁状态：只有真的存在 DOWNLOADING 才持锁。
     *
     * 历史事件可能不带状态（EXTRA_STATUS 缺失），这类按「下载中」保守处理。
     * 等待闸门的 PENDING 任务不持锁 —— 它可能几小时不产生任何进度事件，锁不会有下一次续期机会。
     */
    private fun syncTransferLocks() {
        val transferring = activeTasks.values.any {
            it.status == null || it.status == DownloadStatus.DOWNLOADING
        }
        if (transferring) acquireTransferLocks() else releaseTransferLocks()
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
        val speedBps = intent?.getLongExtra(EXTRA_SPEED_BPS, 0L) ?: 0L
        val remainingMs = intent?.getLongExtra(EXTRA_REMAINING_MS, 0L) ?: 0L
        val action = intent?.action

        when {
            action == ACTION_PAUSE_ALL -> {
                downloads.pauseAll()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            action == ACTION_RETRY_FAILED -> {
                downloads.retryAllFailed()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            else -> {}
        }

        // 无条件尝试进入前台：若用「是否已在前台」缓存短路，服务被 stopForeground / stopSelf 后
        // 标志若未复位，后续 startForegroundService 的 5 秒履约就永远不满足，Android 12+ 会抛
        // ForegroundServiceDidNotStartInTimeException。系统对重复 startForeground 是幂等的，
        // 因此这里不做缓存，直接推进履约。
        ensureForeground(taskId, title ?: "", progress, status, speedBps, remainingMs)

        if (taskId < 0) {
            // 非法 taskId：已占位进入前台以履约，随即撤销并停止服务。
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundTaskId = -1
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val currentStatus = downloads.tasks.value.firstOrNull { it.id == taskId }?.status
        val stale = when (status) {
            DownloadStatus.PAUSED, DownloadStatus.COMPLETED, DownloadStatus.FAILED -> currentStatus != status
            DownloadStatus.DOWNLOADING -> currentStatus != DownloadStatus.DOWNLOADING
            DownloadStatus.PENDING -> currentStatus != DownloadStatus.PENDING
            null -> currentStatus != DownloadStatus.DOWNLOADING && currentStatus != DownloadStatus.PENDING
        }
        when {
            action == ACTION_REMOVE_TASK -> handleRemove(taskId, startId)
            action == ACTION_RESUME_TASK -> {
                downloads.resumeDownload(taskId)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            action == ACTION_CANCEL_TASK -> {
                downloads.cancelDownload(taskId)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            stale -> handleCurrentTask(taskId, startId)
            status == DownloadStatus.PAUSED -> handlePaused(taskId, title ?: "", progress, startId)
            status == DownloadStatus.COMPLETED -> handleTerminal(
                taskId,
                buildCompletedNotification(title ?: "", intent.getStringExtra(EXTRA_FILE_PATH) ?: "", taskId),
                startId
            )
            status == DownloadStatus.FAILED -> handleTerminal(
                taskId,
                buildFailedNotification(title ?: "", taskId),
                startId
            )
            else -> handleProgress(taskId, title ?: "", startId)
        }
        // 锁状态统一在此按「是否仍有真实传输」收敛，任何分支提前返回漏放都会被兜住
        syncTransferLocks()
        return START_NOT_STICKY
    }

    private fun ensureForeground(
        taskId: Int,
        title: String,
        progress: Int,
        status: DownloadStatus?,
        speedBps: Long,
        remainingMs: Long
    ) {
        val id = if (taskId >= 0) notificationId(taskId) else NOTIFICATION_ID_PLACEHOLDER
        startForegroundSafely(id, taskId, buildProgressNotification(title, progress, status, speedBps, remainingMs))
    }

    private fun startForegroundSafely(id: Int, taskId: Int, notification: Notification): Boolean {
        // 不做缓存短路：onStartCommand 已保证每次都推进履约，系统对重复 startForeground
        // 是幂等的，缓存反而会让 5s 计时失效。
        return runCatching {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }.onSuccess {
            foregroundTaskId = taskId
        }.onFailure { e ->
            AppLogger.log("DownloadService", "进入前台被系统拒绝，主动停止服务以撤销契约: ${e.message}")
            // 必须复位：否则 foregroundTaskId 仍指向旧任务，后续进度回调会误判「已在前台」
            // 而只发通知，导致服务永远不满足前台契约。
            foregroundTaskId = -1
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            runCatching { stopSelf() }
        }.isSuccess
    }

    /**
     * dataSync 类型前台服务在 Android 15（API 35）起有时长上限：24 小时窗口内累计约 6 小时后
     * 系统会回调本方法。默认实现会直接抛 RemoteServiceException 从而崩溃整个进程。
     *
     * 下载是可恢复的：这里把进度落盘后转 PAUSED 并停止服务，用户下次点继续即可从位图表续传。
     * 不实现该回调会导致长时间下载在后台被系统杀掉且进度停在半截。
     */
    override fun onTimeout(startId: Int) {
        AppLogger.log(
            "DownloadService",
            "dataSync 前台服务时长已达系统上限，暂停全部任务并退出前台: startId=$startId"
        )
        // 必须真的暂停：下载协程挂在 DownloadManager 的进程级作用域上，本服务被系统收回后它们
        // 照常运行，却已失去前台豁免 —— 随时可能被静默杀掉，界面还停在「下载中」。
        // 转 PAUSED 后位图表已经落盘，用户点「继续」即可从断点续传。
        runCatching { downloads.pauseAllForQuota() }.onFailure { e ->
            AppLogger.logError("DownloadService", "暂停下载失败: " + e.message, e)
        }
        activeTasks.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundTaskId = -1
        releaseTransferLocks()
        stopSelf(startId)
    }

    override fun onDestroy() {        // 服务生命周期终点是锁释放的最后一道兜底
        releaseTransferLocks()
        super.onDestroy()
    }

    private fun handleCurrentTask(taskId: Int, startId: Int) {
        val task = downloads.tasks.value.firstOrNull { it.id == taskId }
        if (task == null) {
            handleRemove(taskId, startId)
            return
        }
        val progress = progressOf(task)
        when (task.status) {
            DownloadStatus.COMPLETED -> handleTerminal(
                taskId,
                buildCompletedNotification(task.title, task.filePath, taskId),
                startId
            )
            DownloadStatus.FAILED -> handleTerminal(
                taskId,
                buildFailedNotification(task.title, taskId),
                startId
            )
            DownloadStatus.PAUSED -> handlePaused(taskId, task.title, progress, startId)
            DownloadStatus.DOWNLOADING, DownloadStatus.PENDING ->
                handleProgress(taskId, task.title, startId)
        }
    }

    private fun progressOf(task: DownloadTask): Int =
        if (task.totalBytes > 0) {
            (task.downloadedBytes * 100 / task.totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

    /** 按任务内存态里的速率估算剩余毫秒；速率未知或已无剩余字节时返回 0（通知不显示剩余时间）。 */
    private fun remainingMsOf(task: DownloadTask): Long =
        DownloadEta.remainingMs(task.downloadedBytes, task.totalBytes, task.speedBps)

    private fun formatEta(remainingMs: Long): String {
        val minutes = remainingMs / 60_000L
        return if (minutes < 60L) {
            getString(R.string.download_eta_minutes, minutes.coerceAtLeast(1L))
        } else {
            getString(R.string.download_eta_hours, String.format(Locale.US, "%.1f", minutes / 60.0))
        }
    }

    private fun handleProgress(taskId: Int, title: String, startId: Int) {
        if (taskId < 0) {
            stopSelfResult(startId)
            return
        }
        val task = downloads.tasks.value.firstOrNull { it.id == taskId }
        if (task == null || (task.status != DownloadStatus.DOWNLOADING && task.status != DownloadStatus.PENDING)) {
            handleCurrentTask(taskId, startId)
            return
        }
        val effectiveTitle = task.title.ifBlank { title }
        val effectiveProgress = progressOf(task)
        val effectiveStatus = task.status
        val effectiveSpeed = task.speedBps
        val effectiveRemaining = remainingMsOf(task)
        activeTasks[taskId] = ActiveTask(effectiveTitle, effectiveProgress, effectiveStatus, effectiveSpeed, effectiveRemaining)
        // 锁不在事件里直接续期：PENDING（等待闸门）同样会走这里，但它不该持锁。
        // 由 onStartCommand 末尾的 syncTransferLocks() 按「是否存在 DOWNLOADING」统一决定。
        val notification = buildProgressNotification(effectiveTitle, effectiveProgress, effectiveStatus, effectiveSpeed, effectiveRemaining)
        // 只有「当前没有前台任务」或「前台任务已不在活动集合里」时才需要占据前台；
        // 其余情况（包括本任务就是前台任务）一律只更新通知，不必重复 startForeground。
        val needForeground = foregroundTaskId < 0 || !activeTasks.containsKey(foregroundTaskId)
        if (needForeground) {
            startForegroundSafely(notificationId(taskId), taskId, notification)
        } else {
            notificationManager.notify(notificationId(taskId), notification)
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
            // foregroundTaskId >= 0 即表示服务仍在前台，无需再查第二个标志
            if (foregroundTaskId >= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            foregroundTaskId = -1
            // 退出前台后再发完成/失败普通通知，避免被 stopForeground(REMOVE) 移除
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
            stopSelfResult(startId)
        } else if (next != null) {
            val promoted = activeTasks[next]!!
            startForegroundSafely(
                notificationId(next),
                next,
                buildProgressNotification(promoted.title, promoted.progress, DownloadStatus.DOWNLOADING, promoted.speedBps, promoted.remainingMs)
            )
            if (taskId >= 0) {
                notificationManager.notify(notificationId(taskId), terminalNotification)
            }
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
        val notification = buildPausedNotification(title, progress, taskId)
        if (activeTasks.isEmpty()) {
            if (foregroundTaskId >= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            foregroundTaskId = -1
            // 退出前台后再发暂停通知，避免被 stopForeground(REMOVE) 一并移除
            notificationManager.notify(notificationId(taskId), notification)
            stopSelfResult(startId)
        } else if (wasForeground || !activeTasks.containsKey(foregroundTaskId)) {
            val next = activeTasks.keys.first()
            val promoted = activeTasks[next]!!
            startForegroundSafely(
                notificationId(next),
                next,
                buildProgressNotification(promoted.title, promoted.progress, DownloadStatus.DOWNLOADING, promoted.speedBps, promoted.remainingMs)
            )
            notificationManager.notify(notificationId(taskId), notification)
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
            if (foregroundTaskId >= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            foregroundTaskId = -1
            stopSelfResult(startId)
        } else if (wasForeground || !activeTasks.containsKey(foregroundTaskId)) {
            val promoted = activeTasks[next]!!
            startForegroundSafely(
                notificationId(next),
                next,
                buildProgressNotification(promoted.title, promoted.progress, DownloadStatus.DOWNLOADING, promoted.speedBps, promoted.remainingMs)
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

    private fun buildProgressNotification(
        title: String,
        progress: Int,
        status: DownloadStatus? = null,
        speedBps: Long = 0L,
        remainingMs: Long = 0L
    ): Notification {
        val displayTitle = privacyTitle(title, R.string.download_notification_downloading)
        // 暂停态显示「已暂停 xx%」，其余显示「下载中 xx%」；速率与剩余时间仅在真实传输时出现
        val contentText = when {
            status == DownloadStatus.PAUSED ->
                getString(R.string.download_notification_paused, progress.coerceIn(0, 100))
            speedBps > 0L && remainingMs > 0L -> getString(
                R.string.download_notification_speed_eta,
                progress.coerceIn(0, 100),
                formatBytes(speedBps),
                formatEta(remainingMs)
            )
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
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.notification_action_pause_all),
                    actionIntent(ACTION_PAUSE_ALL, -1)
                ).build()
            )
            .build()
    }

    /** 暂停态通知用常规通知（可划掉、点击自动消失）；若沿用 setOngoing(true)，用户将无法自行清除。 */
    private fun buildPausedNotification(title: String, progress: Int, taskId: Int): Notification {
        val displayTitle = privacyTitle(title, R.string.download_notification_downloading)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_paused, progress.coerceIn(0, 100)))
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent())
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    getString(R.string.notification_action_resume),
                    actionIntent(ACTION_RESUME_TASK, taskId)
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.notification_action_cancel),
                    actionIntent(ACTION_CANCEL_TASK, taskId)
                ).build()
            )
            .build()
    }

    private fun buildCompletedNotification(title: String, filePath: String = "", taskId: Int = -1): Notification {
        val displayTitle = privacyTitle(title, R.string.download_notification_completed)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_completed))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(taskId, filePath.takeIf { it.isNotBlank() }))
            .build()
    }

    private fun buildFailedNotification(title: String, taskId: Int = -1): Notification {
        val displayTitle = privacyTitle(title, R.string.download_notification_failed)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(getString(R.string.download_notification_failed))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildOpenDownloadsIntent(taskId))
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.stat_notify_error,
                    getString(R.string.download_retry_all),
                    actionIntent(ACTION_RETRY_FAILED, -1)
                ).build()
            )
            .build()
    }

    /** 隐私模式下通知栏只留通用文案：任务标题就是视频名，会直接暴露正在下载的内容。 */
    private fun privacyTitle(title: String, fallbackRes: Int): String =
        if (appearance.privacyMode) getString(fallbackRes) else title.ifEmpty { getString(fallbackRes) }

    private fun buildOpenDownloadsIntent(taskId: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.ACTION_OPEN_DOWNLOADS
            if (taskId >= 0) putExtra(MainActivity.EXTRA_HIGHLIGHT_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            this,
            (MainActivity.ACTION_OPEN_DOWNLOADS.hashCode() + taskId).let { if (it == 0) 1 else it },
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun actionIntent(action: String, taskId: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            this.action = action
            if (taskId >= 0) putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            this,
            action.hashCode() + taskId * 31,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 完成通知的点击目标：有成品文件路径时直达本地播放页；失败通知不用本函数，另由 buildOpenDownloadsIntent 打开下载页并定位出错任务。 */
    private fun buildContentIntent(taskId: Int = -1, filePath: String? = null): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (filePath != null) {
                action = MainActivity.ACTION_PLAY_LOCAL
                data = Uri.parse("file://$filePath")
                putExtra(MainActivity.EXTRA_OPEN_FILE_PATH, filePath)
            }
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val requestCode = if (filePath != null) (taskId * 2 + 1) else 0
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
