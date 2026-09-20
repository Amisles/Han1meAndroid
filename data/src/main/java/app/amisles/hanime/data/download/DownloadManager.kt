package app.amisles.hanime.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.remote.VideoAntiHotlink
import app.amisles.hanime.domain.model.DownloadEntity
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.ConnectionPool
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Call
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import android.content.Intent
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private data class ProgressUpdate(
    val taskId: Int,
    val title: String,
    val progress: Int,
    val status: DownloadStatus
)

// 分块请求服务器返回 200（忽略 Range）时抛出的标记异常。
private class RangeNotSupportedException(message: String) : IOException(message)

// M2：源文件内容/大小已变化（Content-Range 总量与本地持久化的 totalBytes 不符）。
// 续传会把旧前缀与新后缀拼在一起，必须由上层丢弃旧进度整份重下。
private class SourceChangedException(message: String) : IOException(message)

// Content-Range: bytes <start>-<end>/<total|*>
private val CONTENT_RANGE_REGEX = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)")

// 用于自适应分块数决策
private enum class NetworkClass { WIFI, CELLULAR, OTHER }

private fun getCurrentNetworkClass(context: Context): NetworkClass {
    // minSdk 为 API 30，可直接使用 NetworkCapabilities（API 21+），无需已废弃的 ConnectivityManager.TYPE_* 与 activeNetworkInfo
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetworkClass.OTHER
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkClass.OTHER
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> NetworkClass.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkClass.CELLULAR
        else -> NetworkClass.OTHER
    }
}

//首下探测结果
private data class ProbeResult(val supportsRange: Boolean, val totalBytes: Long, val bps: Long)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {

    // 解锁 OkHttp 单主机并发上限，显式配置 16 + 匹配的连接池，并优先 HTTP/2 多路复用。
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .connectionPool(ConnectionPool(maxIdleConnections = MAX_IDLE_CONNECTIONS, keepAliveDuration = KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    // 以 Map 作为任务权威存储，提供 O(1) 按 id 查找，
    private val taskMap = ConcurrentHashMap<Int, DownloadTask>()

    // taskMap 派生 UI 列表快照
    private fun emitTasks() {
        _tasks.value = taskMap.values.sortedByDescending { it.id }
    }

    // 避免 updateTask 每次 500ms 进度刷新都从 taskMap 全量重建列表。结构变化（增删/状态切换）仍走 emitTasks()。
    private fun updateTaskInList(newTask: DownloadTask) {
        val cur = _tasks.value
        val idx = cur.indexOfFirst { it.id == newTask.id }
        if (idx < 0) { emitTasks(); return }
        if (cur[idx] === newTask) return
        _tasks.value = cur.toMutableList().also { it[idx] = newTask }
    }

    var onProgressUpdate: ((taskId: Int, title: String, progress: Int, status: DownloadStatus) -> Unit)? = null

    private val downloadJobs = ConcurrentHashMap<Int, Job>()
    private val taskCalls = ConcurrentHashMap<Int, MutableList<Call>>()
    private val taskCancelFlags = ConcurrentHashMap<Int, AtomicBoolean>()
    private val taskIdCounter = AtomicInteger(0)
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val tasksLock = ReentrantLock()
    @Volatile
    private var concurrencyLimit: Int = Preferences.maxDownloadConcurrent.coerceIn(1, MAX_CONCURRENT)
    private val lastPersistTime = ConcurrentHashMap<Int, Long>()
    private val activeSlots = AtomicInteger(0)
    private val downloadServiceStarted = AtomicBoolean(false)
    // M4：各任务最近一次已转发的整数百分比，用于抑制「百分比未变」的重复跨进程通知
    private val lastNotifiedPercent = ConcurrentHashMap<Int, Int>()

    /**
     * 落盘串行化队列（修复 S10）。
     *
     * 原实现用 `scope.launch { dao.upsert(...) }` 即发即忘，多个写任务在 Dispatchers.IO 上
     * 并发执行且**不保证完成顺序**。短时间内连续状态切换时（下载中→失败→立即重试→下载中），
     * 先发起的 UPSERT 可能后落库，把更新的状态覆盖回旧值；`cancelDownload` 的 DELETE
     * 与残留的 UPSERT 竞争时，已删除的任务会「复活」。
     *
     * 改为「单消费者 Channel」：Channel 严格 FIFO，提交顺序即写库顺序；
     * 而所有提交点都在 tasksLock 内（见 updateTask），提交顺序与逻辑更新顺序一致。
     * UNLIMITED 容量下 trySend 不会挂起或丢弃。
     */
    private sealed interface PersistCmd {
        data class Upsert(val task: DownloadTask) : PersistCmd
        data class Delete(val taskId: Int) : PersistCmd
    }

    private val persistChannel = Channel<PersistCmd>(Channel.UNLIMITED)
    private val defaultDownloadDir: File by lazy {

        val base = context.getExternalFilesDir(null)
            ?.let { File(it, "Downloads") }
            ?: File(context.filesDir, "Downloads")
        if (!base.exists()) base.mkdirs()
        base
    }

    /**
     * 解析实际下载目录：空自定义路径 → 默认目录；非空自定义路径需可写，否则回退默认。
     * 每次新建下载时实时读取 Preferences，使「设置页变更存储路径」对后续新下载立即生效；
     * 既有任务（filePath 已固化）不受影响。
     */
    private fun resolveDownloadDir(): File {
        val custom = Preferences.downloadStoragePath
        if (custom.isNotBlank()) {
            val dir = File(custom)
            if (ensureDirWritable(dir)) return dir
            AppLogger.log("DownloadManager", "自定义存储路径不可写，回退默认目录: $custom")
        }
        return defaultDownloadDir
    }

    /**
     * 按 UTF-8 字节预算截断文件名主体。
     *
     * ext4 等文件系统单个文件名上限 255 字节，而中文标题按每字 3 字节极易越界；
     * 越界时 createNewFile 抛 ENAMETOOLONG，任务会以一个很难懂的 IO 异常失败。
     * 尾部的 `_<videoId>.mp4` 需完整保留（它是文件身份的一部分），故预算从尾部反推。
     */
    private fun clampFileNameBase(baseName: String, uniqueSuffix: String): String {
        val tailBytes = "_$uniqueSuffix.mp4".toByteArray(Charsets.UTF_8).size
        val budget = (MAX_FILE_NAME_BYTES - tailBytes).coerceAtLeast(16)
        if (baseName.toByteArray(Charsets.UTF_8).size <= budget) return baseName
        val sb = StringBuilder()
        var used = 0
        for (ch in baseName) {
            val len = ch.toString().toByteArray(Charsets.UTF_8).size
            if (used + len > budget) break
            sb.append(ch)
            used += len
        }
        return sb.toString()
    }

    private fun ensureDirWritable(dir: File): Boolean {
        return runCatching {
            if (!dir.exists()) dir.mkdirs()
            dir.exists() && dir.isDirectory && dir.canWrite()
        }.getOrDefault(false)
    }

    init {
        onProgressUpdate = { taskId, title, progress, status ->
            forwardToService(context, title, progress, status, taskId)
        }

        scope.launch {
            for (cmd in persistChannel) {
                runCatching {
                    when (cmd) {
                        is PersistCmd.Upsert -> downloadDao.upsertDownload(cmd.task.toEntity())
                        is PersistCmd.Delete -> downloadDao.deleteDownload(cmd.taskId)
                    }
                }.onFailure { e ->
                    AppLogger.logError("DownloadManager", "Failed to persist $cmd: ${e.message}", e)
                }
            }
        }

        scope.launch {
            Preferences.maxDownloadConcurrentFlow.collect { updateConcurrencyLimit(it) }
        }

        scope.launch {
            try {
                val entities = downloadDao.getAllDownloadsOnce()
                val restoredTasks = entities.map { entity ->
                    val status = if (entity.status == DownloadStatus.DOWNLOADING.name) {
                        DownloadStatus.PAUSED
                    } else {
                        runCatching { DownloadStatus.valueOf(entity.status) }.getOrDefault(DownloadStatus.FAILED)
                    }
                    DownloadTask(
                        id = entity.id,
                        title = entity.title,
                        quality = entity.quality,
                        url = entity.url,
                        totalBytes = entity.totalBytes,
                        downloadedBytes = entity.downloadedBytes,
                        status = status,
                        filePath = entity.filePath,
                        thumbnailUrl = entity.thumbnailUrl,
                        videoId = entity.videoId,
                        errorMessage = entity.errorMessage
                    )
                }
                // M6：DB 里的 COMPLETED 不代表文件还在（用户清理下载目录 / 卸载重装 / 存储卡移除）。
                // 不校验会让「已完成」条目点播放直接黑屏，且 isVideoDownloaded 会禁止重新下载，形成死角。
                val verifiedTasks = restoredTasks.map { task ->
                    if (task.status != DownloadStatus.COMPLETED) {
                        task
                    } else {
                        val f = File(task.filePath)
                        if (f.exists() && f.length() > 0) {
                            task
                        } else {
                            AppLogger.log(
                                "DownloadManager",
                                "已完成任务的本地文件缺失，标记为失败: ${task.title} (${task.filePath})"
                            )
                            task.copy(status = DownloadStatus.FAILED, errorMessage = "本地文件已被删除或移动")
                        }
                    }
                }
                tasksLock.withLock {
                    taskMap.clear()
                    verifiedTasks.forEach { taskMap[it.id] = it }
                    emitTasks()
                }
                // 同步落库：getCompletedDownloadCount() 等接口按库统计，不回写会与内存态长期不一致
                verifiedTasks.zip(restoredTasks)
                    .filter { (verified, original) -> verified.status != original.status }
                    .forEach { (verified, _) -> persistTask(verified) }
                taskIdCounter.set(downloadDao.getMaxId() ?: 0)
                AppLogger.log("DownloadManager", "Restored ${restoredTasks.size} tasks from DB, taskIdCounter=$taskIdCounter")
                val interruptedIds = entities.filter {
                    it.status == DownloadStatus.DOWNLOADING.name
                }.map { it.id }
                tasksLock.withLock {
                    interruptedIds.forEach { resumeDownloadInternal(it) }
                }
                // 避免进程重启后它们永久挂起、只能手动恢复。
                startNextPendingTask()
            } catch (e: SQLiteException) {
                AppLogger.logError("DownloadManager", "Failed to restore tasks: ${e.message}", e)
            }
        }
    }

    /**
     * 新建下载任务。
     *
     * @return 成功时为任务 id；[RESULT_INVALID_URL] 直链非法或路径越界；
     *         [RESULT_ALREADY_ACTIVE] 同一文件已有进行中/等待中的任务（未新建）；
     *         [RESULT_ALREADY_COMPLETED] 同一文件已下载完成（未新建）。
     */
    fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String = "",
        videoId: String = ""
    ): Int {
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            AppLogger.logError("DownloadManager", "拒绝下载：非法 url=\"$url\" (title=$title, videoId=$videoId)")
            return RESULT_INVALID_URL
        }
        val dir = resolveDownloadDir()
        val uniqueSuffix = if (videoId.isNotBlank()) {
            videoId.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        } else {
            url.hashCode().toString(36)
        }
        val baseName = "${title}_$quality".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${clampFileNameBase(baseName, uniqueSuffix)}_$uniqueSuffix.mp4"
        val targetFile = File(dir, fileName)
        val canonical = runCatching { targetFile.canonicalPath }.getOrDefault(targetFile.absolutePath)
        val dirCanonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!canonical.startsWith(dirCanonical + File.separator) && canonical != dirCanonical) {
            AppLogger.logError("DownloadManager", "拒绝下载：路径越界 fileName=\"$fileName\"")
            return RESULT_INVALID_URL
        }
        val filePath = targetFile.absolutePath

        tasksLock.withLock {
            // S3：去重必须按「文件身份」而非 (url, quality)。downloadUrl 是带时效签名的 CDN 直链，
            // 重新拉取画质页会得到不同 url；而落盘路径由 (title, quality, videoId) 决定 —— 两者不一致时
            // 会并存两个 filePath 完全相同的任务，两条链路的 RandomAccessFile 在重叠区间并发写同一文件，
            // 且删除任一条都会把共享文件删掉。
            val existingTask = taskMap.values.find { it.filePath == filePath }
            if (existingTask != null) {
                AppLogger.log("DownloadManager", "该文件已有下载任务，复用 taskId=${existingTask.id}: $title ($quality)")
                return when (existingTask.status) {
                    // 暂停/失败：复用即续传（保持「点下载 = 继续」的既有语义）
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                        resumeDownloadInternal(existingTask.id)
                        existingTask.id
                    }
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> RESULT_ALREADY_ACTIVE
                    DownloadStatus.COMPLETED -> RESULT_ALREADY_COMPLETED
                }
            }

            val taskId = taskIdCounter.incrementAndGet()

            val task = DownloadTask(
                id = taskId,
                title = title,
                quality = quality,
                url = url,
                filePath = filePath,
                status = DownloadStatus.PENDING,
                thumbnailUrl = thumbnailUrl,
                videoId = videoId
            )

            taskMap[taskId] = task
            emitTasks()
            persistTask(task)
            AppLogger.log("DownloadManager", "Starting download $taskId: $title ($quality)")

            startDownloadWithConcurrencyInternal(task)

            return taskId
        }
    }

    /**
     * P2-1：以 CAS 原子获取「软并发槽位」，将「判定 + 占位」合为一个原子操作，
     * 杜绝原 check-then-act（先 get 再 incrementAndGet）在并发/批量启动时突破并发上限。
     */
    private fun tryAcquireSoftSlot(): Boolean {
        while (true) {
            val cur = activeSlots.get()
            // concurrencyLimit 为 @Volatile，每轮重读以立即响应 updateConcurrencyLimit
            if (cur >= concurrencyLimit) return false
            if (activeSlots.compareAndSet(cur, cur + 1)) return true
        }
    }

    /**
     * P2-1：从磁盘残留推导续传起点，返回 (单连接续传字节数, 分块续传位图)。
     * 槽位满时任务会退回 PENDING 稍后启动，届时须重新推导，否则以「首下」语义整文件重下；
     * resumeDownloadInternal 也复用此函数，保证两条路径判定一致。
     */
    private fun deriveResumeState(task: DownloadTask): Pair<Long, BooleanArray?> {
        val file = File(task.filePath)
        val partmapFile = File(task.filePath + PARTMAP_SUFFIX)
        if (partmapFile.exists() && file.exists()) {
            val (cc, bitmap) = readPartmap(partmapFile)
            if (cc > 0 && bitmap != null) {
                // S4：位图表只是「上次写入时的意图记录」，与文件实际长度可能不一致
                // （文件被外部截断/替换、存储卸载重挂、上次写入未落盘即崩溃）。
                // 只有「该块区间末尾字节确实落在当前文件长度内」才承认它完成，否则清位重下 ——
                // 否则会出现「位图说完成、数据不在」→ 收尾校验恒失败 → 永久 FAILED 且无法自愈。
                // 注意：全部完成且长度达标的位图表在此同样返回（不再删文件重下），由分块流程直接收尾。
                return 0L to sanitizePartmap(task, file, cc, bitmap)
            }
        }
        // 回退：文件级续传（单连接）或整文件重下
        if (partmapFile.exists()) {
            runCatching { partmapFile.delete() }
            if (file.exists()) runCatching { file.delete() }
            return 0L to null
        }

        val fileLen = if (file.exists()) file.length() else 0L
        val totalKnown = task.totalBytes > 0
        val safeResume = fileLen > 0
                && fileLen >= task.downloadedBytes.coerceAtLeast(0L)
                && (!totalKnown || fileLen < task.totalBytes)
        if (safeResume) return fileLen to null
        if (file.exists()) runCatching { file.delete() }
        return 0L to null
    }

    /**
     * S4：把位图表与实际文件长度对齐。位图中标记完成、但其区间末尾字节尚未落盘的块一律清位重下。
     * totalBytes 未知（<=0）时无法推导块边界，保持原样（此时收尾的完整性校验也会跳过）。
     */
    private fun sanitizePartmap(task: DownloadTask, file: File, chunkCount: Int, bitmap: BooleanArray): BooleanArray {
        val total = task.totalBytes
        if (total <= 0) return bitmap
        val fileLen = runCatching { file.length() }.getOrDefault(0L)
        var cleared = 0
        val sanitized = BooleanArray(chunkCount) { i ->
            val ok = bitmap[i] && chunkEndExclusive(i, chunkCount, total) <= fileLen
            if (!ok && bitmap[i]) cleared++
            ok
        }
        if (cleared > 0) {
            AppLogger.log(
                "DownloadManager",
                "续传一致性校验：$cleared/$chunkCount 个已完成块的数据不在磁盘上" +
                    "(fileLen=$fileLen, total=$total)，已清位重下"
            )
        }
        return sanitized
    }

    /** 第 index 块的「区间末尾 + 1」，即该块完成时文件长度应达到的下界。 */
    private fun chunkEndExclusive(index: Int, chunkCount: Int, totalBytes: Long): Long {
        if (chunkCount <= 0) return 0L
        return if (index == chunkCount - 1) totalBytes else (index + 1) * (totalBytes / chunkCount)
    }

    /**
     * 内部方法，调用者须持有 tasksLock。统一负责 activeSlots 的 CAS 占位与终态/取消时的 -1，
     * 结束后调度下一个 PENDING 任务，修复 B1/B3/B4 并发门控失效。
     */
    private fun startDownloadWithConcurrencyInternal(task: DownloadTask, resumeBytes: Long = 0L, resumeChunkMap: BooleanArray? = null) {
        if (downloadJobs.containsKey(task.id)) return
        // 占不到槽位则退回 PENDING，由 finally 的链式调度重新推导续传起点后拾起
        if (!tryAcquireSoftSlot()) {
            if (task.status != DownloadStatus.PENDING) {
                updateTask(task.id) { it.copy(status = DownloadStatus.PENDING) }
            }
            AppLogger.log("DownloadManager", "下载槽位已满($concurrencyLimit)，任务 ${task.id} 进入等待队列")
            return
        }
        taskCancelFlags.remove(task.id)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                AppLogger.log("DownloadManager", "开始下载: ${task.title} (并发槽位已获取, resumeFrom=$resumeBytes)")

                updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
                try {
                    downloadFile(task.id, task.url, task.filePath, resumeBytes, resumeChunkMap)
                } catch (e: SourceChangedException) {
                    // M2：源文件已变，旧位图表/旧文件已无意义 —— 丢弃后整份重下。
                    // 首下（无续传起点）时直接上抛交给失败处理，不做无谓的重试。
                    if (resumeBytes == 0L && resumeChunkMap == null) throw e
                    AppLogger.logError(
                        "DownloadManager",
                        "源文件已变化，丢弃旧进度后重新下载: ${task.title}, ${e.message}",
                        e
                    )
                    runCatching { File(task.filePath).takeIf { f -> f.exists() }?.delete() }
                    runCatching { File(task.filePath + PARTMAP_SUFFIX).delete() }
                    updateTask(task.id) { it.copy(downloadedBytes = 0, totalBytes = 0, errorMessage = "") }
                    downloadFile(task.id, task.url, task.filePath, 0L, null)
                }

                if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(task.id)) {
                    AppLogger.log("DownloadManager", "下载被取消，保留暂停状态: ${task.title}")
                    return@launch
                }

                updateTask(task.id) { t ->
                    val onDisk = runCatching { File(t.filePath).length() }.getOrDefault(0L)
                    if (t.totalBytes > 0 && (t.downloadedBytes < t.totalBytes || onDisk < t.totalBytes)) {
                        AppLogger.logError(
                            "DownloadManager",
                            "完整性校验失败 ${task.title}: ${t.downloadedBytes}/${t.totalBytes} (磁盘 $onDisk)"
                        )
                        t.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = "下载不完整（${t.downloadedBytes}/${t.totalBytes} 字节）"
                        )
                    } else {
                        t.copy(status = DownloadStatus.COMPLETED)
                    }
                }
                AppLogger.log("DownloadManager", "下载完成: ${task.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(task.id)) {
                    AppLogger.log("DownloadManager", "下载被取消（IO 中断），保留暂停状态: ${task.title}")
                    return@launch
                }
                AppLogger.logError("DownloadManager", "下载失败 ${task.title}: ${e.message}", e)
                updateTask(task.id) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyError(e)) }
            } finally {
                // 释放槽位并调度下一个等待任务（成功/失败/取消均执行）
                currentCoroutineContext()[Job]?.let { self -> downloadJobs.remove(task.id, self) }
                if (!downloadJobs.containsKey(task.id)) {
                    taskCalls.remove(task.id)
                    taskCancelFlags.remove(task.id)
                }
                activeSlots.decrementAndGet()
                startNextPendingTask()
            }
        }

        downloadJobs[task.id] = job
        job.start()
    }

    private fun startNextPendingTask() {
        tasksLock.withLock {
            val pendingTask = taskMap.values.firstOrNull {
                it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id)
            }
            // activeSlots 判定仅作快速失败的启发式；真正的并发正确性由 tryAcquireSoftSlot 的 CAS 保证
            if (pendingTask != null && activeSlots.get() < concurrencyLimit) {
                AppLogger.log("DownloadManager", "启动下一个等待任务: ${pendingTask.title}")
                val (rb, rm) = deriveResumeState(pendingTask)
                startDownloadWithConcurrencyInternal(pendingTask, rb, rm)
            }
        }
    }

    fun updateConcurrencyLimit(maxConcurrent: Int) {
        val safeMax = maxConcurrent.coerceIn(1, MAX_CONCURRENT)
        // 并发门控只有 activeSlots + concurrencyLimit 一套（O1：冗余的信号量已移除）
        concurrencyLimit = safeMax
        AppLogger.log("DownloadManager", "并发下载数已更新为: $safeMax")

        tasksLock.withLock {
            val pendingTasks = taskMap.values.filter {
                it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id)
            }
            pendingTasks.forEach { task ->
                if (activeSlots.get() < concurrencyLimit) {
                    // P2-1：启动前重新推导续传起点
                    val (rb, rm) = deriveResumeState(task)
                    startDownloadWithConcurrencyInternal(task, rb, rm)
                }
            }
        }
    }

    private suspend fun downloadFile(
        taskId: Int,
        url: String,
        filePath: String,
        resumeBytes: Long = 0L,
        resumeChunkMap: BooleanArray? = null
    ) {
        if (resumeChunkMap != null) {
            val total = taskMap[taskId]?.totalBytes ?: 0L
            if (total > 0) {
                downloadFileChunked(taskId, url, filePath, total, resumeMap = resumeChunkMap)
                return
            }
        }
        // 续传（恢复/失败重试）场景：采用单连接 Range 续传，稳定优先
        if (resumeBytes > 0L) {
            downloadFileSingle(taskId, url, filePath, resumeBytes)
            return
        }
        val probe = probeSupportAndThroughput(taskId, url)
        if (probe == null || !probe.supportsRange) {
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }
        if (probe.totalBytes < MIN_CHUNK_TOTAL_BYTES) {
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }
        downloadFileChunked(taskId, url, filePath, probe.totalBytes, singleBps = probe.bps, resumeMap = null)
    }

    /**
     * O3：首下前发一个小窗口 Range 请求，同时完成两件事：
     * 1) 确认服务器支持 206 Range（不支持则上层降级单连接）；
     * 2) 测量单连接吞吐（字节/秒），作为自适应分块数的依据。
     * 探测窗口 [0, PROBE_WINDOW) 读后即弃（约 512KB，相对多 MB 视频可忽略；O9 指标闭环后可省）。
     */
    private suspend fun probeSupportAndThroughput(taskId: Int, url: String): ProbeResult? {
        // O8：改为显式 catch。此前整体包在 runCatching 里，任何挂起点上的取消都会被吞成 null，
        // 使「已取消的任务」继续走单连接重下；当前函数体内虽然没有挂起点，但那是脆弱写法。
        return try {
            val t0 = System.nanoTime()
            val call = client.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", DOWNLOAD_UA)
                    .header(VideoAntiHotlink.REFERER_HEADER, VideoAntiHotlink.referer)
                    .header("Range", "bytes=0-${PROBE_WINDOW - 1}")
                    .get().build()
            )
            trackCall(taskId, call) // 复用任务级 Call 跟踪，暂停时可由 cancelTaskCalls 中断
            val resp = call.execute()
            resp.use { r ->
                if (r.code != 206) {
                    null
                } else {
                    val total = r.header("Content-Range")
                        ?.let { Regex("/(\\d+)$").find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
                    val input = r.body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (read < PROBE_WINDOW) {
                        val n = input.read(buf)
                        if (n == -1) break
                        read += n
                    }
                    val dt = (System.nanoTime() - t0) / 1e9
                    val bps = if (dt > 0.05) (read / dt).toLong() else 0L
                    ProbeResult(supportsRange = true, totalBytes = total, bps = bps)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AppLogger.log("DownloadManager", "首下探测失败，降级单连接: ${e.message}")
            null
        }
    }

    /**
     * 单连接顺序下载（首下降级 / 续传 / 不支持分块路径）。
     * 复用原有稳定逻辑：支持 Range 续传、失败限次重试、每 500ms 节流更新进度。
     */
    private suspend fun downloadFileSingle(taskId: Int, url: String, filePath: String, resumeBytes: Long) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", DOWNLOAD_UA)
            .header(VideoAntiHotlink.REFERER_HEADER, VideoAntiHotlink.referer)
            .get()

        if (resumeBytes > 0) {
            requestBuilder.header("Range", "bytes=$resumeBytes-")
        }

        var response: Response? = null
        var attempt = 0
        val maxRetries = 1
        while (response == null && attempt <= maxRetries) {
            try {
                val call = client.newCall(requestBuilder.build())
                trackCall(taskId, call)
                response = call.execute()
            } catch (e: IOException) {
                if (isTaskCancelled(taskId)) return
                attempt++
                if (attempt <= maxRetries && currentCoroutineContext()[Job]?.isActive == true) {
                    AppLogger.log("DownloadManager", "下载请求失败，1s 后重试($attempt): ${e.message}")
                    delay(1000)
                } else {
                    updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyError(e)) }
                    return
                }
            }
        }

        response!!.use { resp ->
            if (resumeBytes > 0 && resp.code != 206 && !resp.isSuccessful) {
                updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = "HTTP ${resp.code}") }
                return
            }
            if (resumeBytes == 0L && !resp.isSuccessful) {
                updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyHttpError(resp.code)) }
                return
            }

            val body = resp.body
            val bodyLength = body.contentLength()

            if (resumeBytes > 0 && resp.code == 200) {
                AppLogger.log("DownloadManager", "服务器不支持 Range(返回 200)，降级为全量重下: $filePath")
            }

            val isPartial = resp.code == 206
            val persistedTotal = taskMap[taskId]?.totalBytes ?: 0L
            val totalFromRange = resp.header("Content-Range")?.let {
                Regex("/(\\d+)$").find(it)?.groupValues?.get(1)?.toLongOrNull()
            }
            // M2：断点续传时若服务端总量与本地记录不符，说明源文件已变 —— 旧前缀 + 新后缀会拼成坏文件，
            // 直接抛出让上层丢弃旧进度整份重下（仅在确有续传起点时才判定）。
            if (isPartial && resumeBytes > 0 && totalFromRange != null &&
                persistedTotal > 0 && totalFromRange != persistedTotal
            ) {
                throw SourceChangedException(
                    "源文件大小已变化：本地 $persistedTotal，服务端 $totalFromRange"
                )
            }
            val totalBytes = if (isPartial && resumeBytes > 0) {
                totalFromRange ?: (resumeBytes + bodyLength)
            } else {
                bodyLength
            }

            val inputStream = body.byteStream()
            val outputFile = File(filePath)
            // 运行期目录可能被清理/卸载重挂，补一次父目录兜底重建
            outputFile.parentFile?.mkdirs()
            val outputStream = if (resumeBytes > 0 && isPartial) {
                java.io.FileOutputStream(outputFile, true)
            } else {
                java.io.FileOutputStream(outputFile, false)
            }

            val buffer = ByteArray(chooseBufferSize(totalBytes))
            var downloadedBytes = if (isPartial) resumeBytes else 0L
            var lastUpdate = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (true) {
                        if (currentCoroutineContext()[Job]?.isActive != true) break
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            lastUpdate = now
                            updateTask(taskId) {
                                it.copy(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                    }
                }
            }

            updateTask(taskId) {
                it.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes
                )
            }
        }
    }

    /**
     * P2-2：计算单任务允许的分块数上限，使「并发任务数 × 每任务分块数」
     * 不超过全局连接数上限 [MAX_TOTAL_CONNECTIONS]，在保留任务内并行与限制总连接间取平衡。
     */
    private fun effectiveChunkCap(): Int {
        val active = maxOf(activeSlots.get(), 1)
        // O2：整数除法在任务数接近上限时会把每任务连接数压到 1（5 个任务 → 8/5=1 → chunkCount=1 →
        // 每个任务都退化为单连接），与「多线程下载」的预期完全相反。这里给每任务一个保底值；
        // 代价是最坏情况下总连接数略超软预算（5×2=10，仍低于 OkHttp maxRequestsPerHost=16），可接受。
        return maxOf(MIN_CHUNKS_PER_TASK, MAX_TOTAL_CONNECTIONS / active)
    }

    /**
     * 多线程分块并行下载：分块数首下按网络类型 + 实测吞吐自适应（见 [computeChunkCount]），续传沿用位图表；
     * 仅对未完成块发起请求（O4）；慢块驱逐（O5）对持续低吞吐块取消连接后由空闲 worker 以新连接重试；
     * 不再 setLength 预分配以避免空洞（O8）。
     */
    private suspend fun downloadFileChunked(
        taskId: Int,
        url: String,
        filePath: String,
        totalBytes: Long,
        singleBps: Long = 0L,
        resumeMap: BooleanArray? = null
    ) {
        val chunkCount = resumeMap?.size ?: computeChunkCount(getCurrentNetworkClass(context), singleBps, totalBytes)
        val outputFile = File(filePath)
        val partmapFile = File(filePath + PARTMAP_SUFFIX)
        // O7：不足 2 块无并行收益，退回单连接；同时清掉可能残留的位图表，避免留下孤儿文件
        if (chunkCount < 2) {
            if (partmapFile.exists()) runCatching { partmapFile.delete() }
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }

        val chunkDone = resumeMap ?: BooleanArray(chunkCount)
        if (resumeMap == null) {
            // 首下：清空旧文件与旧位图表，重新分块（O8：不再 setLength 预分配，避免空洞）
            if (outputFile.exists()) outputFile.delete()
            outputFile.parentFile?.mkdirs()
            outputFile.createNewFile()
            // M7：位图表是分块续传的唯一依据，首下写失败必须立刻放弃分块路径 —— 否则一旦崩溃/暂停，
            // deriveResumeState 会因「无位图表、但有文件」退化为「按文件长度做单连接续传」，
            // 而分块文件是带空洞的（RandomAccessFile.seek 稀疏写），最终会产出中间零填充的坏文件。
            if (!writePartmap(partmapFile, chunkCount, chunkDone)) {
                AppLogger.logError(
                    "DownloadManager",
                    "分块下载：位图表写入失败，本次改为单连接顺序下载（该文件不可续传）: $filePath",
                    null
                )
                runCatching { if (outputFile.exists()) outputFile.delete() }
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
        val queue = ArrayDeque<Int>().apply { for (i in 0 until chunkCount) if (!chunkDone[i]) addLast(i) }

        // P3-1：标记「服务器对分块请求返回 200 忽略 Range」，用于触发整任务回退单连接下载
        val rangeUnsupported = AtomicBoolean(false)
        // M2：标记「源文件已变化」。与 rangeUnsupported 同理，必须先记录再取消作用域 ——
        // 直接 throw 会被 coroutineScope 的取消路径吞成 CancellationException。
        val sourceChanged = AtomicReference<SourceChangedException?>(null)
        try {
            coroutineScope {
                val csJob = this.coroutineContext[Job]
                fun takeChunk(): Int = synchronized(queue) { if (queue.isNotEmpty()) queue.removeFirst() else -1 }
                fun requeueChunk(i: Int) = synchronized(queue) { queue.addLast(i) }

                val workerCount = chunkCount.coerceAtMost(MAX_REQUESTS_PER_HOST)
                val workers = (0 until workerCount).map { _ ->
                    launch(Dispatchers.IO) {
                        while (currentCoroutineContext()[Job]?.isActive == true && !isTaskCancelled(taskId)) {
                            val idx = takeChunk()
                            if (idx < 0) break
                            // 逐块采样基准必须以「本次连接即将开始」为起点：块被驱逐后可能在队列里等待很久，
                            // 若沿用等待期的计时，重新取到后第一次采样就会被判成「已持续慢 15s」而立刻驱逐。
                            // 同时把 lastBytes 对齐到当前进度，避免把「等待期前写入的字节」当成瞬时速率。
                            chunkLastBytes.set(idx, chunkDownloaded.get(idx))
                            chunkFirstObserved.set(idx, System.currentTimeMillis())
                            chunkSlowSince.set(idx, 0L)
                            val attempts = ++chunkAttempts[idx]
                            val start = idx * chunkSize
                            val end = if (idx == chunkCount - 1) totalBytes - 1 else start + chunkSize - 1
                            try {
                                downloadChunk(taskId, url, outputFile, idx, start, end, totalBytes, chunkDownloaded, chunkCallRefs[idx])
                                synchronized(lock) { chunkDone[idx] = true }
                                completedChunks.incrementAndGet()
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
                                if (attempts >= MAX_CHUNK_ATTEMPTS) {
                                    throw IOException("分块 $idx 重试 $attempts 次仍失败，终止下载", e)
                                }
                                // O3：**不再清零 chunkDownloaded / chunkLastBytes** —— 重试时 downloadChunk 会
                                // 从块内已有偏移续传；清零会让已下载字节被重复计数并丢失续传起点。
                                chunkFirstObserved.set(idx, 0L)
                                chunkSlowSince.set(idx, 0L)
                                AppLogger.log("DownloadManager", "分块 $idx 失败/被驱逐，重新入队以新连接续传(第${attempts}次): ${e.message}")
                                requeueChunk(idx)
                            }
                        }
                    }
                }

                // M3：慢块阈值不能是绝对常量。单连接实测吞吐会被均分到 chunkCount 条连接上，
                // 若仍按固定 50KB/s/块 判定，弱网（例如 300KB/s ÷ 8 块 ≈ 37KB/s/块）会把**每条**正常
                // 连接都判成慢块 → 驱逐 → 重试次数耗尽 → 整个任务失败，与自适应提速的初衷相反。
                // 改为「实测每块基准速率的 SLOW_RELATIVE_PERCENT%，下不低于 FLOOR、上不超过 CAP」；
                // 续传场景没有实测值时退化为下限（宁可少驱逐，也不要误杀正常连接）。
                val perChunkBaseline = if (singleBps > 0) singleBps / chunkCount else 0L
                val slowThresholdBps = maxOf(
                    SLOW_THRESHOLD_FLOOR_BPS,
                    minOf(perChunkBaseline * SLOW_RELATIVE_PERCENT / 100, SLOW_THRESHOLD_CAP_BPS)
                )
                AppLogger.log(
                    "DownloadManager",
                    "分块下载: chunkCount=$chunkCount, 单连接实测=${singleBps / 1024}KB/s, " +
                        "慢块阈值=${slowThresholdBps / 1024}KB/s"
                )

                // 慢块监控器。周期性采样逐块吞吐，对持续低于阈值的分块取消其连接、交 worker 重领
                // （重置慢速计时，重试用新连接）。仅在分块确有活跃连接时驱逐，避免误杀空闲/已完成块。
                val monitor = launch(Dispatchers.IO) {
                    var prevTime = System.currentTimeMillis()
                    while (completedChunks.get() < chunkCount &&
                        currentCoroutineContext()[Job]?.isActive == true &&
                        !isTaskCancelled(taskId)) {   // P2-2：撕销期停止采样
                        delay(SLOW_SAMPLE_MS)
                        val now = System.currentTimeMillis()
                        val dt = (now - prevTime) / 1000.0
                        prevTime = now
                        if (dt <= 0) continue
                        val doneSnapshot = synchronized(lock) { chunkDone.copyOf() }
                        for (i in 0 until chunkCount) {
                            if (doneSnapshot[i]) continue
                            val bytes = chunkDownloaded.get(i)
                            val rate = ((bytes - chunkLastBytes.get(i)) / dt).toLong()
                            chunkLastBytes.set(i, bytes)
                            if (chunkFirstObserved.get(i) == 0L) chunkFirstObserved.set(i, now)
                            val observedFor = now - chunkFirstObserved.get(i)
                            val call = chunkCallRefs[i].get()
                            if (rate < slowThresholdBps) {
                                // 起步宽限期：新块 0 字节阶段不判慢，避免误杀刚建立的连接
                                if (bytes == 0L && observedFor < SLOW_GRACE_MS) continue
                                val slowSince = chunkSlowSince.get(i)
                                if (slowSince == 0L) {
                                    chunkSlowSince.set(i, now)
                                } else if (now - slowSince >= SLOW_DURATION_MS && call != null) {
                                    AppLogger.log("DownloadManager", "慢块驱逐：chunk $i 速率 ${rate / 1024}KB/s 持续 ${(now - slowSince) / 1000}s，重分配连接")
                                    runCatching { call.cancel() }
                                    chunkSlowSince.set(i, 0L)
                                }
                            } else {
                                chunkSlowSince.set(i, 0L)
                            }
                        }
                    }
                }

                // 进度上报 + 位图表节流落盘
                val reporter = launch(Dispatchers.IO) {
                    var lastBitmapPersist = 0L
                    var lastReportedSum = -1L
                    while (completedChunks.get() < chunkCount &&
                        currentCoroutineContext()[Job]?.isActive == true &&
                        !isTaskCancelled(taskId)) {
                        delay(500)
                        var sum = 0L
                        for (i in 0 until chunkCount) sum += chunkDownloaded.get(i)
                        // O6：字节数没有变化时不必再提交一次状态更新（多任务并发下是无谓的列表重建）。
                        // 与 M4 的通知节流叠加后，IPC 与重组都被压到「确有变化」的时刻。
                        if (sum != lastReportedSum) {
                            lastReportedSum = sum
                            updateTask(taskId) { it.copy(downloadedBytes = sum, totalBytes = totalBytes) }
                        }
                        val now = System.currentTimeMillis()
                        synchronized(lock) {
                            if (now - lastBitmapPersist > PARTMAP_PERSIST_MS) {
                                lastBitmapPersist = now
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
        }

        // M2：源文件已变 —— 本函数不再收尾，交由上层丢弃旧进度后整份重下
        sourceChanged.get()?.let { throw it }

        // 已在作用域取消时清空队列并删除位图表，此处用单连接把文件从头写满（FileOutputStream 会截断旧分块数据）。
        if (rangeUnsupported.get()) {
            AppLogger.log("DownloadManager", "降级单连接下载: taskId=$taskId, url=$url")
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
            partmapFile.delete()   // O4：成功后清理位图表
        } else {
            // 完整性不达标：抛出异常触发 FAILED 并保留位图表，下次续传仅补未完成块
            throw IOException("分块下载完整性校验失败：allDone=$allDone, $totalDownloaded/$totalBytes, disk=$onDisk")
        }
    }

    /**
     * 下载单个分块（HTTP Range 请求），写入 outputFile 的 [start, end] 区间。
     * 块内读取循环响应协程取消（暂停 / 取消时及时退出）。
     * @param callRef O5：该分块当前连接的引用，供监控器精准取消「单个慢块」而不影响其它块。
     */
    private suspend fun downloadChunk(
        taskId: Int,
        url: String,
        outputFile: File,
        index: Int,
        start: Long,
        end: Long,
        totalBytes: Long,
        chunkDownloaded: AtomicLongArray,
        callRef: AtomicReference<Call?>
    ) {
        val chunkLength = end - start + 1
        // O3：本块可能已被前一次尝试写入过一部分（被驱逐 / 瞬断后重新入队）。
        // 从「块内已有字节数」处续传，避免整块从头重来 —— 长块重来的代价极高，
        // 且让 MAX_CHUNK_ATTEMPTS 不再等价于「整块白下 5 次」。
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
        callRef.set(call)   // O5：登记当前连接，供监控器精准驱逐单个慢块
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
                // M1/M2：核对响应区间与请求区间、总量与本地记录是否一致
                verifyContentRange(r.header("Content-Range"), index, from, end, totalBytes)
                val input = r.body.byteStream()
                input.use { `in` ->
                    val expected = chunkLength
                    val bufSize = chooseBufferSize(expected)
                    var written = alreadyWritten
                    RandomAccessFile(outputFile, "rw").use { raf ->
                        raf.seek(from)
                        val buffer = ByteArray(bufSize)
                        while (true) {
                            if (currentCoroutineContext()[Job]?.isActive != true) break
                            val bytesRead = `in`.read(buffer)
                            if (bytesRead == -1) break
                            // 直接写入会越过 end 覆盖「下一个分块」的区域，造成跨块数据错乱。
                            val toWrite = minOf(bytesRead.toLong(), expected - written).toInt()
                            if (toWrite > 0) {
                                raf.write(buffer, 0, toWrite)
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
            callRef.set(null)   // O5：连接结束（成功/失败/被驱逐）后清空引用
        }
    }

    /**
     * M1/M2：校验 206 响应的 Content-Range。
     *
     * - **起点**必须等于本次请求的起点：中间层/源站可能返回 206 却是别的区间，数据会被写到错误偏移，
     *   而收尾只校验总长度，会静默产出「能播但花屏/跳帧」的文件，比直接失败更糟；
     * - **总量**必须与本地持久化的 totalBytes 一致：不一致说明源站文件已变（换源/转码/重传），
     *   继续按旧位图表续传会把新旧数据拼接，必须抛 [SourceChangedException] 交由上层整份重下。
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
     * 原子地更新单个任务。使用 tasksLock 保护读-改-写操作。
     * P2-3：任务权威存储为 Map，O(1) 按 id 命中，避免每次全量扫描列表。
     */
    private fun updateTask(taskId: Int, updater: (DownloadTask) -> DownloadTask) {
        var progressUpdate: ProgressUpdate? = null
        tasksLock.withLock {
            val current = taskMap[taskId] ?: return
            val newTask = updater(current)
            val statusChanged = newTask.status != current.status
            // 纯进度刷新仅就地替换对应元素（updateTaskInList），避免每次 500ms 都全量重建列表。
            taskMap[taskId] = newTask
            if (statusChanged) emitTasks() else updateTaskInList(newTask)
            // 进度更新走内存态驱动 UI；仅状态切换或达到节流间隔才落盘，降低写放大
            if (statusChanged || shouldPersistProgress(taskId)) {
                lastPersistTime[taskId] = System.currentTimeMillis()
                persistTask(newTask)
            }
            // 仅锁定内记录需回调的信息，真正回调移出锁外执行（见下方 D2 说明）。
            val candidate = when (newTask.status) {
                DownloadStatus.DOWNLOADING -> if (newTask.totalBytes > 0) {
                    ProgressUpdate(taskId, newTask.title, (newTask.downloadedBytes * 100 / newTask.totalBytes).toInt(), newTask.status)
                } else null
                DownloadStatus.PAUSED -> ProgressUpdate(
                    taskId, newTask.title,
                    if (newTask.totalBytes > 0) (newTask.downloadedBytes * 100 / newTask.totalBytes).toInt() else 0,
                    newTask.status
                )
                DownloadStatus.COMPLETED, DownloadStatus.FAILED ->
                    ProgressUpdate(taskId, newTask.title, 100, newTask.status)
                else -> null
            }
            // M4：通知转发节流。分块 reporter 每 500ms 触发一次 updateTask，若每次都转发，
            // 5 个并发任务即约 10 次/秒的 binder 调用 + 通知重绘，而整数百分比多数并未变化。
            // 状态切换（含暂停/完成/失败）始终转发，保证通知语义不丢。
            if (candidate != null) {
                val last = lastNotifiedPercent[taskId]
                if (statusChanged || last == null || last != candidate.progress) {
                    lastNotifiedPercent[taskId] = candidate.progress
                    progressUpdate = candidate
                }
            }
        }
        //将跨进程IPC移出 tasksLock，
        progressUpdate?.let { (id, title, progress, status) ->
            onProgressUpdate?.invoke(id, title, progress, status)
        }
    }
    private fun trackCall(taskId: Int, call: Call) {
        taskCalls.computeIfAbsent(taskId) { CopyOnWriteArrayList<Call>() }.add(call)
        if (taskCancelFlags[taskId]?.get() == true) {
            runCatching { call.cancel() }
        }
    }

    private fun cancelTaskCalls(taskId: Int) {
        // 顺序重要：必须「先置位、后排空」，见 trackCall 的正确性论证
        taskCancelFlags.computeIfAbsent(taskId) { AtomicBoolean(false) }.set(true)
        taskCalls.remove(taskId)?.forEach { runCatching { it.cancel() } }
    }

    /**
     * P2-2：任务是否正在被暂停/取消撕销。
     *
     * 这是配合「先中断在途请求、再取消协程」新顺序的必要信号：该窗口内协程 isActive 仍为 true，
     * 若仅凭 isActive 判定，分块 worker 会把「被主动取消的 Call」误判为网络瞬断而反复重试，
     * 5 次耗尽后把一次**暂停**升级成 FAILED。以此标记作为撕销期的权威依据。
     */
    private fun isTaskCancelled(taskId: Int): Boolean = taskCancelFlags[taskId]?.get() == true

    /**
     * 进度类落盘节流：距上次落盘超过 [PROGRESS_PERSIST_INTERVAL_MS] 才允许写 DB。
     * 状态切换（由调用方判断）不走此节流，始终立即落盘。
     */
    private fun shouldPersistProgress(taskId: Int): Boolean {
        val now = System.currentTimeMillis()
        val last = lastPersistTime[taskId] ?: 0L
        return now - last >= PROGRESS_PERSIST_INTERVAL_MS
    }

    /** 提交一次落盘。只入队不直接写库，实际写入由 init 中的单消费者协程串行执行。 */
    private fun persistTask(task: DownloadTask) {
        persistChannel.trySend(PersistCmd.Upsert(task))
    }

    /** 提交一次删除。与 [persistTask] 共用同一队列，避免删除与在途更新竞争。 */
    private fun persistDelete(taskId: Int) {
        persistChannel.trySend(PersistCmd.Delete(taskId))
    }

    private fun DownloadTask.toEntity() = DownloadEntity(
        id = id,
        title = title,
        quality = quality,
        url = url,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status.name,
        filePath = filePath,
        thumbnailUrl = thumbnailUrl,
        videoId = videoId,
        errorMessage = errorMessage
    )

    fun pauseDownload(taskId: Int) {
        cancelTaskCalls(taskId)
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        // 复用 updateTask：状态切换（→PAUSED）会立即落盘
        updateTask(taskId) { it.copy(status = DownloadStatus.PAUSED) }
        // 避免空出的槽位不拾起 PENDING 任务导致其余任务永久挂起。
        startNextPendingTask()
        AppLogger.log("DownloadManager", "Download paused: $taskId")
    }

    fun resumeDownload(taskId: Int) {
        tasksLock.withLock {
            resumeDownloadInternal(taskId)
        }
    }

    /**
     * P1：一键重试全部失败任务。仅对 FAILED 状态任务触发续传，
     * 复用 resumeDownloadInternal（含续传起点推导与并发槽位门控），受并发上限约束依次进入调度。
     * 调用方需持有 tasksLock（此处已加锁），resumeDownloadInternal 不再重复取锁。
     */
    fun retryAllFailed() {
        tasksLock.withLock {
            taskMap.values.filter { it.status == DownloadStatus.FAILED }.forEach {
                resumeDownloadInternal(it.id)
            }
        }
        AppLogger.log("DownloadManager", "retryAllFailed: 已触发全部失败任务重试")
    }

    /**
     * 内部方法，调用者必须持有 tasksLock。
     */
    private fun resumeDownloadInternal(taskId: Int) {
        val task = taskMap[taskId] ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        if (downloadJobs.containsKey(taskId)) return
        val (resumeBytes, resumeMap) = deriveResumeState(task)
        startDownloadWithConcurrencyInternal(task, resumeBytes, resumeMap)
        if (resumeMap != null) {
            AppLogger.log("DownloadManager", "Resume (chunked) download: $taskId, undone chunks=${resumeMap.count { !it }}/${resumeMap.size}")
        } else {
            AppLogger.log("DownloadManager", "Resume download started: $taskId (resumeBytes=$resumeBytes)")
        }
    }

    fun cancelDownload(taskId: Int) {
        cancelTaskCalls(taskId)
        val job = downloadJobs.remove(taskId)
        job?.cancel()
        if (job == null) taskCancelFlags.remove(taskId)
        tasksLock.withLock {
            val task = taskMap[taskId]
            task?.let {
                val file = File(it.filePath)
                if (file.exists()) file.delete()
                // O4：一并清理分块续传位图表，避免孤儿文件
                val pm = File(it.filePath + PARTMAP_SUFFIX)
                if (pm.exists()) pm.delete()
            }
            taskMap.remove(taskId)
            emitTasks()
        }
        // 走与 persistTask 相同的串行队列，避免 DELETE 与在途 UPSERT 竞争导致任务复活
        persistDelete(taskId)
        lastNotifiedPercent.remove(taskId)
        // S1：任务记录已删除，无法再走 updateTask 进度通道；必须显式通知前台服务撤销该任务的
        // 常驻通知并重算活动集合，否则通知（ongoing 不可划掉）与 dataSync 前台服务会永久残留。
        forwardRemoveToService(taskId)
        AppLogger.log("DownloadManager", "Download cancelled: $taskId")
    }

    /**
     * P3-2：显式生命周期出口。单例随进程存在是预期行为，但提供结构化取消入口：
     * 进程退出、测试拆卸、或需要强制中断所有在途下载时调用，保证协程与在途请求被确定性回收。
     * scope 根使用 SupervisorJob，单个下载任务的异常不会株连取消其它在途任务（见 scope 声明处）。
     */
    fun shutdown() {
        scopeJob.cancel()
        downloadJobs.clear()
        taskCalls.clear()
        taskCancelFlags.clear()
        activeSlots.set(0)
        AppLogger.log("DownloadManager", "shutdown: 已取消下载作用域并清理在途状态")
    }

    fun getCompletedDownloads(): List<DownloadTask> {
        return taskMap.values
            .filter { it.status == DownloadStatus.COMPLETED }
            .sortedByDescending { it.id }
    }

    fun getDownloadingTasks(): List<DownloadTask> {
        return taskMap.values
            .filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            }
            .sortedByDescending { it.id }
    }

    suspend fun getCompletedDownloadCount(): Int {
        return try {
            downloadDao.getCompletedCount()
        } catch (e: SQLiteException) {
            getCompletedDownloads().size
        }
    }

    fun isVideoDownloaded(videoId: String, quality: String = ""): Boolean {
        return if (videoId.isBlank()) {
            false
        } else {
            taskMap.values.any {
                it.videoId == videoId && it.status == DownloadStatus.COMPLETED &&
                        (quality.isBlank() || it.quality == quality)
            }
        }
    }

    fun isVideoDownloading(videoId: String, quality: String = ""): Boolean {
        return if (videoId.isBlank()) {
            false
        } else {
            taskMap.values.any {
                it.videoId == videoId &&
                        (quality.isBlank() || it.quality == quality) &&
                        (it.status == DownloadStatus.DOWNLOADING ||
                                it.status == DownloadStatus.PENDING ||
                                it.status == DownloadStatus.PAUSED)
            }
        }
    }

    fun getDownloadStatus(videoId: String): DownloadStatus? {
        val task = if (videoId.isBlank()) {
            null
        } else {
            taskMap.values.find { it.videoId == videoId }
        }
        return task?.status
    }

    /**
     * 将下载进度转发给 DownloadService。
     * 下载中使用 startForegroundService 以确保服务在前台运行；
     * 完成/失败时服务应已在前台运行，使用 startService 更新最终通知并停止服务。
     */
    private fun forwardToService(
        context: Context,
        title: String,
        progress: Int,
        status: DownloadStatus,
        taskId: Int
    ) {
        val intent = Intent().apply {
            setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PROGRESS, progress)
            putExtra(EXTRA_STATUS, status.name)
        }
        val firstTime = downloadServiceStarted.compareAndSet(false, true)
        try {
            if (firstTime) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            if (firstTime) {
                downloadServiceStarted.set(false)
                runCatching { context.startService(intent) }.onFailure { se ->
                    AppLogger.log("DownloadManager", "startService 同样被系统拒绝，跳过本次通知转发: ${se.message}")
                }
            }
            AppLogger.log("DownloadManager", "转发进度到 DownloadService 被系统拒绝: ${e.message}")
        } catch (e: SecurityException) {
            // 忽略转发异常，避免影响下载主流程
        }
        // 收到终态/暂停且已无活动任务时复位标记，允许下次新下载重新走 startForegroundService。
        // S2：服务在「无活动任务」时会退出前台并 stopSelf，此处必须复位，否则后续恢复下载会走
        // startService（服务已不在前台，后台启动会被系统拒绝），导致该次下载完全没有通知。
        if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED ||
            status == DownloadStatus.PAUSED
        ) {
            if (!hasActiveDownload()) downloadServiceStarted.set(false)
        }
    }

    /**
     * S1：通知 DownloadService 撤销某个已被取消/删除任务的通知。
     *
     * cancelDownload 会直接删掉任务记录（不再产生任何 updateTask 回调），因此必须走独立通道。
     * 用 startService 而非 startForegroundService：这只是「清理一个已存在的通知」，不承担前台义务；
     * 服务未运行时它会立即 stopSelfResult，代价可忽略。
     */
    private fun forwardRemoveToService(taskId: Int) {
        val intent = Intent().apply {
            setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
            action = ACTION_REMOVE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }
        runCatching { context.startService(intent) }.onFailure { e ->
            AppLogger.log("DownloadManager", "转发取消通知被系统拒绝（通知可能残留到进程结束）: ${e.message}")
        }
        if (!hasActiveDownload()) downloadServiceStarted.set(false)
    }

    /** 是否还有处于 DOWNLOADING / PENDING 的任务（决定前台服务是否应保持活动）。 */
    private fun hasActiveDownload(): Boolean = taskMap.values.any {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
    }

    private fun classifyError(e: Throwable): String {
        return when (e) {
            is SocketTimeoutException -> "网络超时，请检查网络后重试"
            is UnknownHostException -> "无法解析服务器地址（DNS 失败）"
            is SSLException -> "安全连接失败（SSL 错误）"
            is ConnectException -> "无法建立连接，请检查网络"
            is java.io.IOException -> "网络读写错误：${e.message ?: "未知"}"
            else -> "下载失败：${e.message ?: "未知错误"}"
        }
    }

    private fun classifyHttpError(code: Int): String {
        return when (code) {
            401, 403 -> "资源不可用（无权限，HTTP $code）"
            404 -> "资源不存在（HTTP 404）"
            in 400..499 -> "请求被拒绝（HTTP $code）"
            in 500..599 -> "服务器错误（HTTP $code）"
            else -> "下载失败（HTTP $code）"
        }
    }

    /**
     * O3：根据网络类型 + 实测单连接吞吐 + 全局连接预算，决定首下分块数。
     * - Wi-Fi 基准 8、移动 4、其它 2；
     * - 单连接已高速（低 RTT 饱和）→ 降到 ≤3，省握手/调度开销；
     * - 单连接低速（高 RTT/限速）→ 保持较多分块以提速；
     * - 受全局连接预算（MAX_TOTAL_CONNECTIONS / 实际任务数）与最小分块尺寸约束。
     */
    private fun computeChunkCount(netClass: NetworkClass, singleBps: Long, totalBytes: Long): Int {
        var base = when (netClass) {
            NetworkClass.WIFI -> 8
            NetworkClass.CELLULAR -> 4
            NetworkClass.OTHER -> 2
        }
        if (singleBps >= HIGH_SINGLE_BPS) {
            base = minOf(base, 3)
        } else if (singleBps in 1..LOW_SINGLE_BPS) {
            base = minOf(maxOf(base, 4), MAX_CHUNKS)
        }
        val budgeted = effectiveChunkCap()
        val maxBySize = maxOf((totalBytes / CHUNK_SIZE).toInt(), 1)
        return minOf(base, budgeted, MAX_CHUNKS, maxBySize)
    }

    /**
     * 根据分块大小选择读取/写入缓冲（256KB–1MB）。
     */
    private fun chooseBufferSize(chunkBytes: Long): Int {
        return when {
            chunkBytes >= 16 * 1024 * 1024L -> BUFFER_SIZE_LARGE   // ≥16MB 块用 1MB 缓冲
            chunkBytes >= 8 * 1024 * 1024L -> BUFFER_SIZE_MEDIUM   // ≥8MB 块用 512KB 缓冲
            else -> CHUNK_BUFFER_SIZE                              // 默认 256KB
        }
    }


    /**
     * 写位图表，返回是否成功。
     *
     * M7：此前整段包在 runCatching 里且**无任何日志**，落盘失败后调用方无从察觉。
     * O4：先写同名 .tmp 再 rename 原子替换，避免进程在写入中途被杀留下半截内容
     * （读侧对短位图是失败安全的，但块数行被截断会误判为「需整份重下」）。
     */
    private fun writePartmap(file: File, chunkCount: Int, done: BooleanArray): Boolean {
        val payload = "$chunkCount\n${done.joinToString("") { if (it) "1" else "0" }}"
        return runCatching {
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(payload)
            if (file.exists() && !file.delete()) throw IOException("旧位图表删除失败: ${file.name}")
            if (!tmp.renameTo(file)) throw IOException("位图表重命名失败: ${tmp.name}")
        }.onFailure { e ->
            AppLogger.logError("DownloadManager", "位图表写入失败(${file.name}): ${e.message}", e)
        }.isSuccess
    }

    private fun readPartmap(file: File): Pair<Int, BooleanArray?> {
        return runCatching {
            val lines = file.readLines()
            val cc = lines.getOrNull(0)?.toIntOrNull() ?: return@runCatching (0 to null)
            // 位图表是磁盘上的外部输入：块数行若损坏可能是个极大值，先做上界钳制再分配数组，避免 OOM
            if (cc <= 0 || cc > MAX_PARTMAP_CHUNKS) return@runCatching (0 to null)
            val bits = lines.getOrNull(1) ?: return@runCatching (0 to null)
            val arr = BooleanArray(cc) { i -> bits.getOrNull(i) == '1' }
            cc to arr
        }.getOrDefault(0 to null)
    }

    private companion object {
        const val MAX_CONCURRENT = 5
        const val PROGRESS_PERSIST_INTERVAL_MS = 3000L
        // startDownload 的结果码（负数均为「未新建任务」）
        const val RESULT_INVALID_URL = -1
        const val RESULT_ALREADY_ACTIVE = -2
        const val RESULT_ALREADY_COMPLETED = -3
        // 单个文件名（UTF-8 字节）上限：ext4/大多数文件系统为 255 字节，留出余量
        const val MAX_FILE_NAME_BYTES = 200
        const val DOWNLOAD_SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
        // S1：取消/删除任务的通知通道。必须与 DownloadService.ACTION_REMOVE_TASK 手工保持一致。
        const val ACTION_REMOVE_TASK = "app.amisles.hanime.service.action.REMOVE_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"
        const val MAX_REQUESTS_PER_HOST = 16
        const val MAX_IDLE_CONNECTIONS = 16
        const val KEEP_ALIVE_SECONDS = 60L
        const val HIGH_SINGLE_BPS = 8L * 1024 * 1024
        const val LOW_SINGLE_BPS = 1_500_000L
        const val PROBE_WINDOW = 512 * 1024          // 吞吐探测窗口 512KB
        const val PARTMAP_SUFFIX = ".partmap"
        const val PARTMAP_PERSIST_MS = 1000L
        // 位图表块数的合法上界（防损坏文件撑爆数组分配）
        const val MAX_PARTMAP_CHUNKS = 64

        // M3：慢块判定 = max(下限, min(实测每块速率 × SLOW_RELATIVE_PERCENT%, 上限))
        const val SLOW_THRESHOLD_FLOOR_BPS = 16 * 1024L    // 绝对下限：低于此值基本可断定为停滞连接
        const val SLOW_THRESHOLD_CAP_BPS = 512 * 1024L     // 上限：避免高速链路上阈值过大导致频繁驱逐
        const val SLOW_RELATIVE_PERCENT = 15L              // 相对判据：低于每块基准速率的 15%
        const val SLOW_SAMPLE_MS = 2000L            // 逐块吞吐采样周期 2s
        const val SLOW_DURATION_MS = 15000L         // 持续低于阈值 15s 才驱逐，避免抖动误杀
        const val SLOW_GRACE_MS = 8000L             // 新块起步宽限期，期间 0 字节不判慢
        const val MAX_CHUNK_ATTEMPTS = 5            // 单块最大重试/驱逐次数，超限判定整体失败
        const val BUFFER_SIZE_LARGE = 1_048_576     // ≥16MB 分块用 1MB 缓冲
        const val BUFFER_SIZE_MEDIUM = 512 * 1024   // ≥8MB 分块用 512KB 缓冲
        const val MAX_CHUNKS = 8
        const val MAX_TOTAL_CONNECTIONS = 8
        // O2：每个任务至少保留的分块数（避免并发任务多时全部退化为单连接）
        const val MIN_CHUNKS_PER_TASK = 2
        const val CHUNK_SIZE = 4_000_000L
        const val MIN_CHUNK_TOTAL_BYTES = 12_000_000L
        const val CHUNK_BUFFER_SIZE = 256 * 1024
        const val DOWNLOAD_UA = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36"
    }
}