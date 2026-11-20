package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadErrorCode
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.repository.DownloadSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * 下载队列调度：软并发槽位、待启动任务挑选、任务 Job 登记与链式调度。
 *
 * 只在锁内做内存判定（挑任务 + 快速失败），磁盘 I/O（续传点推导）全部在锁外完成；
 * 暂停序号用于识别「调度决定之后、真正开跑之前」发生的暂停。
 */
internal class DownloadQueueScheduler(
    private val store: DownloadTaskStore,
    private val budget: DownloadConcurrencyBudget,
    private val calls: DownloadCallTracker,
    private val updater: DownloadTaskUpdater,
    private val resolver: DownloadResumeResolver,
    private val host: DefaultDownloadTransferHost,
    private val gate: DownloadGateController,
    private val settings: DownloadSettings,
    private val scope: CoroutineScope,
    private val scopeJob: Job
) {

    private val downloadJobs = ConcurrentHashMap<Int, Job>()

    @Volatile
    private var queueOrder: List<Int> = settings.downloadQueueOrder

    fun startNextPendingTask() {
        // 闸门判定含跨进程读取（粘性广播 / ConnectivityManager），必须在锁外
        if (!gate.evaluate().allowed) return
        // 只在锁内做内存判定（挑任务 + 快速失败），磁盘 I/O 全部移到锁外。
        // tasksById / downloadJobs 均为 ConcurrentHashMap，分散读取不会撕裂。
        val tried = HashSet<Int>()
        while (true) {
            val candidate = store.lock.withLock { pickPendingTaskLocked(exclude = tried) } ?: return
            if (budget.activeCount >= budget.limit) return
            AppLogger.log("DownloadManager", "启动下一个等待任务: ${candidate.title}")
            // 暂停序号必须在磁盘 IO 之前取：推导期间用户按了暂停，序号就会变，启动复核随即放弃。
            // 否则会拿「旧的 PENDING 快照」把刚被暂停的任务重新拉起来。
            val epochAtSchedule = store.pauseEpochOf(candidate.id)
            // 锁外推导（含 .partmap 读取/删除、file.length()），不阻塞主线程 pause/cancel
            val plan = resolver.derive(candidate)
            val launched = store.lock.withLock {
                startTask(
                    candidate,
                    plan,
                    expectedStatus = DownloadStatus.PENDING,
                    scheduledPauseEpoch = epochAtSchedule
                )
                downloadJobs.containsKey(candidate.id)
            }
            if (!launched) return
            tried.add(candidate.id)
        }
    }

    /**
     * 启动一个任务。
     *
     * 统一负责槽位占位与释放：只有通过 [DownloadConcurrencyBudget.tryAcquire] 的调用才会进入
     * Job 作用域，占位由 Job 完成回调统一归还；登记本身用 putIfAbsent 保证幂等。
     */
    fun startTask(
        task: DownloadTask,
        plan: ResumePlan,
        expectedStatus: DownloadStatus? = null,
        scheduledPauseEpoch: Long = -1L
    ) {
        // 本方法会在多条路径上重入（startDownload / startNextPendingTask / updateConcurrencyLimit /
        // retryAllFailed）。收敛到同一把锁上，保证「检查 downloadJobs」与「占槽位再登记 job」成为
        // 一个原子步骤：否则并发重入会让同一任务登记两个 Job，前一个被覆盖后其 finally 的
        // 槽位归还就不再对应任何真实占位，计数被减成负数，并发上限永久失效。
        if (downloadJobs.containsKey(task.id)) return
        // 锁外推导续传点期间状态可能已变：这里先做一次廉价复核，避免白占一个槽位、白建一个 Job。
        val snapshot = store.tasksById[task.id]
        if (snapshot == null ||
            (expectedStatus != null && snapshot.status != expectedStatus) ||
            (scheduledPauseEpoch >= 0L && store.pauseEpochOf(task.id) != scheduledPauseEpoch)
        ) {
            AppLogger.log(
                "DownloadManager",
                "调度已过期，跳过启动: ${task.title} (id=${task.id}, 期望 $expectedStatus, 实际 ${snapshot?.status})"
            )
            return
        }
        // 闸门关闭时不占槽位、不启动：任务保持 PENDING，由条件变化或网络恢复重新拾起
        val gateState = gate.evaluate()
        if (!gateState.allowed) {
            if (task.status != DownloadStatus.PENDING) {
                updater.update(task.id) { it.copy(status = DownloadStatus.PENDING) }
            }
            // 通知栏说明等待原因，避免用户以为下载卡死。
            // 本方法在调用方的 store.lock 内执行：跨进程转发不能同步跑在锁里，
            // 这里挪到 IO 作用域异步转发。
            val noticeTaskId = task.id
            val noticeTitle = task.title
            val noticeProgress = DownloadQueuePolicy.progressPercent(task.downloadedBytes, task.totalBytes)
            scope.launch {
                updater.publishQueuedNotice(noticeTaskId, noticeTitle, noticeProgress)
            }
            return
        }
        // 占不到槽位则退回 PENDING，由完成回调的链式调度重新推导续传起点后拾起
        if (!budget.tryAcquire()) {
            if (task.status != DownloadStatus.PENDING) {
                updater.update(task.id) { it.copy(status = DownloadStatus.PENDING) }
            }
            AppLogger.log("DownloadManager", "下载槽位已满(${budget.limit})，任务 ${task.id} 进入等待队列")
            return
        }
        calls.forget(task.id)
        val slotReleased = AtomicBoolean(false)
        fun releaseSlot() {
            if (!scopeJob.isActive) return
            if (!slotReleased.compareAndSet(false, true)) return
            val remaining = budget.release()
            if (remaining < 0) {
                AppLogger.logError("DownloadManager", "activeSlots 计数异常并已纠正: " + remaining)
                budget.reset()
            }
            startNextPendingTask()
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                // 唯一的状态认定点：从锁外推导续传点到这里隔着磁盘 IO，用户可能已经暂停。
                // 这里的复核与 updateTask(PAUSED) 共用 store.lock，因此「暂停」与「开跑」严格有序：
                //   - 暂停先落库 → 复核不通过，本 Job 直接退出（槽位在完成回调归还）；
                //   - 暂停后到   → 此时 Job 已登记，pauseDownload 会 cancel 它，PAUSED 再覆盖状态。
                // 只靠锁外那次复核不够：对一个本来就 PAUSED 的任务「再次暂停」不改变 status，
                // 必须靠暂停序号识别。
                var started = false
                updater.update(task.id) { current ->
                    val statusMatches = expectedStatus == null || current.status == expectedStatus
                    val pauseUnchanged =
                        scheduledPauseEpoch < 0L || store.pauseEpochOf(task.id) == scheduledPauseEpoch
                    if (statusMatches && pauseUnchanged && !calls.isCancelled(task.id)) {
                        started = true
                        current.copy(status = DownloadStatus.DOWNLOADING)
                    } else {
                        current
                    }
                }
                if (!started) {
                    AppLogger.log("DownloadManager", "调度已过期（期间被暂停/取消），放弃启动: ${task.title}")
                    return@launch
                }

                AppLogger.log("DownloadManager", "开始下载: ${task.title} (并发槽位已获取, resumeFrom=${plan.bytes})")

                try {
                    host.download(task.id, task.url, task.filePath, plan)
                } catch (e: SourceChangedException) {
                    // 源文件已变，旧位图表/旧文件已无意义，丢弃后整份重下。
                    // 首下（无续传起点）时直接上抛交给失败处理，不做无谓的重试。
                    if (plan.bytes == 0L && plan.chunkMap == null) throw e
                    AppLogger.logError(
                        "DownloadManager",
                        "源文件已变化，丢弃旧进度后重新下载: ${task.title}, ${e.message}",
                        e
                    )
                    runCatching { File(task.filePath).takeIf { f -> f.exists() }?.delete() }
                    runCatching { File(task.filePath + PARTMAP_SUFFIX).delete() }
                    updater.update(task.id) { it.copy(downloadedBytes = 0, totalBytes = 0, errorMessage = "") }
                    host.download(task.id, task.url, task.filePath, ResumePlan())
                }

                if (currentCoroutineContext()[Job]?.isActive != true || calls.isCancelled(task.id)) {
                    AppLogger.log("DownloadManager", "下载被取消，保留暂停状态: ${task.title}")
                    return@launch
                }

                updater.update(task.id) { current ->
                    val onDisk = runCatching { File(current.filePath).length() }.getOrDefault(0L)
                    // totalBytes <= 0 表示本次一个字节都没落地（例如服务端对失效直链返回空 200）。
                    // 原判据在 totalBytes 未知时直接放行为 COMPLETED，会把空文件标成「已完成」；
                    // 且这正是「首个转发事件即终态」的成因之一（全程无进度回调，收尾直接发终态），
                    // 故一并收紧：未知总量不再视为成功。
                    if (current.totalBytes <= 0 || current.downloadedBytes != current.totalBytes ||
                        onDisk != current.totalBytes
                    ) {
                        AppLogger.logError(
                            "DownloadManager",
                            "完整性校验失败 ${task.title}: ${current.downloadedBytes}/${current.totalBytes} (磁盘 $onDisk)"
                        )
                        current.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = if (current.totalBytes <= 0) {
                                DownloadErrorCode.NO_DATA.name
                            } else {
                                DownloadErrorCode.INCOMPLETE.name
                            }
                        )
                    } else {
                        current.copy(status = DownloadStatus.COMPLETED)
                    }
                }
                AppLogger.log("DownloadManager", "下载完成: ${task.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentCoroutineContext()[Job]?.isActive != true || calls.isCancelled(task.id)) {
                    AppLogger.log("DownloadManager", "下载被取消（IO 中断），保留暂停状态: ${task.title}")
                    return@launch
                }
                AppLogger.logError("DownloadManager", "下载失败 ${task.title}: ${e.message}", e)
                updater.update(task.id) {
                    it.copy(status = DownloadStatus.FAILED, errorMessage = classifyError(e).name)
                }
            } finally {
                currentCoroutineContext()[Job]?.let { self -> downloadJobs.remove(task.id, self) }
                if (!downloadJobs.containsKey(task.id)) {
                    calls.forget(task.id)
                }
            }
        }
        job.invokeOnCompletion { releaseSlot() }

        // 占位与登记必须原子：并发重入一旦让两个 Job 落到同一 taskId，先登记的那个被覆盖，
        // 它的槽位释放就不再对应任何真实占位，activeSlots 会被减成负数。
        // putIfAbsent(LAZY 且未启动的 Job) 是原子的：只有成功登记的一方才有权 start()。
        val existing = downloadJobs.putIfAbsent(task.id, job)
        if (existing != null) {
            job.cancel()
            releaseSlot()
            AppLogger.log("DownloadManager", "任务 " + task.id + " 已在下载中，忽略重复调度")
            return
        }
        if (calls.isCancelled(task.id)) {
            downloadJobs.remove(task.id, job)
            job.cancel()
            releaseSlot()
            AppLogger.log("DownloadManager", "任务在登记后被取消，已归还并发槽位: " + task.id)
            return
        }
        job.start()
    }

    /**
     * 锁内挑选下一个待启动任务（排除 [exclude]）。返回 null 表示没有可用任务。
     *
     * 只读 ConcurrentHashMap，不做任何磁盘 I/O —— 这是持锁执行的关键约束：
     * 持锁做 I/O 会让主线程上的 pauseDownload / cancelDownload / startDownload 长时间等锁。
     */
    private fun pickPendingTaskLocked(exclude: Set<Int>): DownloadTask? =
        DownloadQueuePolicy.pickPendingTask(
            tasks = store.tasksById.values,
            activeJobIds = downloadJobs.keys,
            exclude = exclude,
            queueOrder = queueOrder,
            activeSlots = budget.activeCount,
            concurrencyLimit = budget.limit
        )

    fun updateConcurrencyLimit(maxConcurrent: Int) {
        budget.updateLimit(maxConcurrent)
        AppLogger.log("DownloadManager", "并发下载数已更新为: ${budget.limit}")

        // 锁内挑任务、锁外推导续传起点，避免批量启动时长时间持锁阻塞主线程
        while (budget.activeCount < budget.limit) {
            val candidate = store.lock.withLock { pickPendingTaskLocked(exclude = emptySet()) }
                ?: break
            val epochAtSchedule = store.pauseEpochOf(candidate.id)
            val plan = resolver.derive(candidate)
            val launched = store.lock.withLock {
                startTask(
                    candidate,
                    plan,
                    expectedStatus = DownloadStatus.PENDING,
                    scheduledPauseEpoch = epochAtSchedule
                )
                downloadJobs.containsKey(candidate.id)
            }
            if (!launched) break
        }
    }

    fun moveToFront(taskId: Int) {
        val current = queueOrder.toMutableList()
        current.remove(taskId)
        current.add(0, taskId)
        queueOrder = current
        settings.setDownloadQueueOrder(current)
        // 只是把该任务提前，不打断已经在跑的任务
        val task = store.tasksById[taskId] ?: return
        if (task.status == DownloadStatus.PENDING) startNextPendingTask()
    }

    fun isRunning(taskId: Int): Boolean = downloadJobs.containsKey(taskId)

    fun cancelJob(taskId: Int): Job? = downloadJobs.remove(taskId)?.also { it.cancel() }

    fun jobsSnapshot(): List<Job> = store.lock.withLock { downloadJobs.values.toList() }

    fun clearJobs() {
        downloadJobs.clear()
    }
}
