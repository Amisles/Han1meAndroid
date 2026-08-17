package app.amisles.hanime.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.preferences.Preferences
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import kotlin.coroutines.cancellation.CancellationException

// 避免持锁做跨进程 IPC
private data class ProgressUpdate(
    val taskId: Int,
    val title: String,
    val progress: Int,
    val status: DownloadStatus
)

// 分块请求服务器返回 200（忽略 Range）时抛出的标记异常。
private class RangeNotSupportedException(message: String) : IOException(message)

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
        _tasks.value = taskMap.values.toList()
    }

    // P3-3：进度类更新只替换列表中对应 id 的元素（其余元素引用保持不变，缩小 Compose 重组面），
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
    // P0-2：任务级在途 OkHttp 请求，暂停/取消时调用 Call.cancel() 立即中断底层阻塞读
    private val taskCalls = ConcurrentHashMap<Int, MutableList<Call>>()
    // P2-2：任务级取消标记。cancelTaskCalls 置位后，竞态窗口内才刚建立的 Call
    // 也会在 trackCall 中被立即取消，彻底消除「新建 Call 逃过取消 → 阻塞到 readTimeout(120s)」的窗口。
    private val taskCancelFlags = ConcurrentHashMap<Int, AtomicBoolean>()
    private val taskIdCounter = AtomicInteger(0)
    // P3-2：用 SupervisorJob 作为 scope 根，使单个下载任务的异常不会株连取消其它在途任务
    // （普通 Job 下任一子协程未捕获异常会取消整个 scope，导致「一个任务失败、全部下载中断」）。
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val tasksLock = ReentrantLock()
    // 并发下载的绝对硬上限：信号量固定容量、创建后不再重建，
    // 避免 updateConcurrencyLimit 重建对象导致在途任务持有的旧许可成为孤儿。
    private val downloadSemaphore: Semaphore = Semaphore(MAX_CONCURRENT)
    // 用户可配置的有效并发上限（1..MAX_CONCURRENT），作为调度软门限（由状态计数控制）
    @Volatile
    private var concurrencyLimit: Int = Preferences.maxDownloadConcurrent.coerceIn(1, MAX_CONCURRENT)
    // 进度落盘节流：进度类更新仅按此间隔写 DB，状态切换始终立即落盘，减少写放大
    private val lastPersistTime = ConcurrentHashMap<Int, Long>()
    // 已分配的下载槽位计数（同步计数器）：在“决定启动”那一刻 +1，
    // 任务进入终态/暂停/取消时 -1。作为并发门控统一依据，修复 B1/B3/B4
    // （原 currentDownloadingCount 基于异步状态，突发/批量场景下恒为 0）。
    private val activeSlots = AtomicInteger(0)
    // D5：跟踪前台服务是否已通过 startForegroundService 启动，
    // 后续进度更新改用 startService，避免 Android 12+ 反复 startForegroundService 的时序风险。
    // P2-6：用 AtomicBoolean.compareAndSet 保证首启判定与复位的原子性，
    // 避免 @Volatile 仅保证可见性、read-modify-write 非原子导致的并发首包重复 startForegroundService。
    private val downloadServiceStarted = AtomicBoolean(false)

    // P2-4 修复：原为 getter，每次访问都做 exists()/mkdirs() 磁盘 I/O，且在 tasksLock 内被调用，
    // 慢存储会连带阻塞 pause/cancel/resume/updateConcurrencyLimit 等全部取锁操作。
    // 改为 by lazy（默认 SYNCHRONIZED，线程安全）仅首次计算一次。
    // 目录若在运行期被清理，由写入侧的 parentFile.mkdirs() 兜底重建。
    private val downloadDir: File by lazy {
        // D1：外部存储不可用时（部分设备/限存储场景 getExternalFilesDir 返回 null）
        // 降级到应用私有内部存储，避免 NPE。
        val base = context.getExternalFilesDir(null)
            ?.let { File(it, "Downloads") }
            ?: File(context.filesDir, "Downloads")
        if (!base.exists()) base.mkdirs()
        base
    }

    init {
        onProgressUpdate = { taskId, title, progress, status ->
            forwardToService(context, title, progress, status, taskId)
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
                tasksLock.withLock {
                    taskMap.clear()
                    restoredTasks.forEach { taskMap[it.id] = it }
                    emitTasks()
                }
                // P2-4：统一使用 DownloadDao.getMaxId() 取代内存 maxOfOrNull，避免重复计算且语义更清晰
                taskIdCounter.set(downloadDao.getMaxId() ?: 0)
                AppLogger.log("DownloadManager", "Restored ${restoredTasks.size} tasks from DB, taskIdCounter=$taskIdCounter")
                // P1-3 修复：进程被杀时 DOWNLOADING 任务被恢复为 PAUSED，应自动续传，
                // 而非一直挂起。仅自动续传「原本正在下载」的任务，用户手动暂停的不动。
                val interruptedIds = entities.filter {
                    it.status == DownloadStatus.DOWNLOADING.name
                }.map { it.id }
                tasksLock.withLock {
                    interruptedIds.forEach { resumeDownloadInternal(it) }
                }
                // H1：恢复出的 PENDING 任务应自动进入调度（受并发上限约束），
                // 避免进程重启后它们永久挂起、只能手动恢复。
                startNextPendingTask()
            } catch (e: SQLiteException) {
                AppLogger.logError("DownloadManager", "Failed to restore tasks: ${e.message}", e)
            }
        }
    }

    fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String = "",
        videoId: String = ""
    ): Int {
        // A1：入参校验，避免空/非法直链产生“幽灵”FAILED 任务（返回 -1 表示拒绝）
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            AppLogger.logError("DownloadManager", "拒绝下载：非法 url=\"$url\" (title=$title, videoId=$videoId)")
            return -1
        }
        // P2-4 修复：文件名构造与 canonicalPath 越界校验涉及磁盘 I/O（目录解析 / 符号链接展开），
        // 全部移到 tasksLock 之外。文件名只依赖 title/quality/videoId/url，不依赖 taskId，
        // 因此提前计算不改变任何语义。
        // P2-1（安全项）：文件名加入 videoId（或 URL 短 hash）保证唯一，避免同名同画质互相覆盖；
        // videoId 一并清洗非法字符。最后用 canonicalPath 校验不逃出 downloadDir，防止路径穿越。
        val dir = downloadDir
        val uniqueSuffix = if (videoId.isNotBlank()) {
            videoId.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        } else {
            url.hashCode().toString(36)
        }
        val baseName = "${title}_$quality".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${baseName}_${uniqueSuffix}.mp4"
        val targetFile = File(dir, fileName)
        val canonical = runCatching { targetFile.canonicalPath }.getOrDefault(targetFile.absolutePath)
        val dirCanonical = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!canonical.startsWith(dirCanonical + File.separator) && canonical != dirCanonical) {
            AppLogger.logError("DownloadManager", "拒绝下载：路径越界 fileName=\"$fileName\"")
            return -1
        }
        val filePath = targetFile.absolutePath

        tasksLock.withLock {
            val existingTask = taskMap.values.find { it.url == url && it.quality == quality }
            if (existingTask != null) {
                AppLogger.log("DownloadManager", "Download already exists for $quality: $title (taskId=${existingTask.id})")
                if (existingTask.status == DownloadStatus.PAUSED || existingTask.status == DownloadStatus.FAILED) {
                    resumeDownloadInternal(existingTask.id)
                }
                return existingTask.id
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

            // P2-1 修复：槽位判定与占位统一交由下方 CAS 裁决，占不到则保持 PENDING 由链式调度拾起
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
        // O4：分块级续传——位图表存在且有未完成块时，仅重启这些块（仍并行），避免整文件重下
        if (partmapFile.exists() && file.exists()) {
            val (cc, bitmap) = readPartmap(partmapFile)
            if (cc > 0 && bitmap != null && bitmap.any { !it }) {
                return 0L to bitmap
            }
        }
        // 回退：文件级续传（单连接）或整文件重下
        // P0-1 修复：分块下载在随机偏移写入，file.length() 不可靠（可能等于 totalBytes 却实为空洞）。
        // 仅当「文件实际字节数与已下载进度严格一致（顺序单连接写入）且明显小于总量」时才安全续传；
        // 否则删除残留文件整文件重下，杜绝空洞文件被标记为已完成。
        val safeResume = file.exists() && file.length() > 0
            && file.length() < task.totalBytes
            && file.length() == task.downloadedBytes
        if (safeResume) return file.length() to null
        if (file.exists()) runCatching { file.delete() }
        if (partmapFile.exists()) runCatching { partmapFile.delete() }
        return 0L to null
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
        // P2-2：清除上一轮可能残留的取消标记，避免新 Job 的请求刚建立就被误取消
        taskCancelFlags.remove(task.id)

        // P1-3：以 LAZY 启动，保证「注册进 downloadJobs」先于协程体执行，
        // 否则秒失败的任务可能在注册前就跑完 finally，导致条目残留（清理失效）。
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                downloadSemaphore.withPermit {
                    AppLogger.log("DownloadManager", "开始下载: ${task.title} (并发槽位已获取, resumeFrom=$resumeBytes)")

                    updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
                    downloadFile(task.id, task.url, task.filePath, resumeBytes, resumeChunkMap)
                    // P0-2 修复：任务被取消（暂停/取消）时，保留调用方设置的 PAUSED 状态，
                    // 不在此处改写为 FAILED/COMPLETED。
                    // P2-2：撕销期（isActive 仍 true）提前返回时跳过完整性校验，避免把暂停判成 FAILED
                    if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(task.id)) {
                        AppLogger.log("DownloadManager", "下载被取消，保留暂停状态: ${task.title}")
                        return@launch
                    }
                    // H5/H6 完整性校验：完成前断言下载字节数达标，并核对磁盘实际大小，
                    // 避免写出不全文件却标记成功（含分块空洞场景）。
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
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IOException) {
                // P0-2 修复：Call.cancel() 在暂停/取消时会令阻塞读抛出 IOException，
                // 这并非真实网络错误，不标记为失败，保留 PAUSED 状态。
                // P2-2：撕销期（isActive 仍 true）直接保留 PAUSED，不标记失败
                if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(task.id)) {
                    AppLogger.log("DownloadManager", "下载被取消（IO 中断），保留暂停状态: ${task.title}")
                    return@launch
                }
                AppLogger.logError("DownloadManager", "下载失败 ${task.title}: ${e.message}", e)
                updateTask(task.id) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyError(e)) }
            } finally {
                // 释放槽位并调度下一个等待任务（成功/失败/取消均执行）
                // P1-3 修复：任务进入终态后必须清理 job 记录。原先只有 pause/cancel 会 remove，
                // 自然 COMPLETED/FAILED 的条目会残留，导致 resumeDownloadInternal 的
                // containsKey 守卫把「FAILED 任务重试」静默吞掉（且 map 随完成任务无限增长）。
                // 用「键值同时匹配」的条件移除，避免误删已被 resume 重新注册的新 Job。
                currentCoroutineContext()[Job]?.let { self -> downloadJobs.remove(task.id, self) }
                // P2-2 修复：Call 列表/取消标记的清理必须让位于「已接管该任务的新 Job」。
                // 「暂停→立即恢复」时，新 Job 已注册（LAZY 保证注册先于运行）并可能已 trackCall，
                // 而旧 Job 的 finally 此刻才异步执行；无条件 remove 会抹掉新 Job 的 Call 登记，
                // 使后续 pause 无法中断其阻塞读（僵持到 readTimeout）。
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
                // P2-1：等待期间可能已有分块进度，启动前重新推导续传起点，避免整文件重下
                val (rb, rm) = deriveResumeState(pendingTask)
                startDownloadWithConcurrencyInternal(pendingTask, rb, rm)
            }
        }
    }

    fun updateConcurrencyLimit(maxConcurrent: Int) {
        val safeMax = maxConcurrent.coerceIn(1, MAX_CONCURRENT)
        // 仅更新软门限；信号量固定容量，不再重建对象（避免旧许可成为孤儿）
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
        // O4：分块续传——直接进入分块下载，仅重启位图表中未完成的块
        if (resumeChunkMap != null) {
            val total = taskMap[taskId]?.totalBytes ?: 0L
            if (total > 0) {
                downloadFileChunked(taskId, url, filePath, total, resumeMap = resumeChunkMap)
                return
            }
            // totalBytes 未知时退化为单连接续传
        }
        // 续传（恢复/失败重试）场景：采用单连接 Range 续传，稳定优先
        if (resumeBytes > 0L) {
            downloadFileSingle(taskId, url, filePath, resumeBytes)
            return
        }
        // 首下：探测 Range 支持 + 测量单连接吞吐（O3 自适应依据）
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
        return runCatching {
            val t0 = System.nanoTime()
            val call = client.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", DOWNLOAD_UA)
                    .header("Referer", DOWNLOAD_REFERER)
                    .header("Range", "bytes=0-${PROBE_WINDOW - 1}")
                    .get().build()
            )
            trackCall(taskId, call) // 复用任务级 Call 跟踪，暂停时可由 cancelTaskCalls 中断
            val resp = call.execute()
            resp.use { r ->
                if (r.code != 206) return@runCatching null
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
        }.getOrNull()
    }

    /**
     * 单连接顺序下载（首下降级 / 续传 / 不支持分块路径）。
     * 复用原有稳定逻辑：支持 Range 续传、失败限次重试、每 500ms 节流更新进度。
     */
    private suspend fun downloadFileSingle(taskId: Int, url: String, filePath: String, resumeBytes: Long) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", DOWNLOAD_UA)
            .header("Referer", DOWNLOAD_REFERER)
            .get()

        if (resumeBytes > 0) {
            requestBuilder.header("Range", "bytes=$resumeBytes-")
        }

        // C3：对瞬时网络异常做有限重试（单次 1s 退避），其余异常按类型细分原因
        var response: Response? = null
        var attempt = 0
        val maxRetries = 1
        while (response == null && attempt <= maxRetries) {
            try {
                val call = client.newCall(requestBuilder.build())
                trackCall(taskId, call)
                response = call.execute()
            } catch (e: IOException) {
                // P2-2：撕销期既不重试也不标记失败，直接返回让 PAUSED 生效
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

            // C1：续传但服务器返回 200（忽略 Range），降级为全量重下：
            // 后续 FileOutputStream(outputFile, false) 会先截断已有部分文件，已下载字节作废。
            if (resumeBytes > 0 && resp.code == 200) {
                AppLogger.log("DownloadManager", "服务器不支持 Range(返回 200)，降级为全量重下: $filePath")
            }

            val isPartial = resp.code == 206
            val totalBytes = if (isPartial && resumeBytes > 0) {
                val contentRange = resp.header("Content-Range")
                val totalFromRange = contentRange?.let {
                    Regex("/(\\d+)$").find(it)?.groupValues?.get(1)?.toLongOrNull()
                }
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
        val perTask = MAX_TOTAL_CONNECTIONS / active
        return perTask.coerceAtLeast(1)
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
        // 不足 2 块无并行收益，退回单连接
        if (chunkCount < 2) {
            downloadFileSingle(taskId, url, filePath, 0L)
            return
        }

        val outputFile = File(filePath)
        val partmapFile = File(filePath + PARTMAP_SUFFIX)

        // O4：分块完成位图。续传时复用位图表，仅重启未完成块；首下时全 false。
        val chunkDone = resumeMap ?: BooleanArray(chunkCount)
        if (resumeMap == null) {
            // 首下：清空旧文件与旧位图表，重新分块（O8：不再 setLength 预分配，避免空洞）
            if (outputFile.exists()) outputFile.delete()
            outputFile.parentFile?.mkdirs()
            outputFile.createNewFile()
            writePartmap(partmapFile, chunkCount, chunkDone)
        } else if (!partmapFile.exists()) {
            writePartmap(partmapFile, chunkCount, chunkDone)
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
        // O5 运行时状态：逐块连接引用、吞吐采样、重试计数（详见下方 monitor）
        val initialDone = chunkDone.count { it }
        val completedChunks = AtomicInteger(initialDone)
        val chunkCallRefs = Array(chunkCount) { AtomicReference<Call?>(null) }
        // P1-1 修复：监控基线由 LongArray 改为 AtomicLongArray。worker 重试时需重置基线，
        // 而读写分属 worker/monitor 两个线程，普通数组无可见性保证（可能读到旧高水位，
        // 使重置后的块被误判为「速率负增长」而反复驱逐）。
        val chunkLastBytes = AtomicLongArray(chunkCount)
        val chunkSlowSince = AtomicLongArray(chunkCount)
        val chunkFirstObserved = AtomicLongArray(chunkCount)
        val chunkAttempts = IntArray(chunkCount)
        val queue = ArrayDeque<Int>().apply { for (i in 0 until chunkCount) if (!chunkDone[i]) addLast(i) }

        // P3-1：标记「服务器对分块请求返回 200 忽略 Range」，用于触发整任务回退单连接下载
        val rangeUnsupported = AtomicBoolean(false)
        try {
            coroutineScope {
                val csJob = this.coroutineContext[Job]  // P3-1：捕获外层作用域 Job，便于回退时整体取消 worker/monitor/reporter
            // O4+O5：以「分块队列 + 固定 worker 池」取代一次性全量启动。
            // worker 循环领取分块并下载；慢块/瞬断由 monitor 取消连接后重新入队，
            // 由空闲 worker 以新连接重试，避免单块卡慢拖垮整体（aria2 lowest-speed-limit 语义）。
            fun takeChunk(): Int = synchronized(queue) { if (queue.isNotEmpty()) queue.removeFirst() else -1 }
            fun requeueChunk(i: Int) = synchronized(queue) { queue.addLast(i) }

            val workerCount = chunkCount.coerceAtMost(MAX_REQUESTS_PER_HOST)
            val workers = (0 until workerCount).map { _ ->
                launch(Dispatchers.IO) {
                    // P2-2：撕销标记与 isActive 并列作为退出条件
                    while (currentCoroutineContext()[Job]?.isActive == true && !isTaskCancelled(taskId)) {
                        val idx = takeChunk()
                        if (idx < 0) break
                        val attempts = ++chunkAttempts[idx]
                        val start = idx * chunkSize
                        val end = if (idx == chunkCount - 1) totalBytes - 1 else start + chunkSize - 1
                        try {
                            downloadChunk(taskId, url, outputFile, idx, start, end, chunkDownloaded, chunkCallRefs[idx])
                            synchronized(lock) { chunkDone[idx] = true }
                            completedChunks.incrementAndGet()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: IOException) {
                            // 任务已取消则停止重试；否则（慢块驱逐/瞬断）重新入队以新连接重试
                            if (currentCoroutineContext()[Job]?.isActive != true || isTaskCancelled(taskId)) break
                            // P3-1：检测到服务器不支持分块（返回 200 忽略 Range），标记整任务回退单连接，
                            // 并取消本作用域让其余 worker/monitor/reporter 立即终止，避免空耗 5 次重试后判失败。
                            if (e is RangeNotSupportedException) {
                                AppLogger.log("DownloadManager", "分块下载检测到不支持 Range，回退单连接: ${e.message}")
                                rangeUnsupported.set(true)
                                synchronized(queue) { queue.clear() }
                                csJob?.cancel()
                                return@launch
                            }
                            if (attempts >= MAX_CHUNK_ATTEMPTS) {
                                throw IOException("分块 $idx 重试 $attempts 次仍失败，终止下载", e)
                            }
                            // P1-1 修复：重试会用 Range 整段覆盖重写该块（磁盘内容正确），
                            // 但 downloadChunk 用 addAndGet 累加，若不清零会「旧 partial + 新 full」叠加，
                            // 使 reporter 汇总出的进度瞬时 >100%（多次重试后更夸张）。
                            // 同时清空监控基线，让新连接重新走起步宽限期，避免刚建立就被判慢驱逐。
                            chunkDownloaded.set(idx, 0L)
                            chunkLastBytes.set(idx, 0L)
                            chunkFirstObserved.set(idx, 0L)
                            chunkSlowSince.set(idx, 0L)
                            AppLogger.log("DownloadManager", "分块 $idx 失败/被驱逐，重新入队以新连接重试(第${attempts}次): ${e.message}")
                            requeueChunk(idx)
                        }
                    }
                }
            }

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
                        if (rate < SLOW_THRESHOLD_BPS) {
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
                while (completedChunks.get() < chunkCount &&
                    currentCoroutineContext()[Job]?.isActive == true &&
                    !isTaskCancelled(taskId)) {
                    delay(500)
                    var sum = 0L
                    for (i in 0 until chunkCount) sum += chunkDownloaded.get(i)
                    updateTask(taskId) { it.copy(downloadedBytes = sum, totalBytes = totalBytes) }
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
        } catch (ce: kotlinx.coroutines.CancellationException) {
            if (!rangeUnsupported.get()) throw ce
        }

        // P3-1：分块请求被服务器以 200 拒绝（忽略 Range）→ 回退单连接全量下载。
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
        chunkDownloaded: AtomicLongArray,
        callRef: AtomicReference<Call?>
    ) {
        val call = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", DOWNLOAD_UA)
                .header("Referer", DOWNLOAD_REFERER)
                .header("Range", "bytes=$start-$end")
                .get().build()
        )
        trackCall(taskId, call)
        callRef.set(call)   // O5：登记当前连接，供监控器精准驱逐单个慢块
        try {
            val resp = call.execute()
            resp.use { r ->
                // P1-1 修复：必须收到 206 才表示该响应是 Range 分块；
                // 若服务器忽略 Range 返回 200/其它，写入会错位到 [start, start+body) 造成文件损坏。
                // P3-1：仅当服务器「返回 200 忽略 Range」时抛可降级标记异常（上层回退单连接）；
                // 其它非 206（403/416/5xx 等）视为真实错误，保持原有重试/失败语义。
                if (r.code == 200) {
                    throw RangeNotSupportedException("分块 $index 服务器忽略 Range(HTTP 200)，回退单连接下载")
                } else if (r.code != 206) {
                    throw IOException("分块 $index 不支持 Range(HTTP ${r.code})，无法安全分块下载")
                }
                val input = r.body.byteStream()
                input.use { `in` ->
                    // O8：按分块大小自适应放大缓冲（256KB–1MB），减少 read/write 系统调用与磁盘 I/O 占比
                    val expected = end - start + 1
                    val bufSize = chooseBufferSize(expected)
                    var written = 0L
                    RandomAccessFile(outputFile, "rw").use { raf ->
                        raf.seek(start)
                        val buffer = ByteArray(bufSize)
                        while (true) {
                            if (currentCoroutineContext()[Job]?.isActive != true) break
                            val bytesRead = `in`.read(buffer)
                            if (bytesRead == -1) break
                            // P0-1 配套：写入长度按剩余区间裁剪。若服务器无视 Range 上界超发数据，
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
                    // P0-1：必须校验「实际写入 == 期望区间长度」。否则（场景一）连接中途断流使块留零字节空洞，
                    // 而更高偏移块把文件撑到 totalBytes，收尾校验双双通过→损坏文件被标 COMPLETED；
                    // （场景二）暂停/取消时读循环 break 后仍会置 chunkDone=true，续传跳过该不完整块。
                    // 两种都须阻止「未写满却算完成」：未达标则抛异常，由 worker 判定重试或保留 PAUSED。
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
     * 原子地更新单个任务。使用 tasksLock 保护读-改-写操作。
     * P2-3：任务权威存储为 Map，O(1) 按 id 命中，避免每次全量扫描列表。
     */
    private fun updateTask(taskId: Int, updater: (DownloadTask) -> DownloadTask) {
        var progressUpdate: ProgressUpdate? = null
        tasksLock.withLock {
            val current = taskMap[taskId] ?: return
            val newTask = updater(current)
            val statusChanged = newTask.status != current.status
            // P2-3/P3-3：写入 Map；状态切换属结构变化需重建列表快照，
            // 纯进度刷新仅就地替换对应元素（updateTaskInList），避免每次 500ms 都全量重建列表。
            taskMap[taskId] = newTask
            if (statusChanged) emitTasks() else updateTaskInList(newTask)
            // 进度更新走内存态驱动 UI；仅状态切换或达到节流间隔才落盘，降低写放大
            if (statusChanged || shouldPersistProgress(taskId)) {
                lastPersistTime[taskId] = System.currentTimeMillis()
                persistTask(newTask)
            }
            // 仅锁定内记录需回调的信息，真正回调移出锁外执行（见下方 D2 说明）。
            // P2-5：PAUSED 也转发给服务，使通知能显示「已暂停 xx%」（原逻辑只转发 DOWNLOADING/COMPLETED/FAILED）。
            progressUpdate = when (newTask.status) {
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

    private fun persistTask(task: DownloadTask) {
        scope.launch {
            try {
                downloadDao.upsertDownload(
                    DownloadEntity(
                        id = task.id,
                        title = task.title,
                        quality = task.quality,
                        url = task.url,
                        totalBytes = task.totalBytes,
                        downloadedBytes = task.downloadedBytes,
                        status = task.status.name,
                        filePath = task.filePath,
                        thumbnailUrl = task.thumbnailUrl,
                        videoId = task.videoId,
                        errorMessage = task.errorMessage
                    )
                )
            } catch (e: SQLiteException) {
                AppLogger.logError("DownloadManager", "Failed to persist task ${task.id}: ${e.message}", e)
            }
        }
    }

    fun pauseDownload(taskId: Int) {
        // P2-2 修复：顺序必须是「先中断在途请求、再取消协程」。
        // call.execute() / input.read() 是阻塞调用，协程 cancel() 不会打断它们；
        // 若先 cancel 协程，worker 仍可能在极短窗口内新建请求并阻塞其上，最长僵持 readTimeout(120s)。
        cancelTaskCalls(taskId)
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        // 复用 updateTask：状态切换（→PAUSED）会立即落盘
        updateTask(taskId) { it.copy(status = DownloadStatus.PAUSED) }
        // B2：槽位已被释放（job 的 finally 中 decrement），立即尝试调度下一个等待任务，
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
     * 内部方法，调用者必须持有 tasksLock。
     */
    private fun resumeDownloadInternal(taskId: Int) {
        val task = taskMap[taskId] ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        if (downloadJobs.containsKey(taskId)) return

        // P2-1：续传起点推导抽取到 deriveResumeState，与链式调度共用同一判定
        val (resumeBytes, resumeMap) = deriveResumeState(task)

        // 复用统一启动函数：受 activeSlots 软门限（CAS）约束（修复 B3/P2-1），
        // 槽位满时会退回 PENDING 排队；结束后统一 decrement 槽位 + 调度下一个 PENDING 任务
        startDownloadWithConcurrencyInternal(task, resumeBytes, resumeMap)
        if (resumeMap != null) {
            AppLogger.log("DownloadManager", "Resume (chunked) download: $taskId, undone chunks=${resumeMap.count { !it }}/${resumeMap.size}")
        } else {
            AppLogger.log("DownloadManager", "Resume download started: $taskId (resumeBytes=$resumeBytes)")
        }
    }

    fun cancelDownload(taskId: Int) {
        // P2-2：同暂停，先中断在途请求再取消协程
        cancelTaskCalls(taskId)
        val job = downloadJobs.remove(taskId)
        job?.cancel()
        // 任务将被彻底删除：有 Job 时由其 finally 回收取消标记（带「新 Job 已接管」守卫）；
        // 无 Job 时没有 finally 兜底，此处直接回收，避免条目长期滞留
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
        scope.launch {
            try {
                downloadDao.deleteDownload(taskId)
            } catch (e: SQLiteException) {
                AppLogger.logError("DownloadManager", "Failed to delete task $taskId from DB: ${e.message}", e)
            }
        }
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
        return taskMap.values.filter { it.status == DownloadStatus.COMPLETED }
    }

    fun getDownloadingTasks(): List<DownloadTask> {
        return taskMap.values.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
        }
    }

    suspend fun getCompletedDownloadCount(): Int {
        return try {
            downloadDao.getCompletedCount()
        } catch (e: SQLiteException) {
            getCompletedDownloads().size
        }
    }

    fun isVideoDownloaded(videoId: String, quality: String = ""): Boolean {
        // F2：有 videoId 时以 videoId 为准（CDN 直链不含 id 也不会误判）。
        // videoId 为空时无法可靠定位任务，按「未下载」处理（保守），
        // 避免 contains("") 恒为 true 误判任意任务已下载（原 P1-2 缺陷）。
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
        // P1-2 修复：videoId 为空时无法可靠定位任务，按「未下载中」处理（保守）。
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
        // P1-2 修复：videoId 为空时无法可靠定位任务，返回 null（保守）。
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
        try {
            val intent = Intent().apply {
                setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, status.name)
            }
            // D5：首次（服务尚未进入前台）用 startForegroundService 确保进入前台；
            // 后续进度更新改用 startService，避免 Android 12+ 反复 startForegroundService 的时序竞争。
            // P2-6：compareAndSet 保证「首次」判定与置位的原子性，并发首包不会重复 startForegroundService。
            val firstTime = downloadServiceStarted.compareAndSet(false, true)
            if (firstTime) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            // 收到终态且已无活动任务时复位标记，允许下次新下载重新走 startForegroundService
            // （服务可能已停止，需重新拉起前台）。
            if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED) {
                val stillActive = taskMap.values.any {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                if (!stillActive) downloadServiceStarted.set(false)
            }
        } catch (e: SecurityException) {
            // 忽略转发异常，避免影响下载主流程
        }
    }

    // C3：将异常按类型细分为可读的失败原因，便于 UI 展示
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

    // C3：将 HTTP 状态码细分为可读的失败原因
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
         * O8：根据分块大小选择读取/写入缓冲（256KB–1MB）。
         * 大分块代表吞吐受限场景，放大缓冲可显著减少 read/write 系统调用次数，
         * 降低 CPU 与磁盘 I/O 占比（对齐报告 O8 建议）。
         */
        private fun chooseBufferSize(chunkBytes: Long): Int {
            return when {
                chunkBytes >= 16 * 1024 * 1024L -> BUFFER_SIZE_LARGE   // ≥16MB 块用 1MB 缓冲
                chunkBytes >= 8 * 1024 * 1024L -> BUFFER_SIZE_MEDIUM  // ≥8MB 块用 512KB 缓冲
                else -> CHUNK_BUFFER_SIZE                            // 默认 256KB
            }
        }

        // O4：分块完成位图表（sidecar 文件）的读写，格式：首行 chunkCount，次行 0/1 位图
    private fun writePartmap(file: File, chunkCount: Int, done: BooleanArray) {
        runCatching {
            file.writeText("$chunkCount\n${done.joinToString("") { if (it) "1" else "0" }}")
        }
    }

    private fun readPartmap(file: File): Pair<Int, BooleanArray?> {
        return runCatching {
            val lines = file.readLines()
            val cc = lines.getOrNull(0)?.toIntOrNull() ?: return@runCatching (0 to null)
            val bits = lines.getOrNull(1) ?: return@runCatching (0 to null)
            val arr = BooleanArray(cc) { i -> bits.getOrNull(i) == '1' }
            cc to arr
        }.getOrDefault(0 to null)
    }

    private companion object {
        const val MAX_CONCURRENT = 5
        const val PROGRESS_PERSIST_INTERVAL_MS = 3000L
        const val DOWNLOAD_SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
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

        const val SLOW_THRESHOLD_BPS = 50 * 1024L   // 单块速率低于 50 KB/s 视为慢块
        const val SLOW_SAMPLE_MS = 2000L            // 逐块吞吐采样周期 2s
        const val SLOW_DURATION_MS = 15000L         // 持续低于阈值 15s 才驱逐，避免抖动误杀
        const val SLOW_GRACE_MS = 8000L             // 新块起步宽限期，期间 0 字节不判慢
        const val MAX_CHUNK_ATTEMPTS = 5            // 单块最大重试/驱逐次数，超限判定整体失败
        const val BUFFER_SIZE_LARGE = 1_048_576     // ≥16MB 分块用 1MB 缓冲
        const val BUFFER_SIZE_MEDIUM = 512 * 1024   // ≥8MB 分块用 512KB 缓冲
        const val MAX_CHUNKS = 8                       // O3：单任务分块硬上限（结合 MAX_TOTAL_CONNECTIONS=8）
        const val MAX_TOTAL_CONNECTIONS = 8
        const val CHUNK_SIZE = 4_000_000L              // O2：最小分块 4MB，对齐 CDN 4MB/8MB 缓存分片
        const val MIN_CHUNK_TOTAL_BYTES = 12_000_000L // O2：文件 <12MB 不分块，直接单连接
        const val CHUNK_BUFFER_SIZE = 256 * 1024       // O2/O8：分块读取缓冲 256KB
        const val SINGLE_BUFFER_SIZE = 256 * 1024      // O2/O8：单连接读取缓冲 256KB
        const val DOWNLOAD_UA = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36"
        const val DOWNLOAD_REFERER = "https://hanimeone.me/"
    }
}