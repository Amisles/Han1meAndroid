package app.amisles.hanime.data.download

import android.content.Context
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.local.entity.DownloadEntity
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.extension.redactUrlForLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.ConnectionPool
import okhttp3.Protocol
import okhttp3.Call
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import android.content.Intent
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {

    // maxRequestsPerHost 只对异步调用生效：OkHttp 仅对 readyAsyncCalls 限流，本项目下载全部走
    // 同步 call.execute()，因此该配置不构成并发兜底，真正的全局上限由 connectionPermits 承担。
    internal val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .connectionPool(ConnectionPool(maxIdleConnections = MAX_IDLE_CONNECTIONS, keepAliveDuration = KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))
        // 分块 Range 下载强制 HTTP/1.1：h2 下同一主机的多条流共用一条 TCP 连接，一次丢包会让所有
        // 分块同时归零（队头阻塞），且部分 CDN 限制单连接并发流数
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    // 任务权威存储：Map 提供 O(1) 按 id 查找
    private val taskMap = ConcurrentHashMap<Int, DownloadTask>()

    // 由 taskMap 派生 UI 列表快照
    private fun emitTasks() {
        _tasks.value = taskMap.values.sortedByDescending { it.id }
    }

    // 纯进度刷新仅就地替换对应元素，避免每次 500ms 都全量重建列表；结构变化仍走 emitTasks()
    private fun updateTaskInList(newTask: DownloadTask) {
        val cur = _tasks.value
        val idx = cur.indexOfFirst { it.id == newTask.id }
        if (idx < 0) { emitTasks(); return }
        if (cur[idx] === newTask) return
        _tasks.value = cur.toMutableList().also { it[idx] = newTask }
    }

    var onProgressUpdate: ((taskId: Int, title: String, progress: Int, status: DownloadStatus) -> Unit)? = null

    private val downloadJobs = ConcurrentHashMap<Int, Job>()
    // 在途 Call 跟踪。用 ConcurrentLinkedQueue 而非 CopyOnWriteArrayList：后者每次 add 都复制
    // 整个底层数组，而分块路径每块、每次重连都会 trackCall，累积过程为 O(n²)；且已完成的 Call
    // 不主动移除，单个大文件任务可堆积上千个已关闭对象（块数上限 MAX_PARTMAP_CHUNKS = 4096）。
    private val taskCalls = ConcurrentHashMap<Int, ConcurrentLinkedQueue<Call>>()
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
    // 各任务最近一次已转发的整数百分比，用于抑制「百分比未变」的重复跨进程通知
    private val lastNotifiedPercent = ConcurrentHashMap<Int, Int>()

    /**
     * 本进程内已判定「分块无收益」的文件（按 filePath）。
     *
     * 驱逐预算耗尽降级单连接后记入，使后续暂停/恢复不再重新进入分块路径。
     * 只记内存不落盘，进程重启后允许再试一次分块，避免一次误判导致永久降级。
     */
    internal val singleConnectionFiles: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * 全局连接预算（硬上限）。下载全部走同步 call.execute()，OkHttp Dispatcher 限流对其无效，
     * 若不自行兜底，在途 TCP 连接会过多，在移动链路上互相抢带宽并加剧丢包。
     * 用 kotlinx 的 Semaphore：acquire 是挂起点，协程被取消时能立即退出。
     */
    internal val connectionPermits = Semaphore(MAX_CONCURRENT_CONNECTIONS)

    /**
     * 落盘串行化队列。原实现「即发即忘」的 upsert 不保证完成顺序，连续状态切换时可能
     * 先发起的 UPSERT 后落库而覆盖新状态、DELETE 与残留 UPSERT 竞争导致任务复活。
     * 改为单消费者 Channel：严格 FIFO，提交顺序即写库顺序（提交点均在 tasksLock 内）。
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

    /** 当前网络类型；供拆分出去的下载引擎判定并行连接数。 */
    internal fun currentNetworkClass(): NetworkClass = getCurrentNetworkClass(context)

    /** 任务已持久化的总字节数（未知时为 0）；供拆分出去的单连接引擎做源文件变更判定。 */
    internal fun totalBytesOf(taskId: Int): Long = taskMap[taskId]?.totalBytes ?: 0L

    /**
     * 解析实际下载目录：空自定义路径 → 默认目录；非空自定义路径需可写，否则回退默认。
     * 每次新建下载时实时读取 Preferences，使设置页变更立即对后续新下载生效。
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
                // DB 里的 COMPLETED 不代表文件还在（用户清理目录 / 卸载重装 / 存储卡移除）。
                // 不校验会让「已完成」条目点播放黑屏，且 isVideoDownloaded 会禁止重新下载。
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
                // 避免进程重启后这些任务永久挂起、只能手动恢复。
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
     *         [RESULT_ALREADY_ACTIVE] 同一文件已有进行中/等待中的任务；
     *         [RESULT_ALREADY_COMPLETED] 同一文件已下载完成。
     */
    fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String = "",
        videoId: String = ""
    ): Int {
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            // 脱敏：直链带时效签名，落盘后等同于泄露访问凭据
            AppLogger.logError("DownloadManager", "拒绝下载：非法 url=\"${url.redactUrlForLog()}\" (title=$title, videoId=$videoId)")
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
            // 去重必须按「文件身份」而非 (url, quality)：downloadUrl 是带时效签名的 CDN 直链，
            // 重新拉取画质页会得到不同 url；而落盘路径由 (title, quality, videoId) 决定。若按不按
            // filePath 去重，会并存两个 filePath 相同的任务并发写同一文件，删除任一条都会误删共享文件。
            val existingTask = taskMap.values.find { it.filePath == filePath }
            if (existingTask != null) {
                AppLogger.log("DownloadManager", "该文件已有下载任务，复用 taskId=${existingTask.id}: $title ($quality)")
                return when (existingTask.status) {
                    // 暂停/失败：复用即续传，保持「点下载 = 继续」的既有语义
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
     * 以 CAS 原子获取「软并发槽位」，将判定与占位合为一个原子操作，
     * 避免 check-then-act 在并发/批量启动时突破并发上限。
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
     * 从磁盘残留推导续传起点，返回 (单连接续传字节数, 分块续传位图)。
     * 槽位满退回 PENDING 后须重新推导，否则会以「首下」语义整文件重下；
     * resumeDownloadInternal 复用此函数，保证两条路径判定一致。
     */
    private fun deriveResumeState(task: DownloadTask): Pair<Long, BooleanArray?> {
        val file = File(task.filePath)
        val partmapFile = File(task.filePath + PARTMAP_SUFFIX)
        // writePartmap 的中间文件：它的存在本身就是「上次位图表替换未完成」的证据
        val partmapTmpFile = File(task.filePath + PARTMAP_SUFFIX + ".tmp")
        if (partmapFile.exists() && file.exists()) {
            val (cc, bitmap) = readPartmap(partmapFile)
            if (cc > 0 && bitmap != null) {
                // 位图表有效：顺手清理上次替换失败残留的 .tmp，避免孤儿文件堆积
                if (partmapTmpFile.exists()) runCatching { partmapTmpFile.delete() }
                // 位图表只是「上次写入时的意图记录」，与文件实际长度可能不一致。只有「该块区间末尾
                // 字节确实落在当前文件长度内」才承认完成，否则清位重下，避免「位图说完成、数据不在」
                // 导致收尾校验恒失败且无法自愈。
                val sanitized = sanitizePartmap(task, file, cc, bitmap)
                // 该文件已被判定分块无收益时，直接按连续完成前缀做单连接续传（缓存避免周期性震荡）
                if (singleConnectionFiles.contains(task.filePath)) {
                    val prefix = contiguousDoneBytes(sanitized, cc, task.totalBytes)
                    AppLogger.log(
                        "DownloadManager",
                        "该文件已判定分块无收益，继续单连接续传: prefix=$prefix / total=${task.totalBytes}"
                    )
                    return prefix to null
                }
                return 0L to sanitized
            }
        }
        // 位图表替换被中断：.partmap 缺失但 .partmap.tmp 残留，说明分块进度已不可信。
        // 分块写入是稀疏的（file.length() 只是最大已写偏移，其下可能有大片空洞），若退化为按
        // file.length() 做单连接续传，空洞永远不会被回填，而收尾长度校验仍会通过，最终产出
        // 「能播但花屏」的损坏文件。故此处必须丢弃进度、整份重下。
        if (!partmapFile.exists() && partmapTmpFile.exists()) {
            AppLogger.logError(
                "DownloadManager",
                "位图表替换被中断（残留 ${partmapTmpFile.name}），分块进度不可信，丢弃后整份重下: ${task.title}"
            )
            runCatching { partmapTmpFile.delete() }
            if (file.exists()) runCatching { file.delete() }
            return 0L to null
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
     * 内部方法，调用者须持有 tasksLock。统一负责 activeSlots 的 CAS 占位与终态/取消时的 -1，
     * 结束后调度下一个 PENDING 任务。
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
                    // 源文件已变，旧位图表/旧文件已无意义，丢弃后整份重下。
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
                    // totalBytes <= 0 表示本次一个字节都没落地（例如服务端对失效直链返回空 200）。
                    // 原判据在 totalBytes 未知时直接放行为 COMPLETED，会把空文件标成「已完成」；
                    // 且这正是「首个转发事件即终态」的成因之一（全程无进度回调，收尾直接发终态），
                    // 故一并收紧：未知总量不再视为成功。
                    if (t.totalBytes <= 0 || t.downloadedBytes < t.totalBytes || onDisk < t.totalBytes) {
                        AppLogger.logError(
                            "DownloadManager",
                            "完整性校验失败 ${task.title}: ${t.downloadedBytes}/${t.totalBytes} (磁盘 $onDisk)"
                        )
                        t.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = if (t.totalBytes <= 0) {
                                "下载失败：未收到任何数据"
                            } else {
                                "下载不完整（${t.downloadedBytes}/${t.totalBytes} 字节）"
                            }
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
        // 并发门控只有 activeSlots + concurrencyLimit 一套
        concurrencyLimit = safeMax
        AppLogger.log("DownloadManager", "并发下载数已更新为: $safeMax")

        tasksLock.withLock {
            val pendingTasks = taskMap.values.filter {
                it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id)
            }
            pendingTasks.forEach { task ->
                if (activeSlots.get() < concurrencyLimit) {
                    // 启动前重新推导续传起点
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
        // 权威判定：该文件在本进程内已被判定分块无收益时，一律走单连接。deriveResumeState 给出的是
        // 连续完成前缀作为续传起点，这里兜住前缀为 0 的边界，否则 resumeBytes=0 会绕过记忆重新走分块。
        if (singleConnectionFiles.contains(filePath)) {
            downloadFileSingle(taskId, url, filePath, resumeBytes)
            return
        }
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
     * 计算单任务允许的并行连接数上限，使「并发任务数 × 每任务连接数」不超过全局软预算
     * [MAX_TOTAL_CONNECTIONS]。软预算只是启发式，硬上限由 [connectionPermits] 保证。
     */
    internal fun effectiveParallelismCap(): Int {
        val active = maxOf(activeSlots.get(), 1)
        // 整数除法在任务数接近上限时会把每任务连接数压到 1，故给每任务一个保底值，
        // 超出软预算的部分由全局信号量兜住
        return maxOf(MIN_PARALLEL_PER_TASK, MAX_TOTAL_CONNECTIONS / active)
    }

    /**
     * 原子地更新单个任务。使用 tasksLock 保护读-改-写操作。
     */
    internal fun updateTask(taskId: Int, updater: (DownloadTask) -> DownloadTask) {
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
            // 通知转发节流：reporter 每 500ms 触发一次 updateTask，若每次转发即约 10 次/秒的 binder
            // 调用 + 通知重绘，而整数百分比多数未变。状态切换始终转发，保证通知语义不丢。
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
    internal fun trackCall(taskId: Int, call: Call) {
        taskCalls.computeIfAbsent(taskId) { ConcurrentLinkedQueue() }.add(call)
        if (taskCancelFlags[taskId]?.get() == true) {
            runCatching { call.cancel() }
        }
    }

    /**
     * 连接结束后解除跟踪，使 [taskCalls] 只保留「在途连接」。
     *
     * 分块路径每块、每次重连都会 [trackCall]，不在此移除会让列表随块数与重试次数无界增长
     * （块数上限 4096），既占内存又使 cancelTaskCalls 的遍历开销随历史累积量放大。
     * 探测与单连接路径每个任务最多各产生 1~2 个 Call，随任务结束统一清理，故不单独解除。
     */
    internal fun untrackCall(taskId: Int, call: Call) {
        taskCalls[taskId]?.remove(call)
    }

    private fun cancelTaskCalls(taskId: Int) {
        // 顺序重要：必须「先置位、后排空」，见 trackCall 的正确性论证
        taskCancelFlags.computeIfAbsent(taskId) { AtomicBoolean(false) }.set(true)
        taskCalls.remove(taskId)?.forEach { runCatching { it.cancel() } }
    }

    /**
     * 任务是否正在被暂停/取消撕销。
     *
     * 配合「先中断在途请求、再取消协程」的顺序：该窗口内协程 isActive 仍为 true，
     * 若仅凭 isActive 判定，分块 worker 会把被主动取消的 Call 误判为瞬断而反复重试，
     * 重试耗尽后把一次暂停升级成 FAILED。
     */
    internal fun isTaskCancelled(taskId: Int): Boolean = taskCancelFlags[taskId]?.get() == true

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
     * 一键重试全部失败任务。仅对 FAILED 状态任务触发续传，复用 resumeDownloadInternal
     * （含续传起点推导与并发槽位门控），受并发上限约束依次进入调度。
     * 调用方需持有 tasksLock，resumeDownloadInternal 不再重复取锁。
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
                // 一并清理分块续传位图表与其 .tmp 中间文件，避免孤儿文件
                val pm = File(it.filePath + PARTMAP_SUFFIX)
                if (pm.exists()) pm.delete()
                val pmTmp = File(it.filePath + PARTMAP_SUFFIX + ".tmp")
                if (pmTmp.exists()) pmTmp.delete()
            }
            taskMap.remove(taskId)
            emitTasks()
        }
        // 走与 persistTask 相同的串行队列，避免 DELETE 与在途 UPSERT 竞争导致任务复活
        persistDelete(taskId)
        lastNotifiedPercent.remove(taskId)
        // 任务记录已删除，无法再走 updateTask 进度通道；必须显式通知前台服务撤销该任务的
        // 常驻通知并重算活动集合，否则通知（ongoing 不可划掉）与 dataSync 前台服务会永久残留。
        forwardRemoveToService(taskId)
        AppLogger.log("DownloadManager", "Download cancelled: $taskId")
    }

    /**
     * 显式生命周期出口。单例随进程存在是预期行为，但提供结构化取消入口：进程退出、测试拆卸、
     * 或需要强制中断所有在途下载时调用，保证协程与在途请求被确定性回收。
     * scope 根使用 SupervisorJob，单个下载任务的异常不会株连其它在途任务。
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
            downloadServiceStarted.set(false)
            runCatching { context.startForegroundService(intent) }.onFailure { se ->
                AppLogger.log(
                    "DownloadManager",
                    "转发进度到 DownloadService 被系统拒绝: ${e.message} / ${se.message}"
                )
            }
        } catch (e: SecurityException) {
            // 权限受限（通知 / 前台服务类型）：同样复位标志，允许下次重新尝试
            downloadServiceStarted.set(false)
        }
        // 收到终态/暂停且已无活动任务时复位标记，允许下次新下载重新走 startForegroundService。
        // 服务在「无活动任务」时会退出前台并 stopSelf，此处必须复位，否则后续恢复下载会走
        // startService（服务已不在前台，后台启动会被系统拒绝），导致该次下载完全没有通知。
        if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED ||
            status == DownloadStatus.PAUSED
        ) {
            if (!hasActiveDownload()) downloadServiceStarted.set(false)
        }
    }

    /**
     * 通知 DownloadService 撤销某个已被取消/删除任务的通知。
     *
     * cancelDownload 会直接删掉任务记录（不再产生任何 updateTask 回调），因此必须走独立通道。
     * 用 startService 而非 startForegroundService：这只是清理一个已存在的通知，不承担前台义务。
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

    /**
     * 对外只暴露 [startDownload] 的三个结果码。
     * 调参常量已移至 `DownloadTuning.kt`（包级 internal），此处只保留 UI 层需要读取的公开结果码。
     */
    companion object {
        /**
         * [startDownload] 的结果码。负数均表示「未新建任务」，供 UI 层区分提示文案：
         * - [RESULT_INVALID_URL]：直链非法或路径越界；
         * - [RESULT_ALREADY_ACTIVE]：同一文件已有进行中 / 等待中的任务；
         * - [RESULT_ALREADY_COMPLETED]：同一文件已下载完成。
         */
        const val RESULT_INVALID_URL = -1
        const val RESULT_ALREADY_ACTIVE = -2
        const val RESULT_ALREADY_COMPLETED = -3
    }
}
