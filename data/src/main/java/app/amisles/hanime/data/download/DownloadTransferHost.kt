package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadTask
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * 下载引擎（分块 / 单连接）所需的宿主能力。
 *
 * 引擎只依赖这组窄接口：连接、全局连接预算、任务状态读写与取消标记。
 * 状态存储、持久化与通知策略留在管理器侧，避免引擎反向持有整个下载子系统。
 */
internal interface DownloadTransferHost {

    val client: OkHttpClient
    val connectionPermits: Semaphore
    val referer: String

    /** 本进程内已判定「分块无收益」的文件（按 filePath）。 */
    val singleConnectionFiles: MutableSet<String>

    fun currentNetworkClass(): NetworkClass
    fun knownTotalBytes(taskId: Int): Long
    fun effectiveParallelismCap(): Int
    fun markChunkedFile(taskId: Int)
    fun clearChunkedMark(taskId: Int)
    fun updateTask(taskId: Int, updater: (DownloadTask) -> DownloadTask)
    fun trackCall(taskId: Int, call: Call)
    fun untrackCall(taskId: Int, call: Call)
    fun isTaskCancelled(taskId: Int): Boolean
}

/**
 * 下载引擎宿主：持有连接池与全局连接上限，并把状态读写委托给任务存储与更新器。
 *
 * 下载全部走同步 call.execute()，OkHttp Dispatcher 的 maxRequestsPerHost 只对异步调用生效，
 * 真正的全局上限由 [connectionPermits] 承担。
 */
internal class DefaultDownloadTransferHost(
    context: android.content.Context,
    private val store: DownloadTaskStore,
    private val calls: DownloadCallTracker,
    private val updater: DownloadTaskUpdater,
    private val resolver: DownloadResumeResolver,
    private val budget: DownloadConcurrencyBudget,
    private val antiHotlink: app.amisles.hanime.data.remote.VideoAntiHotlinkProvider
) : DownloadTransferHost {

    private val appContext = context.applicationContext ?: context

    override val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .connectionPool(
            ConnectionPool(
                maxIdleConnections = MAX_IDLE_CONNECTIONS,
                keepAliveDuration = KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS
            )
        )
        // 分块 Range 下载强制 HTTP/1.1：h2 下同一主机的多条流共用一条 TCP 连接，一次丢包会让所有
        // 分块同时归零（队头阻塞），且部分 CDN 限制单连接并发流数
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    override val connectionPermits = Semaphore(MAX_CONCURRENT_CONNECTIONS)

    override val referer: String get() = antiHotlink.referer

    override val singleConnectionFiles: MutableSet<String> = resolver.singleConnectionFiles

    override fun currentNetworkClass(): NetworkClass = getCurrentNetworkClass(appContext)

    override fun knownTotalBytes(taskId: Int): Long = store.tasksById[taskId]?.totalBytes ?: 0L

    override fun effectiveParallelismCap(): Int = budget.effectiveParallelismCap()

    override fun markChunkedFile(taskId: Int) = resolver.markChunked(taskId)

    override fun clearChunkedMark(taskId: Int) = resolver.clearChunked(taskId)

    override fun updateTask(taskId: Int, updater: (DownloadTask) -> DownloadTask) =
        this.updater.update(taskId, updater)

    override fun trackCall(taskId: Int, call: Call) = calls.track(taskId, call)

    override fun untrackCall(taskId: Int, call: Call) = calls.untrack(taskId, call)

    override fun isTaskCancelled(taskId: Int): Boolean = calls.isCancelled(taskId)

    /**
     * 按续传计划选择下载路径：已判定分块无收益、有分块位图、有单连接前缀，或首下探测后分块。
     */
    suspend fun download(
        taskId: Int,
        url: String,
        filePath: String,
        plan: ResumePlan
    ) {
        // 权威判定：该文件在本进程内已被判定分块无收益时，一律走单连接。续传计划给出的是
        // 连续完成前缀作为续传起点，这里兜住前缀为 0 的边界，否则会绕过记忆重新走分块。
        if (singleConnectionFiles.contains(filePath)) {
            downloadFileSingle(taskId, url, filePath, plan.bytes)
            return
        }
        if (plan.chunkMap != null) {
            val total = knownTotalBytes(taskId)
            if (total > 0) {
                downloadFileChunked(
                    taskId,
                    url,
                    filePath,
                    total,
                    resumeMap = plan.chunkMap,
                    resumePartial = plan.partial
                )
                return
            }
        }
        // 续传（恢复/失败重试）场景：采用单连接 Range 续传，稳定优先
        if (plan.bytes > 0L) {
            downloadFileSingle(taskId, url, filePath, plan.bytes)
            return
        }
        // 已知总量时先做一次磁盘空间预检：空间不足立即失败，避免把 ENOSPC 当成可重试的网络
        // 错误反复退避重试（每次重试都会再失败），让用户以为在下载却毫无进展。
        val knownTotal = knownTotalBytes(taskId)
        if (knownTotal > 0) requireFreeSpace(File(filePath), knownTotal)
        val probe = probeSupportAndThroughput(taskId, url)
        if (probe == null || !probe.supportsRange) {
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }
        if (probe.totalBytes < MIN_CHUNK_TOTAL_BYTES) {
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }
        // 探测拿到了准确总量，写第一个字节前再确认一次空间
        requireFreeSpace(File(filePath), probe.totalBytes)
        downloadFileChunked(
            taskId,
            url,
            filePath,
            probe.totalBytes,
            singleBps = probe.bps,
            resumeMap = null
        )
    }
}
