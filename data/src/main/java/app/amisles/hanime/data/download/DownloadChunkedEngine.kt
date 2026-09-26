package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.remote.VideoAntiHotlink
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Request

/**
 * 分块并行下载引擎：worker 调度、慢块驱逐、聚合低位全量重建、零进度看门狗、位图表节流落盘。
 *
 * 以 [DownloadManager] 的扩展函数承载，避免把类内状态整体放开为 internal；
 * 调参常量见 `DownloadTuning.kt`，位图表读写与分块几何见 `DownloadPartmap.kt`。
 */
/**
 * 驱逐后的重连退避：按已驱逐次数线性递增并封顶，附加与块序号相关的错峰偏移，
 * 避免多个块同时重建连接后又同时被判慢。
 */
private fun evictionBackoffMs(evictedTimes: Int, chunkIndex: Int): Long {
    val base = (EVICTION_BACKOFF_BASE_MS * evictedTimes).coerceAtMost(EVICTION_BACKOFF_MAX_MS)
    val stagger = (chunkIndex % 5) * EVICTION_BACKOFF_STAGGER_MS
    return base + stagger
}

/**
 * 全量重建在途分块连接：取消所有活跃连接，由 worker 重新入队并建新连接重试。
 *
 * 对限速型 CDN，「换一批新请求」往往能立刻拿到整块高速投递，本函数即「用户手动暂停
 * 再恢复」的自动化版本。计数计入 [globalRebuildCount]（独立上限），不消耗 evictionTotal；
 * 但必须递增 chunkEvictCount，否则 worker 会把主动取消误判为真实 IO 失败。
 */
private fun rebuildAllChunkConnections(
    taskId: Int,
    reason: String,
    aggregateBps: Long,
    chunkCount: Int,
    chunkCallRefs: Array<AtomicReference<Call?>>,
    chunkEvictCount: AtomicIntegerArray,
    globalRebuildCount: AtomicInteger
): Int {
    var rebuilt = 0
    for (i in 0 until chunkCount) {
        val call = chunkCallRefs[i].get() ?: continue
        chunkEvictCount.incrementAndGet(i)
        runCatching { call.cancel() }
        rebuilt++
    }
    if (rebuilt > 0) {
        val seq = globalRebuildCount.incrementAndGet()
        AppLogger.log(
            "DownloadManager",
            "全量重建分块连接(t=$taskId): $reason（重建 $rebuilt 条，聚合 ${aggregateBps / 1024}KB/s，第 $seq 次）"
        )
    }
    return rebuilt
}

/**
 * 诊断日志格式化（读取并归零窗口计数）。判读要点：
 * 1) 连接已打满但聚合仍低 → 瓶颈不在连接数（服务端限速或本机存储受限）；
 * 2) 读占接近 100% → 时间几乎全花在等网络；
 * 3) 每MB写偏大或慢写不为 0 → 本地写入在阻塞；
 * 4) 最慢块远低于最快块且聚合健康 → 属正常分配不均，不应驱逐。
 */
private fun formatChunkDiag(
    taskId: Int,
    activeCalls: Int,
    workerCount: Int,
    aggregateBps: Long,
    chunkRates: String,
    io: ChunkIoStats,
    evictions: Int,
    watchdogs: Int
): String {
    val writeNanos = io.writeNanos.getAndSet(0L)
    val writeBytes = io.writeBytes.getAndSet(0L)
    val readNanos = io.readNanos.getAndSet(0L)
    val readBytes = io.readBytes.getAndSet(0L)
    val slowWrites = io.slowWrites.getAndSet(0)
    val msPerMb = if (writeBytes > 0) (writeNanos / 1e6) / (writeBytes / 1048576.0) else 0.0
    val ioNanos = readNanos + writeNanos
    val readPercent = if (ioNanos > 0) readNanos * 100 / ioNanos else 0L
    return "诊断[t=$taskId]: 连接=$activeCalls/$workerCount 聚合=${aggregateBps / 1024}KB/s " +
        "块速率(KB/s)[$chunkRates] 读占=${readPercent}%(读${readBytes / 1024}KB 写${writeBytes / 1024}KB) " +
        "每MB写=${msPerMb.toInt()}ms 慢写(>${SLOW_WRITE_WARN_MS}ms)=$slowWrites " +
        "累计驱逐=$evictions 看门狗=$watchdogs"
}

/**
 * 多线程分块并行下载。
 *
 * - 块数与并行连接数解耦：块数 = ceil(总量 / [CHUNK_SIZE])，并行连接数由 [computeParallelism] 推导，
 *   使同时在写的偏移集中在连续区域，而非数块各偏居数百 MB 之外；
 * - 仅对未完成块发起请求；
 * - 慢块驱逐：对持续低吞吐块取消连接后由空闲 worker 以新连接重试；
 * - 不 setLength 预分配，以避免空洞。
 */
internal suspend fun DownloadManager.downloadFileChunked(
    taskId: Int,
    url: String,
    filePath: String,
    totalBytes: Long,
    singleBps: Long = 0L,
    resumeMap: BooleanArray? = null
) {
    // 块数由固定块长 CHUNK_SIZE 推导（不再等于并行连接数），使并发写入窗口只覆盖文件的一段
    // 连续区域，重试与续传粒度同步变细。块布局仍由 chunkSize = totalBytes / chunkCount 推导，
    // 因此旧位图表（chunkCount=3）继续按原布局续传，.partmap 格式无需迁移。
    val chunkCount = resumeMap?.size
        ?: ceilDiv(totalBytes, CHUNK_SIZE).coerceIn(1L, MAX_PARTMAP_CHUNKS.toLong()).toInt()
    val outputFile = File(filePath)
    val partmapFile = File(filePath + PARTMAP_SUFFIX)
    // 不足 2 块无并行收益，退回单连接；同时清掉可能残留的位图表，避免留下孤儿文件
    if (chunkCount < 2) {
        if (partmapFile.exists()) runCatching { partmapFile.delete() }
        downloadFileSingle(taskId, url, filePath, 0L)
        return
    }

    val chunkDone = resumeMap ?: BooleanArray(chunkCount)
    if (resumeMap == null) {
        // 首下：清空旧文件与旧位图表，重新分块
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()
        outputFile.createNewFile()
        // 位图表是分块续传的唯一依据，首下写失败必须立刻放弃分块路径：否则一旦崩溃/暂停，
        // deriveResumeState 会退化为按文件长度做单连接续传，而分块文件是带空洞的稀疏写。
        if (!writePartmap(partmapFile, chunkCount, chunkDone)) {
            AppLogger.logError(
                "DownloadManager",
                "分块下载：位图表写入失败，本次改为单连接顺序下载（该文件不可续传）: $filePath",
                null
            )
            runCatching { if (outputFile.exists()) outputFile.delete() }
            // 一并清掉失败写入残留的 .tmp：否则后续 deriveResumeState 会把它当作「位图表替换被
            // 中断」，从而拒绝续传这次单连接下载已完成的连续前缀（该前缀本身是可信的）。
            runCatching { File(filePath + PARTMAP_SUFFIX + ".tmp").delete() }
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }
    } else if (!partmapFile.exists()) {
        if (!writePartmap(partmapFile, chunkCount, chunkDone)) {
            AppLogger.log("DownloadManager", "续传位图表重建失败，该文件可能无法跨进程续传: ${partmapFile.name}")
        }
    }

    val chunkSize = totalBytes / chunkCount
    val chunkDownloaded = AtomicLongArray(chunkCount)
    // 续传：恢复已完成块的已下载字节，计入总进度
    if (resumeMap != null) {
        for (i in chunkDone.indices) {
            if (chunkDone[i]) {
                val start = i * chunkSize
                val end = if (i == chunkCount - 1) totalBytes - 1 else start + chunkSize - 1
                chunkDownloaded.set(i, end - start + 1)
            }
        }
    }

    val lock = Any()
    val initialDone = chunkDone.count { it }
    val completedChunks = AtomicInteger(initialDone)
    val chunkCallRefs = Array(chunkCount) { AtomicReference<Call?>(null) }
    val chunkLastBytes = AtomicLongArray(chunkCount)
    val chunkSlowSince = AtomicLongArray(chunkCount)
    val chunkFirstObserved = AtomicLongArray(chunkCount)
    val chunkAttempts = IntArray(chunkCount)
    // 驱逐计数与重试计数分离：被监控驱逐不算「失败」，但需要一个总预算，超限即降级单连接
    val chunkEvictCount = AtomicIntegerArray(chunkCount)
    // 诊断与预算计数：ioStats 供 monitor 判别「时间花在读还是写」，两个计数器供聚合判据与日志
    val ioStats = ChunkIoStats()
    val evictionTotal = AtomicInteger(0)
    val watchdogCount = AtomicInteger(0)
    val globalRebuildCount = AtomicInteger(0)
    val queue = ArrayDeque<Int>().apply { for (i in 0 until chunkCount) if (!chunkDone[i]) addLast(i) }

    // 标记「服务器对分块请求返回 200 忽略 Range」，用于触发整任务回退单连接下载
    val rangeUnsupported = AtomicBoolean(false)
    // 标记「源文件已变化」。与 rangeUnsupported 同理，必须先记录再取消作用域 ——
    // 直接 throw 会被 coroutineScope 的取消路径吞成 CancellationException。
    val sourceChanged = AtomicReference<SourceChangedException?>(null)
    try {
        coroutineScope {
            val csJob = this.coroutineContext[Job]
            fun takeChunk(): Int = synchronized(queue) { if (queue.isNotEmpty()) queue.removeFirst() else -1 }
            fun requeueChunk(i: Int) = synchronized(queue) { queue.addLast(i) }

            // 并行连接数不等同块数（块数可能上百），单独按网络类型 + 实测吞吐推导，
            // 并受全局软预算与 MAX_PARALLEL_CONNECTIONS 约束。
            val workerCount = computeParallelism(currentNetworkClass(), singleBps, chunkCount)
            val workers = (0 until workerCount).map { _ ->
                launch(Dispatchers.IO) {
                    while (currentCoroutineContext()[Job]?.isActive == true && !isTaskCancelled(taskId)) {
                        val idx = takeChunk()
                        if (idx < 0) break
                        // 逐块采样基准必须以「本次连接即将开始」为起点：块被驱逐后可能在队列里等待很久，
                        // 若沿用等待期的计时，重新取到后第一次采样就会被判成「已持续慢」而立刻驱逐。
                        chunkLastBytes.set(idx, chunkDownloaded.get(idx))
                        chunkFirstObserved.set(idx, System.currentTimeMillis())
                        chunkSlowSince.set(idx, 0L)
                        // 取块时快照驱逐计数，失败时据此区分「被监控驱逐」与「真实 IO 失败」
                        val evictedBefore = chunkEvictCount.get(idx)
                        val start = idx * chunkSize
                        val end = if (idx == chunkCount - 1) totalBytes - 1 else start + chunkSize - 1
                        try {
                            downloadChunk(
                                taskId, url, outputFile, idx, start, end,
                                totalBytes, chunkDownloaded, chunkCallRefs[idx], ioStats
                            )
                            synchronized(lock) { chunkDone[idx] = true }
                            completedChunks.incrementAndGet()
                            // 诊断：分块完成耗时与均速。慢块问题的本质是「块的耗时分布」——
                            // 一个块用 2s 还是 120s 直接决定总时长；这也是判断服务端是否
                            // 「部分区间快、部分区间慢」（边缘缓存命中 vs 回源）的唯一直接证据。
                            val blockBytes = end - start + 1
                            val spentMs = (System.currentTimeMillis() - chunkFirstObserved.get(idx))
                                .coerceAtLeast(1L)
                            AppLogger.log(
                                "DownloadManager",
                                "分块完成: #$idx ${blockBytes / 1024}KB 用时=${spentMs / 1000}s " +
                                    "均速=${blockBytes * 1000 / spentMs / 1024}KB/s"
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: IOException) {
                            // 任务已取消则停止重试；否则（慢块驱逐/瞬断）重新入队以新连接重试
                            if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(taskId)) break
                            if (e is RangeNotSupportedException) {
                                AppLogger.log("DownloadManager", "分块下载检测到不支持 Range，回退单连接: ${e.message}")
                                rangeUnsupported.set(true)
                                synchronized(queue) { queue.clear() }
                                csJob?.cancel()
                                return@launch
                            }
                            if (e is SourceChangedException) {
                                AppLogger.log("DownloadManager", "分块下载发现源文件已变化，终止本次下载: ${e.message}")
                                sourceChanged.set(e)
                                synchronized(queue) { queue.clear() }
                                csJob?.cancel()
                                return@launch
                            }
                            // 区分「慢块监控驱逐」与「真实 IO 失败」：驱逐是监控器的主动决策，
                            // 不应计入 MAX_CHUNK_ATTEMPTS，否则一次本可自愈的卡顿会被升级为整任务失败。
                            val evictedNow = chunkEvictCount.get(idx)
                            val evicted = evictedNow > evictedBefore
                            if (evicted) {
                                // 驱逐总预算耗尽：判定分块路径对当前网络/本机存储无收益，降级单连接续传。
                                // 预算是「本任务累计驱逐次数」（跨块），否则块数一多总驱逐量会随块数膨胀。
                                if (evictionTotal.get() >= MAX_EVICTIONS_PER_TASK) {
                                    throw ChunkedInefficientException(
                                        "分块下载持续无收益（已驱逐 $evictedNow 次），降级单连接续传"
                                    )
                                }
                                // 退避后再重连，避免「砍了立刻重连 → 又被判慢」的自激循环
                                delay(evictionBackoffMs(evictedNow, idx))
                            } else {
                                val attempts = ++chunkAttempts[idx]
                                if (attempts >= MAX_CHUNK_ATTEMPTS) {
                                    throw IOException("分块 $idx 重试 $attempts 次仍失败，终止下载", e)
                                }
                            }
                            // 不清零 chunkDownloaded / chunkLastBytes：重试时 downloadChunk 会从块内
                            // 已有偏移续传，清零会让已下载字节被重复计数并丢失续传起点。
                            chunkFirstObserved.set(idx, 0L)
                            chunkSlowSince.set(idx, 0L)
                            val reason = if (evicted) "被驱逐(第${evictedNow}次)"
                                else "IO 失败(第${chunkAttempts[idx]}次)"
                            AppLogger.log("DownloadManager", "分块 $idx $reason，重新入队以新连接续传: ${e.message}")
                            requeueChunk(idx)
                        }
                    }
                }
            }

            // 慢块阈值不能是绝对常量：单连接实测吞吐会被均分到各条并行连接上，若按固定值判定，
            // 弱网会把每条正常连接都判成慢块 → 驱逐 → 重试耗尽 → 整个任务失败。
            // 改为「实测每连接基准速率的 SLOW_RELATIVE_PERCENT%，下不低于 FLOOR、上不超过 CAP」；
            // 基准的分母取实际并行连接数（workerCount）而非块总数 —— 块数可能上百，拿块数当分母
            // 会把阈值压到接近 0，驱逐形同失效。
            val perChunkBaseline = if (singleBps > 0) singleBps / workerCount else 0L
            val slowThresholdBps = maxOf(
                SLOW_THRESHOLD_FLOOR_BPS,
                minOf(perChunkBaseline * SLOW_RELATIVE_PERCENT / 100, SLOW_THRESHOLD_CAP_BPS)
            )
            AppLogger.log(
                "DownloadManager",
                "分块下载: chunkCount=$chunkCount, 并行连接=$workerCount, " +
                    "单连接实测=${singleBps / 1024}KB/s, 慢块阈值=${slowThresholdBps / 1024}KB/s"
            )

            // 慢块监控器。周期性采样逐块吞吐，对持续低于阈值的分块取消其连接、交 worker 重领。
            // 判慢必须结合聚合速率：只看单块速率时，多连接共享同一瓶颈会让每条连接份额天然偏低，
            // 全部被判慢 → 全部驱逐重连 → 速率长期停在低位。现在：本窗口零字节的「真停滞」一律允许
            // 驱逐；「在动但慢」仅当聚合速率也不健康时才驱逐。
            val monitor = launch(Dispatchers.IO) {
                var prevTime = System.currentTimeMillis()
                // 续传恢复时 chunkDownloaded 已预置「已完成块」的字节数，首个窗口必须以它为基准，
                // 否则会把历史字节当成这一窗口的增量（出现聚合速率的假峰值）。
                var prevTotalBytes = 0L
                for (i in 0 until chunkCount) prevTotalBytes += chunkDownloaded.get(i)
                // 聚合持续低位计时与上次全量重建时刻（见下方「全量重建」分支）
                var aggregateLowSince = 0L
                var lastRebuildAt = 0L
                // 本任务观测到的最高聚合速率：作为「健康」的相对基准（见下）
                var peakAggregateBps = 0L
                while (completedChunks.get() < chunkCount &&
                    currentCoroutineContext()[Job]?.isActive == true &&
                    !isTaskCancelled(taskId)) {   // 销毁期停止采样
                    delay(SLOW_SAMPLE_MS)
                    val now = System.currentTimeMillis()
                    val dt = (now - prevTime) / 1000.0
                    prevTime = now
                    if (dt <= 0) continue
                    val doneSnapshot = synchronized(lock) { chunkDone.copyOf() }
                    // 聚合速率与在途连接数（判慢与诊断都用）
                    var totalBytesNow = 0L
                    var activeCalls = 0
                    for (i in 0 until chunkCount) {
                        totalBytesNow += chunkDownloaded.get(i)
                        if (!doneSnapshot[i] && chunkCallRefs[i].get() != null) activeCalls++
                    }
                    val aggregateBps = ((totalBytesNow - prevTotalBytes) / dt).toLong()
                    prevTotalBytes = totalBytesNow
                    // 健康阈值相对化：取「本任务观测峰值 / AGGREGATE_HEALTHY_RATIO」与绝对下限的较大者。
                    // 只用固定下限时，长期低位会被判为健康，白白错过干预时机。
                    if (aggregateBps > peakAggregateBps) peakAggregateBps = aggregateBps
                    val aggregateHealthyBps = maxOf(
                        AGGREGATE_HEALTHY_FLOOR_BPS,
                        peakAggregateBps / AGGREGATE_HEALTHY_RATIO,
                        if (singleBps > 0) singleBps * AGGREGATE_HEALTHY_PERCENT / 100 else 0L
                    )
                    val aggregateHealthy = aggregateBps >= aggregateHealthyBps
                    // 逐块速率明细（只列在途块，最多 DIAG_MAX_CHUNK_RATES 条）：
                    // 「总量低但每条连接都在动」与「只有一两条在动、其余恒为 0」病因不同，
                    // 只看最快/最慢会漏掉后者 —— 而后者正是服务端串行派发数据的典型特征。
                    val ratesText = StringBuilder()
                    var listedRates = 0
                    for (i in 0 until chunkCount) {
                        if (doneSnapshot[i]) continue
                        val bytes = chunkDownloaded.get(i)
                        val rate = ((bytes - chunkLastBytes.get(i)) / dt).toLong()
                        chunkLastBytes.set(i, bytes)
                        if (chunkFirstObserved.get(i) == 0L) chunkFirstObserved.set(i, now)
                        val observedFor = now - chunkFirstObserved.get(i)
                        val call = chunkCallRefs[i].get()
                        if (call != null && listedRates < DIAG_MAX_CHUNK_RATES) {
                            ratesText.append('#').append(i).append(':').append(rate / 1024).append(' ')
                            listedRates++
                        }
                        if (rate < slowThresholdBps) {
                            // 宽限期按「本次取块时刻」计，**不能**附加 `bytes == 0L` 条件 ——
                            // bytes 是块累计字节，被驱逐过的块重连后必然 > 0，等于没有宽限，
                            // 新连接还没跑完 TCP 慢启动就被判慢驱逐，形成自激循环。
                            if (observedFor < SLOW_GRACE_MS) continue
                            // 聚合健康时不做任何驱逐：此时「某条连接 0 字节」只是服务端在服务别人，
                            // 驱逐它只会增加请求数、加重服务端排队。
                            if (aggregateHealthy) {
                                chunkSlowSince.set(i, 0L)
                                continue
                            }
                            val slowSince = chunkSlowSince.get(i)
                            if (slowSince == 0L) {
                                chunkSlowSince.set(i, now)
                            } else if (now - slowSince >= SLOW_DURATION_MS && call != null) {
                                // 驱逐计入独立预算（与真实 IO 失败分开统计）
                                val evictSeq = chunkEvictCount.incrementAndGet(i)
                                evictionTotal.incrementAndGet()
                                AppLogger.log(
                                    "DownloadManager",
                                    "慢块驱逐：chunk $i 速率 ${rate / 1024}KB/s(聚合 ${aggregateBps / 1024}KB/s) " +
                                        "持续 ${(now - slowSince) / 1000}s，重分配连接(第 $evictSeq 次)"
                                )
                                runCatching { call.cancel() }
                                chunkSlowSince.set(i, 0L)
                            }
                        } else {
                            chunkSlowSince.set(i, 0L)
                        }
                    }
                    // 聚合持续低位 → 全量重建连接（自动化用户手动的「暂停再恢复」）。
                    // 对限速型 CDN，换一批新请求比继续等更有效；三重约束防止变成重连风暴：
                    // 持续时长、冷却间隔、总次数上限。
                    if (!aggregateHealthy) {
                        if (aggregateLowSince == 0L) aggregateLowSince = now
                    } else {
                        aggregateLowSince = 0L
                    }
                    if (aggregateLowSince != 0L &&
                        now - aggregateLowSince >= AGGREGATE_LOW_DURATION_MS &&
                        now - lastRebuildAt >= GLOBAL_REBUILD_COOLDOWN_MS &&
                        globalRebuildCount.get() < MAX_GLOBAL_REBUILDS_PER_TASK
                    ) {
                        lastRebuildAt = now
                        rebuildAllChunkConnections(
                            taskId = taskId,
                            reason = "聚合持续低于阈值 ${(now - aggregateLowSince) / 1000}s",
                            aggregateBps = aggregateBps,
                            chunkCount = chunkCount,
                            chunkCallRefs = chunkCallRefs,
                            chunkEvictCount = chunkEvictCount,
                            globalRebuildCount = globalRebuildCount
                        )
                    }
                    if (DIAG_ENABLED) {
                        AppLogger.log(
                            "DownloadManager",
                            formatChunkDiag(
                                taskId, activeCalls, workerCount, aggregateBps,
                                if (listedRates == 0) "无在途块" else ratesText.toString().trim(),
                                ioStats, evictionTotal.get(), watchdogCount.get()
                            )
                        )
                    }
                }
            }

            // 进度上报 + 位图表节流落盘
            val reporter = launch(Dispatchers.IO) {
                var lastBitmapPersist = 0L
                var lastPersistedDone = completedChunks.get()
                var lastReportedSum = -1L
                // 零进度看门狗用的「上次确有字节增长的时刻」
                var lastProgressAt = System.currentTimeMillis()
                while (completedChunks.get() < chunkCount &&
                    currentCoroutineContext()[Job]?.isActive == true &&
                    !isTaskCancelled(taskId)) {
                    delay(REPORT_INTERVAL_MS)
                    var sum = 0L
                    for (i in 0 until chunkCount) sum += chunkDownloaded.get(i)
                    val now = System.currentTimeMillis()
                    // 字节数没有变化时不必再提交一次状态更新（多任务并发下是无谓的列表重建）
                    if (sum != lastReportedSum) {
                        lastReportedSum = sum
                        lastProgressAt = now
                        updateTask(taskId) { it.copy(downloadedBytes = sum, totalBytes = totalBytes) }
                    } else if (now - lastProgressAt >= ZERO_PROGRESS_TRIGGER_MS) {
                        // 零进度看门狗：整任务连续 ZERO_PROGRESS_TRIGGER_MS 无字节增长时的兜底，
                        // 与 monitor 的「聚合持续低位」共用同一个全量重建动作。
                        lastProgressAt = now
                        val watchdogRebuilt = rebuildAllChunkConnections(
                            taskId = taskId,
                            reason = "${ZERO_PROGRESS_TRIGGER_MS / 1000}s 无字节增长",
                            aggregateBps = 0L,
                            chunkCount = chunkCount,
                            chunkCallRefs = chunkCallRefs,
                            chunkEvictCount = chunkEvictCount,
                            globalRebuildCount = globalRebuildCount
                        )
                        if (watchdogRebuilt > 0) watchdogCount.incrementAndGet()
                    }
                    synchronized(lock) {
                        val done = completedChunks.get()
                        // 位图表落盘：每 5s 一次，但有块完成时立即落盘 —— 块边界才是「丢进度重下代价
                        // 变大」的时刻，而高频 write+delete+rename 会与数据写抢 IO。
                        if (done != lastPersistedDone || now - lastBitmapPersist > PARTMAP_PERSIST_MS) {
                            lastBitmapPersist = now
                            lastPersistedDone = done
                            writePartmap(partmapFile, chunkCount, chunkDone)
                        }
                    }
                }
                if (outputFile.exists()) {
                    synchronized(lock) { writePartmap(partmapFile, chunkCount, chunkDone) }
                }
            }

            workers.forEach { it.join() }
            monitor.cancel()
            reporter.cancel()
        }
    } catch (ce: CancellationException) {
        if (!rangeUnsupported.get() && sourceChanged.get() == null) throw ce
    } catch (inefficient: ChunkedInefficientException) {
        // 不让「驱逐预算耗尽」升级为任务失败：保留位图表，改用单连接从「已完成块的连续前缀」处续传
        AppLogger.log("DownloadManager", "分块下载降级单连接续传: ${inefficient.message}")
        // 记住「该文件分块无收益」，使后续暂停/恢复直接走单连接，避免周期性震荡
        singleConnectionFiles.add(filePath)
        if (isTaskCancelled(taskId)) return
        val contiguous = contiguousDoneBytes(chunkDone, chunkCount, totalBytes)
        downloadFileSingle(taskId, url, filePath, contiguous)
        // 单连接写满后位图表已无意义（其分块布局与单连接续传冲突），完整落盘时清理
        val fullLength = runCatching { outputFile.length() }.getOrDefault(0L)
        if (partmapFile.exists() && fullLength >= totalBytes) {
            runCatching { partmapFile.delete() }
        }
        return
    }

    // 源文件已变：本函数不再收尾，交由上层丢弃旧进度后整份重下
    sourceChanged.get()?.let { throw it }

    // 已在作用域取消时清空队列并删除位图表，此处用单连接把文件从头写满（FileOutputStream 会截断旧分块数据）。
    if (rangeUnsupported.get()) {
        AppLogger.log("DownloadManager", "降级单连接下载: taskId=$taskId, url=${url.redactUrlForLog()}")
        if (partmapFile.exists()) partmapFile.delete()
        downloadFileSingle(taskId, url, filePath, 0L)
        return
    }

    if (isTaskCancelled(taskId)) {
        if (outputFile.exists()) {
            synchronized(lock) { writePartmap(partmapFile, chunkCount, chunkDone) }
        }
        AppLogger.log("DownloadManager", "分块下载中止（暂停/取消）: taskId=$taskId，位图表已保留供续传")
        return
    }

    // 收尾校验：所有块完成且磁盘大小达标才成功，否则保留位图表供续传
    var allDone = true
    synchronized(lock) { for (i in chunkDone.indices) if (!chunkDone[i]) { allDone = false; break } }
    var totalDownloaded = 0L
    for (i in 0 until chunkCount) totalDownloaded += chunkDownloaded.get(i)
    val onDisk = runCatching { outputFile.length() }.getOrDefault(0L)
    updateTask(taskId) { it.copy(downloadedBytes = totalDownloaded.coerceAtMost(totalBytes), totalBytes = totalBytes) }

    // 每个分块已精确校验「写入长度 == 区间长度」，各块区间之和恰为 totalBytes，
    if (allDone && totalDownloaded >= totalBytes && onDisk >= totalBytes) {
        partmapFile.delete()   // 成功后清理位图表
    } else {
        // 完整性不达标：抛出异常触发 FAILED 并保留位图表，下次续传仅补未完成块
        throw IOException("分块下载完整性校验失败：allDone=$allDone, $totalDownloaded/$totalBytes, disk=$onDisk")
    }
}

/**
 * 下载单个分块（HTTP Range 请求），写入 outputFile 的 [start, end] 区间。
 * 块内读取循环响应协程取消（暂停 / 取消时及时退出）。
 * @param callRef 该分块当前连接的引用，供监控器精准取消「单个慢块」而不影响其它块。
 */
private suspend fun DownloadManager.downloadChunk(
    taskId: Int,
    url: String,
    outputFile: File,
    index: Int,
    start: Long,
    end: Long,
    totalBytes: Long,
    chunkDownloaded: AtomicLongArray,
    callRef: AtomicReference<Call?>,
    io: ChunkIoStats
) {
    // 一条分块请求 = 一条真实连接，整段（建连 + 读完响应体）纳入全局连接预算
    connectionPermits.withPermit {
        downloadChunkInternal(
            taskId, url, outputFile, index, start, end,
            totalBytes, chunkDownloaded, callRef, io
        )
    }
}

private suspend fun DownloadManager.downloadChunkInternal(
    taskId: Int,
    url: String,
    outputFile: File,
    index: Int,
    start: Long,
    end: Long,
    totalBytes: Long,
    chunkDownloaded: AtomicLongArray,
    callRef: AtomicReference<Call?>,
    io: ChunkIoStats
) {
    val chunkLength = end - start + 1
    // 本块可能已被前一次尝试写入过一部分（被驱逐 / 瞬断后重新入队），
    // 从「块内已有字节数」处续传，避免整块从头重来。
    val alreadyWritten = chunkDownloaded.get(index).coerceIn(0L, chunkLength)
    if (alreadyWritten >= chunkLength) return   // 防御：本块已完整
    val from = start + alreadyWritten
    val call = client.newCall(
        Request.Builder().url(url)
            .header("User-Agent", DOWNLOAD_UA)
            .header(VideoAntiHotlink.REFERER_HEADER, VideoAntiHotlink.referer)
            .header("Range", "bytes=$from-$end")
            .get().build()
    )
    trackCall(taskId, call)
    callRef.set(call)   // 登记当前连接，供监控器精准驱逐单个慢块
    try {
        val resp = call.execute()
        resp.use { r ->
            // 仅当服务器「返回 200 忽略 Range」时抛可降级标记异常（上层回退单连接）；
            // 其它非 206（403/416/5xx 等）视为真实错误，保持原有重试/失败语义。
            if (r.code == 200) {
                throw RangeNotSupportedException("分块 $index 服务器忽略 Range(HTTP 200)，回退单连接下载")
            } else if (r.code != 206) {
                throw IOException("分块 $index 不支持 Range(HTTP ${r.code})，无法安全分块下载")
            }
            // 核对响应区间与请求区间、总量与本地记录是否一致
            verifyContentRange(r.header("Content-Range"), index, from, end, totalBytes)
            // 诊断：首次响应打印协议与服务器标识 —— 用于确认 HTTP/1.1 已生效并识别中间的 CDN 层。
            if (io.protocolLogged.compareAndSet(false, true)) {
                val serverHeader = r.header("Server")
                val rangesHeader = r.header("Accept-Ranges")
                AppLogger.log(
                    "DownloadManager",
                    "诊断: 分块响应 协议=${r.protocol} Server=$serverHeader " +
                        "Accept-Ranges=$rangesHeader 首块长度=$chunkLength 总量=$totalBytes"
                )
            }
            val input = r.body.byteStream()
            input.use { `in` ->
                val expected = chunkLength
                var written = alreadyWritten
                RandomAccessFile(outputFile, "rw").use { raf ->
                    raf.seek(from)
                    val buffer = ByteArray(CHUNK_PATH_BUFFER_SIZE)
                    while (true) {
                        if (currentCoroutineContext()[Job]?.isActive != true) break
                        // 诊断埋点：分别累计「等网络」与「写磁盘」的耗时，用于判别瓶颈位置
                        val readStartedAt = System.nanoTime()
                        val bytesRead = `in`.read(buffer)
                        io.readNanos.addAndGet(System.nanoTime() - readStartedAt)
                        if (bytesRead == -1) break
                        io.readBytes.addAndGet(bytesRead.toLong())
                        // 直接写入会越过 end 覆盖「下一个分块」的区域，造成跨块数据错乱。
                        val toWrite = minOf(bytesRead.toLong(), expected - written).toInt()
                        if (toWrite > 0) {
                            val writeStartedAt = System.nanoTime()
                            raf.write(buffer, 0, toWrite)
                            val writeNanos = System.nanoTime() - writeStartedAt
                            io.writeNanos.addAndGet(writeNanos)
                            io.writeBytes.addAndGet(toWrite.toLong())
                            if (writeNanos >= SLOW_WRITE_WARN_MS * 1_000_000L) {
                                io.slowWrites.incrementAndGet()
                            }
                            written += toWrite
                            chunkDownloaded.addAndGet(index, toWrite.toLong())
                        }
                        if (written >= expected) break
                    }
                }
                if (written != expected) {
                    throw IOException("分块 $index 写入长度不符：期望 $expected，实际 $written（连接中途断流或被中断）")
                }
            }
        }
    } finally {
        callRef.set(null)          // 连接结束后清空引用（成功/失败/被驱逐）
        untrackCall(taskId, call)  // 连接已结束，移出在途列表，避免无界增长
    }
}

/**
 * 校验 206 响应的 Content-Range。
 *
 * - 起点必须等于本次请求的起点：中间层/源站可能返回 206 却是别的区间，数据会被写到错误偏移，
 *   而收尾只校验总长度，会静默产出「能播但花屏/跳帧」的文件；
 * - 总量必须与本地持久化的 totalBytes 一致：不一致说明源站文件已变，继续按旧位图表续传会拼接
 *   新旧数据，须抛 [SourceChangedException] 交由上层整份重下。
 */
private fun verifyContentRange(
    header: String?,
    index: Int,
    expectedStart: Long,
    expectedEnd: Long,
    totalBytes: Long
) {
    if (header.isNullOrBlank()) {
        // RFC 9110 要求 206 必须带 Content-Range；缺失时无法确认对齐，宁可失败重试
        throw IOException("分块 $index 的 206 响应缺少 Content-Range，无法确认数据区间")
    }
    val m = CONTENT_RANGE_REGEX.find(header)
        ?: throw IOException("分块 $index 的 Content-Range 无法解析: $header")
    val respStart = m.groupValues[1].toLongOrNull() ?: -1L
    val respEnd = m.groupValues[2].toLongOrNull() ?: -1L
    val respTotal = m.groupValues[3].toLongOrNull() ?: 0L
    if (respStart != expectedStart) {
        throw IOException("分块 $index 响应区间起点不符：请求 $expectedStart，实际 $respStart（$header）")
    }
    if (respEnd in 0 until expectedEnd) {
        throw IOException("分块 $index 响应区间过短：期望至 $expectedEnd，实际至 $respEnd（$header）")
    }
    if (respTotal > 0 && totalBytes > 0 && respTotal != totalBytes) {
        throw SourceChangedException("源文件大小已变化：本地 $totalBytes，服务端 $respTotal（$header）")
    }
}

/**
 * 根据网络类型 + 实测单连接吞吐 + 全局连接预算，决定并行连接数（块数由 [CHUNK_SIZE] 单独推导）。
 * - Wi-Fi 基准 8、移动 4、其它 2；
 * - 单连接已高速 → 降到 ≤3，省握手/调度开销；单连接低速 → 保持较多连接以提速；
 * - 受全局软预算、[MAX_PARALLEL_CONNECTIONS] 与块数约束。
 */
private fun DownloadManager.computeParallelism(netClass: NetworkClass, singleBps: Long, chunkCount: Int): Int {
    var base = when (netClass) {
        NetworkClass.WIFI -> 8
        NetworkClass.CELLULAR -> 4
        NetworkClass.OTHER -> 2
    }
    if (singleBps >= HIGH_SINGLE_BPS) {
        base = minOf(base, 3)
    } else if (singleBps in 1..LOW_SINGLE_BPS) {
        base = minOf(maxOf(base, 4), MAX_PARALLEL_CONNECTIONS)
    }
    return minOf(base, effectiveParallelismCap(), MAX_PARALLEL_CONNECTIONS, chunkCount)
        .coerceAtLeast(1)
}
