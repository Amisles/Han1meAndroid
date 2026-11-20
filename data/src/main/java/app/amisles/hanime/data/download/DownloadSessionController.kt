package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.model.StartDownloadResult
import java.io.File
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 下载任务的生命周期编排：新建、暂停、恢复、取消、全部暂停/恢复与网络触发的批量动作。
 *
 * 只做决策与状态迁移；实际启动交给 [DownloadQueueScheduler]，续传推导交给
 * [DownloadResumeResolver]，落盘与通知分别交给持久化与更新器。
 */
internal class DownloadSessionController(
    private val store: DownloadTaskStore,
    private val persistence: DownloadPersistence,
    private val scheduler: DownloadQueueScheduler,
    private val resolver: DownloadResumeResolver,
    private val updater: DownloadTaskUpdater,
    private val calls: DownloadCallTracker,
    private val gate: DownloadGateController,
    private val maintenance: DownloadStorageMaintenance,
    private val scope: CoroutineScope
) {

    fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String,
        videoId: String
    ): StartDownloadResult {
        val downloadUrl = url.toHttpUrlOrNull()
            ?.takeIf { it.scheme == "https" }
            ?.toString()
        if (downloadUrl == null) {
            // 脱敏：直链带时效签名，落盘后等同于泄露访问凭据
            AppLogger.logError(
                "DownloadManager",
                "拒绝下载：非法 url=\"${url.redactUrlForLog()}\" (title=$title, videoId=$videoId)"
            )
            return StartDownloadResult.InvalidUrl
        }
        val dir = maintenance.resolveDownloadDir()
        val uniqueSuffix = if (videoId.isNotBlank()) {
            videoId.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        } else {
            downloadUrl.hashCode().toString(36)
        }
        val baseName = "${title}_$quality".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${clampFileNameBase(baseName, uniqueSuffix)}_$uniqueSuffix.mp4"
        val targetFile = File(dir, fileName)
        val canonical = runCatching { targetFile.canonicalPath }.getOrDefault(targetFile.absolutePath)
        val dirCanonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!canonical.startsWith(dirCanonical + File.separator) && canonical != dirCanonical) {
            AppLogger.logError("DownloadManager", "拒绝下载：路径越界 fileName=\"$fileName\"")
            return StartDownloadResult.InvalidUrl
        }
        val filePath = targetFile.absolutePath

        // 需要「锁外推导续传状态」的既有任务；非空表示本方法复用既有任务而非新建
        var deferredResumeTask: DownloadTask? = null
        var createdTaskId: Int? = null

        store.lock.withLock {
            // 去重必须按「文件身份」而非 (url, quality)：downloadUrl 是带时效签名的 CDN 直链，
            // 重新拉取画质页会得到不同 url；而落盘路径由 (title, quality, videoId) 决定。若不按
            // filePath 去重，会并存两个 filePath 相同的任务并发写同一文件，删除任一条都会误删共享文件。
            val existingTask = store.tasksById.values.find { it.filePath == filePath }
            if (existingTask != null) {
                AppLogger.log(
                    "DownloadManager",
                    "该文件已有下载任务，复用 taskId=${existingTask.id}: $title ($quality)"
                )
                when (existingTask.status) {
                    // 暂停/失败：复用即续传，保持「点下载 = 继续」的既有语义。
                    // 续传推导含磁盘 IO，不能在前面的锁内做，故记下来移到锁外执行。
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> deferredResumeTask = existingTask
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING ->
                        return StartDownloadResult.AlreadyActive
                    DownloadStatus.COMPLETED -> return StartDownloadResult.AlreadyCompleted
                }
                // 非局部 return 与 return@withLock 混用容易写错：这里只退出锁块，真正的启动在锁外
                return@withLock
            }

            val taskId = store.nextId()

            val task = DownloadTask(
                id = taskId,
                title = title,
                quality = quality,
                url = downloadUrl,
                filePath = filePath,
                status = DownloadStatus.PENDING,
                thumbnailUrl = thumbnailUrl,
                videoId = videoId
            )

            store.tasksById[taskId] = task
            store.emit()
            persistence.enqueue(task)
            AppLogger.log("DownloadManager", "Starting download $taskId: $title ($quality)")

            // 新建任务此刻必然是 PENDING；若开跑前被暂停，启动复核会放弃
            scheduler.startTask(task, ResumePlan(), expectedStatus = DownloadStatus.PENDING)
            createdTaskId = taskId
        }

        deferredResumeTask?.let { task ->
            // 先取「暂停序号」再做磁盘 IO：推导期间用户若按了暂停，序号会变，启动复核随即放弃本次调度
            val epochAtSchedule = store.pauseEpochOf(task.id)
            val plan = resolver.derive(task)
            store.lock.withLock {
                scheduler.startTask(
                    task,
                    plan,
                    expectedStatus = task.status,
                    scheduledPauseEpoch = epochAtSchedule
                )
            }
            return StartDownloadResult.Started(task.id)
        }
        return createdTaskId?.let(StartDownloadResult::Started) ?: StartDownloadResult.InvalidUrl
    }

    fun pauseDownload(taskId: Int) = pauseInternal(taskId, scheduleNext = true)

    /**
     * @param scheduleNext 是否在暂停后立刻调度下一个 PENDING 任务。[pauseAll] 会传 false，
     *   把 N 次链式调度收敛成一次，避免「暂停 A 拉起 B、再暂停 B」的无谓启动/取消。
     */
    private fun pauseInternal(taskId: Int, scheduleNext: Boolean) {
        // 先记暂停意图：调度链可能在几毫秒后才发现这次暂停（见 [DownloadTaskStore.bumpPauseEpoch] 的说明）
        store.bumpPauseEpoch(taskId)
        // 用户/系统主动暂停时移出自动暂停集合：这不是断网触发的，不应在网络恢复后被自动拉起
        gate.forgetAutoPaused(taskId)

        calls.cancelAll(taskId)
        scheduler.cancelJob(taskId)
        // 复用 updater：状态切换（→PAUSED）会立即落盘
        updater.update(taskId) { it.copy(status = DownloadStatus.PAUSED) }
        // 避免空出的槽位不拾起 PENDING 任务导致其余任务永久挂起。
        if (scheduleNext) scheduler.startNextPendingTask()
        AppLogger.log("DownloadManager", "Download paused: $taskId")
    }

    fun resumeDownload(taskId: Int) {
        store.lock.withLock {
            resumeInternal(taskId)
        }
    }

    /**
     * 启动单个任务的续传：先在锁外推导续传状态，再持锁启动。
     * 调用方**不要**持有 [DownloadTaskStore.lock]（推导含磁盘 I/O）。
     */
    internal fun resumeInternal(taskId: Int) {
        val task = store.tasksById[taskId] ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        if (scheduler.isRunning(taskId)) return
        // 推导含磁盘 IO：期间可能被暂停/取消，故先记录期望状态与暂停序号，开跑前复核
        val expectedStatus = task.status
        val epochAtSchedule = store.pauseEpochOf(taskId)
        val plan = resolver.derive(task)
        store.lock.withLock {
            scheduler.startTask(
                task,
                plan,
                expectedStatus = expectedStatus,
                scheduledPauseEpoch = epochAtSchedule
            )
        }
        if (plan.chunkMap != null) {
            AppLogger.log(
                "DownloadManager",
                "Resume (chunked) download: $taskId, undone chunks=${plan.chunkMap.count { !it }}/${plan.chunkMap.size}"
            )
        } else {
            AppLogger.log("DownloadManager", "Resume download started: $taskId (resumeBytes=${plan.bytes})")
        }
    }

    fun pauseAll() {
        val active = store.lock.withLock {
            store.tasksById.values
                .filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                .map { it.id }
        }
        // 对所有任务（含此刻仍为 PAUSED 的）取号：批量恢复正在逐个拉起时，「暂停全部」必须能
        // 打断那些还没轮到、但已经在 resumeAll 名单里的任务。
        store.tasksById.keys.forEach { store.bumpPauseEpoch(it) }
        // 逐个暂停时不再链式调度：否则「暂停 A → 链路拉起 B → 暂停 B」会白白启动再取消，
        // 在 Service.onTimeout（主线程）这种场景下还会放大停顿。统一在最后调度一次。
        active.forEach { pauseInternal(it, scheduleNext = false) }
        scheduler.startNextPendingTask()
        AppLogger.log("DownloadManager", "pauseAll: 已暂停 " + active.size + " 个任务")
    }

    /**
     * 系统回收 dataSync 前台服务配额时的兜底暂停（由 DownloadService.onTimeout 调用）。
     *
     * 与用户主动「全部暂停」等价，单独命名只是为了在日志里能区分触发来源：前台服务被系统收回后
     * 进程里的协程并不会停，任务必须显式转 PAUSED，否则会失去前台豁免、随后被系统静默杀掉。
     */
    fun pauseAllForQuota() {
        AppLogger.log("DownloadManager", "系统回收前台服务配额，暂停全部下载")
        pauseAll()
    }

    fun resumeAll() {
        val paused = store.lock.withLock {
            store.tasksById.values.filter { it.status == DownloadStatus.PAUSED }.map { it.id }
        }
        paused.forEach { id ->
            val task = store.tasksById[id] ?: return@forEach
            // 取号后再推导：期间用户若暂停（哪怕对象本来就是 PAUSED，status 不会变）或点了
            // 「全部暂停」，序号都会变，据此跳过，不会出现「暂停全部」之后仍在拉起任务的情况。
            val epochAtSchedule = store.pauseEpochOf(id)
            val plan = resolver.derive(task)
            store.lock.withLock {
                scheduler.startTask(
                    task,
                    plan,
                    expectedStatus = DownloadStatus.PAUSED,
                    scheduledPauseEpoch = epochAtSchedule
                )
            }
        }
        AppLogger.log("DownloadManager", "resumeAll: 已恢复 " + paused.size + " 个任务")
    }

    /**
     * 一键重试全部失败任务。锁内只取 FAILED 任务列表，随后在锁外逐个推导续传状态，
     * 再持锁调用调度器（含并发槽位门控）；不复用 [resumeInternal]。
     * 调用方不要持有 [DownloadTaskStore.lock]，推导含磁盘 I/O。
     */
    fun retryAllFailed() {
        val failedIds = store.lock.withLock {
            store.tasksById.values.filter { it.status == DownloadStatus.FAILED }.map { it.id }
        }
        failedIds.forEach { id ->
            val task = store.tasksById[id] ?: return@forEach
            val epochAtSchedule = store.pauseEpochOf(id)
            val plan = resolver.derive(task)
            store.lock.withLock {
                val current = store.tasksById[id] ?: return@withLock
                if (current.status != DownloadStatus.FAILED) return@withLock
                scheduler.startTask(
                    current,
                    plan,
                    expectedStatus = DownloadStatus.FAILED,
                    scheduledPauseEpoch = epochAtSchedule
                )
            }
        }
        AppLogger.log("DownloadManager", "retryAllFailed: 已触发 " + failedIds.size + " 个失败任务重试")
    }

    fun cancelDownload(taskId: Int) {
        calls.cancelAll(taskId)
        val job = scheduler.cancelJob(taskId)

        // 锁内只做内存移除；文件删除移到锁外（delete 会触发 FS 元数据更新，持锁期间
        // 主线程的 pauseDownload / startDownload 会长时间等锁）
        val removed = store.lock.withLock {
            val task = store.tasksById.remove(taskId)
            store.emit()
            task
        }
        // 走与 updater 相同的串行队列，避免 DELETE 与在途 UPSERT 竞争导致任务复活
        persistence.enqueueDelete(taskId)
        updater.forget(taskId)
        store.forgetPauseEpoch(taskId)
        // 任务记录已删除，无法再走更新通道；必须显式通知前台服务撤销该任务的
        // 常驻通知并重算活动集合，否则通知（ongoing 不可划掉）与 dataSync 前台服务会永久残留。
        updater.notifyRemoved(taskId)
        AppLogger.log("DownloadManager", "Download cancelled: $taskId")

        if (removed == null) {
            if (job == null) calls.forget(taskId)
            return
        }
        // 文件删除必须等 Job 真正结束：Job.cancel() 是协作式的，写循环里检查到取消后仍有
        // 正在进行的 write。若此时就删文件，写入会落到已 unlink 的 inode 上，随后新建的
        // 同名任务会指向另一个 inode，表现为「新任务下载到旧数据」并泄漏磁盘空间。
        scope.launch {
            job?.join()
            if (job == null) calls.forget(taskId)
            // 等待期间用户可能对同一部视频重新发起下载并复用了这个路径：
            // 此时文件归新任务所有，绝不能删（新任务可能正靠它续传）。
            val pathTaken = store.lock.withLock {
                store.tasksById.values.any { it.filePath == removed.filePath }
            }
            if (pathTaken) {
                AppLogger.log(
                    "DownloadManager",
                    "取消后该路径已被新任务接管，跳过文件删除: ${removed.filePath}"
                )
                return@launch
            }
            resolver.deleteFiles(removed)
        }
    }

    suspend fun suspendForBackupImport() = withContext(Dispatchers.IO) {
        val jobs = scheduler.jobsSnapshot()
        pauseAll()
        val remaining = scheduler.jobsSnapshot()
        (jobs + remaining).distinct().forEach { it.join() }
        persistence.flush()
    }

    fun pauseForNetworkLoss() {
        val active = store.lock.withLock {
            store.tasksById.values
                .filter {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                .map { it.id }
        }
        if (active.isEmpty()) return
        AppLogger.log("DownloadManager", "网络已断开，暂停 " + active.size + " 个任务等待恢复")
        // 用暂停语义（含取消在途调用）使位图表落盘。
        // 必须先暂停再登记：pauseInternal 会清除自动暂停标记，顺序反过来会让网络恢复后无法自动续传。
        active.forEach {
            pauseInternal(it, scheduleNext = false)
            gate.markAutoPaused(it)
        }
        scheduler.startNextPendingTask()
    }

    fun resumeAfterNetwork() {
        // 换到 Wi-Fi 后闸门可能刚好放行，先重算再决定要不要续传
        scheduler.startNextPendingTask()
        val paused = store.lock.withLock {
            // 只恢复「因断网自动暂停」的那些，手动暂停的不动
            store.tasksById.values
                .filter { it.status == DownloadStatus.PAUSED && gate.isAutoPaused(it.id) }
                .map { it.id }
        }
        if (paused.isEmpty()) return
        AppLogger.log("DownloadManager", "网络已恢复，自动续传 " + paused.size + " 个已暂停任务")
        paused.forEach { id ->
            gate.forgetAutoPaused(id)
            resumeInternal(id)
        }
    }

    fun pendingCount(): Int = store.tasksById.values.count { it.status == DownloadStatus.PENDING }
}
