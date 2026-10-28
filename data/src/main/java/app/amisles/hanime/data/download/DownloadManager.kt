package app.amisles.hanime.data.download

import android.content.Context
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.DownloadGateState
import app.amisles.hanime.domain.model.DownloadStorageInfo
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.ConnectionPool
import okhttp3.Protocol
import okhttp3.Call
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.database.sqlite.SQLiteException
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) : app.amisles.hanime.domain.repository.DownloadRepository {

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
    override val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

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

    /**
     * 每个任务最近一次「暂停」的序号（单调递增）。
     *
     * 调度决定与真正开跑之间隔着锁外的续传点推导（磁盘 IO），这段窗口里用户完全可能按下暂停。
     * 任务处于 PENDING 时复核 status 即可发现；但从 PAUSED 恢复时「再次暂停」不改变 status
     * （本来就是 PAUSED），status 复核失效，故用序号记录「暂停意图」：调度前取号、开跑前比对，
     * 序号变了就说明期间有过暂停，放弃启动。[pauseAll] 会给所有任务取号，使「暂停全部」同样能
     * 打断正在进行的批量恢复。
     */
    private val pauseEpoch = ConcurrentHashMap<Int, AtomicLong>()
    private val taskIdCounter = AtomicInteger(0)

    /** 备份导入等外部写入会占用任意 id；记录已占用的下界，新建任务的 id 一律跳过该区间。 */
    @Volatile
    private var reservedIdFloor: Int = 0
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

    /** 网络监听是否已注册；DownloadManager 是进程级单例，重复注册会收到重复回调。 */
    @Volatile
    private var networkCallbackRegistered = false

    /** 电源状态监听是否已注册；与 [networkCallbackRegistered] 同理，只注册一次。 */
    @Volatile
    private var gateReceiverRegistered = false

    /** 持有引用：动态注册的接收器由系统持有，这里再存一份避免被优化掉。 */
    private var gateReceiver: BroadcastReceiver? = null

    /**
     * 因断网而被自动暂停的任务 id。只有这些任务才会在网络恢复后自动续传 ——
     * 用户手动点暂停的任务必须保持暂停，否则「暂停省个流量」的意图会被下一次网络切换打断。
     */
    private val autoPausedByNetwork: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    /**
     * 落盘合并队列：按 taskId 只保留最新快照，删除以哨兵标记（覆盖同 id 的在途 upsert）。
     *
     * 原来是 UNLIMITED 的每条队列：进度每 500ms 一条、状态切换一条，批量添加 100 个任务时
     * 队列可积压上千份完整快照（标题、URL、路径都在内），既是内存压力也让数据库反复重写同一行。
     * 改为「id → 最新状态」后，无论产生多少条中间态，一个任务最终只落一次盘。
     */
    private val pendingPersist = ConcurrentHashMap<Int, DownloadTask>()
    private val pendingDelete: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val persistSignal = Channel<Unit>(Channel.CONFLATED)
    /** 默认下载目录：公共 Download/Hanime（见 [resolveDefaultDownloadDir]），卸载应用不会被清掉。 */
    private val defaultDownloadDir: File by lazy {
        resolveDefaultDownloadDir(context.getExternalFilesDir(null))
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

    @Volatile
    private var queueOrder: List<Int> = Preferences.downloadQueueOrder

    private val _gateState = MutableStateFlow(DownloadGateState.OPEN)
    /** 调度闸门状态。任一条件不满足则新任务不启动，已运行的任务不受影响。 */
    override val gateState: StateFlow<DownloadGateState> = _gateState.asStateFlow()

    init {
        onProgressUpdate = { taskId, title, progress, status ->
            forwardToService(context, title, progress, status, taskId)
        }

        scope.launch {
            for (signal in persistSignal) {
                runCatching {
                    // 一次性取走当前全部待写项：期间的中间态已在上面的 map 里被覆盖，
                    // 因此这里拿到的就是每个任务的最终状态，写一次即正确。
                    val deletedIds = pendingDelete.toList().also { pendingDelete.removeAll(it.toSet()) }
                    val batch = pendingPersist.values.toList().also { items ->
                        // 逐个 remove 而非 clear：消费期间新产生的快照不能被误删
                        items.forEach { pendingPersist.remove(it.id) }
                    }
                    deletedIds.forEach { id ->
                        val stillPending = pendingDelete.contains(id)
                        if (!stillPending) downloadDao.deleteDownload(id)
                    }
                    if (batch.isNotEmpty()) {
                        // 批量 upsert 在单事务内完成，避免 N 次独立 fsync
                        downloadDao.upsertAll(batch.map { it.toEntity() })
                    }
                }.onFailure { e ->
                    AppLogger.logError("DownloadManager", "Failed to persist batch: " + e.message, e)
                }
            }
        }

        scope.launch {
            Preferences.maxDownloadConcurrentFlow.collect { updateConcurrencyLimit(it) }
        }

        scope.launch {
            Preferences.downloadRateLimitFlow.collect { megabytesPerSecond ->
                GlobalDownloadRateLimiter.instance.setLimit(megabytesPerSecond.toLong() * 1024L * 1024L)
            }
        }

        scope.launch {
            combine(
                Preferences.downloadWifiOnlyFlow,
                Preferences.downloadChargingOnlyFlow,
                Preferences.downloadNightOnlyFlow
            ) { wifi, charging, night -> Triple(wifi, charging, night) }
                .collect {
                    if (refreshGateState().allowed) startNextPendingTask()
                }
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
                adoptOrphanFiles()
                AppLogger.log("DownloadManager", "Restored ${restoredTasks.size} tasks from DB, taskIdCounter=$taskIdCounter")
                val interruptedIds = entities.filter {
                    it.status == DownloadStatus.DOWNLOADING.name
                }.map { it.id }
                interruptedIds.forEach { resumeDownloadInternal(it) }
                // 避免进程重启后这些任务永久挂起、只能手动恢复。
                startNextPendingTask()
            } catch (e: SQLiteException) {
                AppLogger.logError("DownloadManager", "Failed to restore tasks: ${e.message}", e)
            }
        }

        registerNetworkObserver()
        registerGateObservers()
    }

    /**
     * 把下载目录里「有文件、无任务」的成品视频登记为已完成任务。
     *
     * 卸载重装后数据库随应用数据一起被清除，但 Download/Hanime 是公共目录、文件仍在。
     * 不重建任务的话，这些文件既不出现在下载页，也会被当成孤儿数据。
     */
    /**
     * 目录直读为空时改走 MediaStore。
     *
     * 分区存储下本应用看不到「上一个安装」写入的文件，但 MediaStore 仍为它们建了索引；
     * 这是卸载重装后能认出历史下载的唯一途径。
     */
    private fun scanViaMediaStore(dir: File, knownNames: Set<String>): List<DiscoveredDownload> {
        val videos = MediaStoreVideoScanner.queryDownloads(context, DEFAULT_DOWNLOAD_SUBDIR)
        return videos
            .filter { it.displayName !in knownNames }
            .mapNotNull { item ->
                val parsed = DownloadStorageInspector.parseDownloadedFileName(item.displayName)
                    ?: return@mapNotNull null
                DiscoveredDownload(
                    videoId = parsed.first,
                    title = parsed.second,
                    quality = parsed.third,
                    filePath = item.absolutePath,
                    totalBytes = item.size
                )
            }
    }
    private fun adoptOrphanFiles(): Int {
        val dir = resolveDownloadDir()
        val known = tasksLock.withLock { taskMap.values.mapTo(mutableSetOf()) { File(it.filePath).name } }
        val discovered = DownloadStorageInspector
            .scanDownloadedFiles(dir, known)
            .ifEmpty { scanViaMediaStore(dir, known) }
        if (discovered.isEmpty()) return 0
        val adopted = mutableListOf<DownloadTask>()
        tasksLock.withLock {
            discovered.forEach { item ->
                val nextId = nextTaskId()
                val task = DownloadTask(
                    id = nextId,
                    title = item.title,
                    quality = item.quality,
                    url = "",
                    totalBytes = item.totalBytes,
                    downloadedBytes = item.totalBytes,
                    status = DownloadStatus.COMPLETED,
                    filePath = item.filePath,
                    videoId = item.videoId
                )
                taskMap[nextId] = task
                adopted += task
            }
            emitTasks()
        }
        adopted.forEach { persistTask(it) }
        AppLogger.log("DownloadManager", "Adopted ${adopted.size} existing files from ${dir.absolutePath}")
        return adopted.size
    }

    /**
     * 新建下载任务。
     *
     * @return 成功时为任务 id；[RESULT_INVALID_URL] 直链非法或路径越界；
     *         [RESULT_ALREADY_ACTIVE] 同一文件已有进行中/等待中的任务；
     *         [RESULT_ALREADY_COMPLETED] 同一文件已下载完成。
     */
    override fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String,
        videoId: String
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

        // 需要「锁外推导续传状态」的既有任务；非空表示本方法复用既有任务而非新建
        var deferredResumeTask: DownloadTask? = null
        var createdTaskId: Int? = null

        tasksLock.withLock {
            // 去重必须按「文件身份」而非 (url, quality)：downloadUrl 是带时效签名的 CDN 直链，
            // 重新拉取画质页会得到不同 url；而落盘路径由 (title, quality, videoId) 决定。若按不按
            // filePath 去重，会并存两个 filePath 相同的任务并发写同一文件，删除任一条都会误删共享文件。
            val existingTask = taskMap.values.find { it.filePath == filePath }
            if (existingTask != null) {
                AppLogger.log("DownloadManager", "该文件已有下载任务，复用 taskId=${existingTask.id}: $title ($quality)")
                when (existingTask.status) {
                    // 暂停/失败：复用即续传，保持「点下载 = 继续」的既有语义。
                    // 续传推导含磁盘 IO，不能在前面的锁内做，故记下来移到锁外执行。
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> deferredResumeTask = existingTask
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> return RESULT_ALREADY_ACTIVE
                    DownloadStatus.COMPLETED -> return RESULT_ALREADY_COMPLETED
                }
                // 非局部 return 与 return@withLock 混用容易写错：这里只退出锁块，真正的启动在锁外
                return@withLock
            }

            val taskId = nextTaskId()

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

            // 新建任务此刻必然是 PENDING；若开跑前被暂停，启动复核会放弃
            startDownloadWithConcurrencyInternal(task, expectedStatus = DownloadStatus.PENDING)
            createdTaskId = taskId
        }

        deferredResumeTask?.let { task ->
            // 先取「暂停序号」再做磁盘 IO：推导期间用户若按了暂停，序号会变，启动复核随即放弃本次调度
            val epochAtSchedule = pauseEpochOf(task.id)
            val (rb, rm) = deriveResumeState(task)
            tasksLock.withLock {
                startDownloadWithConcurrencyInternal(
                    task,
                    rb,
                    rm,
                    expectedStatus = task.status,
                    scheduledPauseEpoch = epochAtSchedule
                )
            }
            return task.id
        }
        return createdTaskId ?: RESULT_INVALID_URL
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
     * 分块下载标记文件（存放在应用私有目录，与公共目录里的位图表分开）。
     *
     * 位图表在公共 Download 目录，用户清理垃圾文件、或「先删位图再删文件」的清理动作被中断，
     * 都会留下「无位图表、但有稀疏成品」的状态。此时按 file.length() 做单连接续传会把空洞
     * 永久留在文件中间，而收尾只比对长度 → 静默产出能播但花屏的坏文件。标记放在私有目录，
     * 即使位图表被外部删掉、进程重启，也仍能判定「这个文件曾被分块写过，长度不可信」。
     */
    private fun chunkedMarkerFile(taskId: Int) =
        File(File(context.filesDir, CHUNKED_MARKER_DIR), taskId.toString())

    /** 分块写入开始前打标记，成功后清除（见 [chunkedMarkerFile]）。 */
    internal fun markChunkedFile(taskId: Int) {
        runCatching {
            val marker = chunkedMarkerFile(taskId)
            marker.parentFile?.mkdirs()
            marker.writeText("")
        }.onFailure { AppLogger.logError("DownloadManager", "写分块标记失败: " + it.message, it) }
    }

    internal fun clearChunkedMark(taskId: Int) {
        runCatching { chunkedMarkerFile(taskId).delete() }
    }

    private fun hasChunkedMark(taskId: Int): Boolean =
        runCatching { chunkedMarkerFile(taskId).exists() }.getOrDefault(false)

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
                // 该文件已被判定分块无收益时，本应改走单连接续传。但分块写入是稀疏的：
                // contiguousDoneBytes 只保证「位图中的逻辑前缀连续」，且 sanitizePartmap 只校验
                // 各块末尾字节是否落在 file.length() 内 —— 文件中间仍可能有从未写入的空洞。
                // 若按前缀截断后单连接续写，空洞将永久保留，而收尾只比对文件长度，最终会得到
                // 「长度正确但中段是垃圾数据」的坏文件。故此处一律丢弃分块进度，整份重下。
                if (singleConnectionFiles.contains(task.filePath)) {
                    AppLogger.logError(
                        "DownloadManager",
                        "该文件曾判定分块无收益，但残留的是稀疏分块文件，不能直接单连接续传；丢弃后整份重下: " + task.title
                    )
                    // 先删成品再删位图：中途崩溃时残留的是「有位图、无成品」，下次仍走本分支；
                    // 反过来会留下「无位图、有稀疏成品」，正是下面要防的那条误判路径。
                    runCatching { file.delete() }
                    runCatching { partmapFile.delete() }
                    clearChunkedMark(task.id)
                    return 0L to null
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
            // 同样先删成品再删 .tmp：反过来的话，两步之间崩溃会留下「无位图、无 .tmp、有稀疏成品」，
            // 正好落进下面的单连接续传回退。删除后重下，标记同时失效。
            if (file.exists()) runCatching { file.delete() }
            runCatching { partmapTmpFile.delete() }
            clearChunkedMark(task.id)
            return 0L to null
        }
        // 回退：文件级续传（单连接）或整文件重下
        if (partmapFile.exists()) {
            if (file.exists()) runCatching { file.delete() }
            runCatching { partmapFile.delete() }
            clearChunkedMark(task.id)
            return 0L to null
        }

        // 位图表缺失、但本任务此前确实走过分块下载：成品可能是稀疏的（file.length() 只是最大
        // 已写偏移，其下可能有大片空洞）。按长度做单连接续传，空洞永远回填不了，而收尾只比对
        // 长度 → 静默产出「能播但花屏」的坏文件。标记在私有目录，不受公共目录里的删除动作影响。
        if (hasChunkedMark(task.id)) {
            AppLogger.logError(
                "DownloadManager",
                "位图表缺失但该文件曾分块写入，磁盘长度不可信，丢弃后整份重下: " + task.title
            )
            if (file.exists()) runCatching { file.delete() }
            clearChunkedMark(task.id)
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
     * 启动一个任务。
     *
     * 统一负责 activeSlots 的占位与释放：只有通过 [tryAcquireSoftSlot] 的调用才会进入 Job 作用域，
     * 也只有那里才会 -1，保证占位与释放成对；登记本身用 putIfAbsent 保证幂等。
     * 结束后调度下一个 PENDING 任务。
     *
     * @param expectedStatus 调用方在锁外推导续传点之前看到的任务状态。开跑前会复核一次：状态对不上
     *   说明期间被暂停/取消，放弃本次调度（占用的槽位由 Job 的 finally 归还）。null 表示不复核。
     * @param scheduledPauseEpoch 调度前的暂停序号，见 [pauseEpoch]。负数表示不校验。
     */
    private fun startDownloadWithConcurrencyInternal(
        task: DownloadTask,
        resumeBytes: Long = 0L,
        resumeChunkMap: BooleanArray? = null,
        expectedStatus: DownloadStatus? = null,
        scheduledPauseEpoch: Long = -1L
    ) {
        // 本方法会在多条路径上重入（startDownload / startNextPendingTask / updateConcurrencyLimit /
        // retryAllFailed）。收敛到同一把锁上，保证「检查 downloadJobs」与「占槽位再登记 job」成为
        // 一个原子步骤：否则并发重入会让同一任务登记两个 Job，前一个被覆盖后其 finally 的
        // activeSlots -1 就不再对应任何真实占位，计数被减成负数，并发上限永久失效。
        if (downloadJobs.containsKey(task.id)) return
        // 锁外推导续传点期间状态可能已变：这里先做一次廉价复核，避免白占一个槽位、白建一个 Job。
        // 真正的判定在 Job 体开头（与 updateTask(PAUSED) 共用 tasksLock，见那里的说明）。
        val snapshot = taskMap[task.id]
        if (snapshot == null ||
            (expectedStatus != null && snapshot.status != expectedStatus) ||
            (scheduledPauseEpoch >= 0L && pauseEpochOf(task.id) != scheduledPauseEpoch)
        ) {
            AppLogger.log(
                "DownloadManager",
                "调度已过期，跳过启动: ${task.title} (id=${task.id}, 期望 $expectedStatus, 实际 ${snapshot?.status})"
            )
            return
        }
        // 闸门关闭时不占槽位、不启动：任务保持 PENDING，由条件变化或网络恢复重新拾起
        val gate = refreshGateState()
        if (!gate.allowed) {
            if (task.status != DownloadStatus.PENDING) {
                updateTask(task.id) { it.copy(status = DownloadStatus.PENDING) }
            }
            // 通知栏说明等待原因，避免用户以为下载卡死。
            // 本方法在调用方的 tasksLock 内执行：forwardToService 是跨进程 IPC，
            // 不能同步跑在锁里（updateTask 已把同类转发移出锁外），这里挪到 IO 作用域异步转发。
            val gateNoticeTaskId = task.id
            val gateNoticeTitle = task.title
            val gateNoticeProgress = gateProgress(task)
            scope.launch {
                onProgressUpdate?.invoke(
                    gateNoticeTaskId,
                    gateNoticeTitle,
                    gateNoticeProgress,
                    DownloadStatus.PENDING
                )
            }
            return
        }
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
                // 唯一的状态认定点：从锁外推导续传点到这里隔着磁盘 IO，用户可能已经暂停。
                // 这里的复核与 updateTask(PAUSED) 共用 tasksLock，因此「暂停」与「开跑」严格有序：
                //   - 暂停先落库 → 复核不通过，本 Job 直接退出（槽位在 finally 归还）；
                //   - 暂停后到   → 此时 Job 已登记，pauseDownload 会 cancel 它，PAUSED 再覆盖状态。
                // 只靠锁外那次复核不够：对一个本来就 PAUSED 的任务「再次暂停」不改变 status，
                // 必须靠暂停序号识别。
                var started = false
                updateTask(task.id) { current ->
                    val statusMatches = expectedStatus == null || current.status == expectedStatus
                    val pauseUnchanged =
                        scheduledPauseEpoch < 0L || pauseEpochOf(task.id) == scheduledPauseEpoch
                    if (statusMatches && pauseUnchanged && !isTaskCancelled(task.id)) {
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

                AppLogger.log("DownloadManager", "开始下载: ${task.title} (并发槽位已获取, resumeFrom=$resumeBytes)")

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
                    if (t.totalBytes <= 0 || t.downloadedBytes != t.totalBytes || onDisk != t.totalBytes) {
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
                currentCoroutineContext()[Job]?.let { self -> downloadJobs.remove(task.id, self) }
                if (!downloadJobs.containsKey(task.id)) {
                    taskCalls.remove(task.id)
                    taskCancelFlags.remove(task.id)
                }
                // 进入此作用域必然已在上面通过 tryAcquireSoftSlot 占位，故此处释放是配对的。
                val remaining = activeSlots.decrementAndGet()
                if (remaining < 0) {
                    // 防御：计数理论上不应为负。一旦出现即说明存在占位/释放缺口，
                    // 记日志并纠正为 0，避免并发上限永久失效导致连接与内存失控。
                    AppLogger.logError("DownloadManager", "activeSlots 计数异常并已纠正: " + remaining)
                    activeSlots.set(0)
                }
                startNextPendingTask()
            }
        }

        // 占位与登记必须原子：并发重入一旦让两个 Job 落到同一 taskId，先登记的那个被覆盖，
        // 它的 finally 里的 -1 就不再对应任何真实占位，activeSlots 会被减成负数。
        // putIfAbsent(LAZY 且未启动的 Job) 是原子的：只有成功登记的一方才有权 start()。
        val existing = downloadJobs.putIfAbsent(task.id, job)
        if (existing != null) {
            // 已有 Job 占有该任务：归还刚拿到的槽位，本次调度作废
            activeSlots.decrementAndGet()
            AppLogger.log("DownloadManager", "任务 " + task.id + " 已在下载中，忽略重复调度")
            return
        }
        // 登记与 start 之间可能被取消：LAZY Job 一旦被取消，协程体不会执行，finally 也就不会归还
        // 槽位，这里必须补还，否则并发上限会永久少一格。
        if (isTaskCancelled(task.id)) {
            downloadJobs.remove(task.id, job)
            if (activeSlots.decrementAndGet() < 0) activeSlots.set(0)
            AppLogger.log("DownloadManager", "任务在登记后被取消，已归还并发槽位: " + task.id)
            return
        }
        job.start()
    }

    private fun gateProgress(task: DownloadTask): Int =
        if (task.totalBytes > 0) (task.downloadedBytes * 100 / task.totalBytes).toInt() else 0

    private fun startNextPendingTask() {
        // 闸门判定含跨进程读取（粘性广播 / ConnectivityManager），必须在锁外
        if (!refreshGateState().allowed) return
        // 只在锁内做内存判定（挑任务 + 快速失败），磁盘 I/O 全部移到锁外。
        // taskMap / downloadJobs 均为 ConcurrentHashMap，分散读取不会撕裂。
        val tried = HashSet<Int>()
        while (true) {
            val candidate = tasksLock.withLock { pickPendingTaskLocked(exclude = tried) } ?: return
            if (activeSlots.get() >= concurrencyLimit) return
            AppLogger.log("DownloadManager", "启动下一个等待任务: ${candidate.title}")
            // 暂停序号必须在磁盘 IO 之前取：推导期间用户按了暂停，序号就会变，启动复核随即放弃。
            // 否则会拿「旧的 PENDING 快照」把刚被暂停的任务重新拉起来（P2-5）。
            val epochAtSchedule = pauseEpochOf(candidate.id)
            // 锁外推导（含 .partmap 读取/删除、file.length()），不阻塞主线程 pause/cancel
            val (rb, rm) = deriveResumeState(candidate)
            val launched = tasksLock.withLock {
                startDownloadWithConcurrencyInternal(
                    candidate,
                    rb,
                    rm,
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
     * 锁内挑选下一个待启动任务（排除 [exclude]）。返回 null 表示没有可用任务。
     *
     * 只读 ConcurrentHashMap，不做任何磁盘 I/O —— 这是持锁执行的关键约束：
     * 持锁做 I/O 会让主线程上的 pauseDownload / cancelDownload / startDownload 长时间等锁。
     */
    private fun pickPendingTaskLocked(exclude: Set<Int>): DownloadTask? {
        val pending = taskMap.values.filter {
            it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id) && it.id !in exclude
        }
        if (pending.isEmpty()) return null
        // 队列顺序优先，未列入的自定义顺序按 id 升序（先加的排前面），保证行为可预期
        val order = queueOrder
        val pendingTask = pending.minWithOrNull(
            compareBy({ order.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }, { it.id })
        )
        return pendingTask?.takeIf { activeSlots.get() < concurrencyLimit }
    }

    override fun adoptDownloadIdCeiling(maxId: Int) {
        if (maxId <= 0) return
        while (true) {
            val current = taskIdCounter.get()
            if (current >= maxId) return
            if (taskIdCounter.compareAndSet(current, maxId)) {
                AppLogger.log("DownloadManager", "已采纳外部下载 id 上界: $maxId")
                return
            }
        }
    }

    override fun adoptDownloadIdFloor(minId: Int) {
        if (minId <= 0) return
        if (minId > reservedIdFloor) reservedIdFloor = minId
    }

    private fun nextTaskId(): Int {
        while (true) {
            val candidate = taskIdCounter.incrementAndGet()
            if (candidate < reservedIdFloor) {
                taskIdCounter.updateAndGet { if (it < reservedIdFloor) reservedIdFloor else it }
                continue
            }
            return candidate
        }
    }

    override fun moveToFront(taskId: Int) {
        val current = queueOrder.toMutableList()
        current.remove(taskId)
        current.add(0, taskId)
        queueOrder = current
        Preferences.setDownloadQueueOrder(current)
        // 只是把该任务提前，不打断已经在跑的任务
        val task = taskMap[taskId] ?: return
        if (task.status == DownloadStatus.PENDING) startNextPendingTask()
    }

    override fun pauseAll() {
        val active = tasksLock.withLock {
            taskMap.values.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
                .map { it.id }
        }
        // 对所有任务（含此刻仍为 PAUSED 的）取号：批量恢复正在逐个拉起时，「暂停全部」必须能
        // 打断那些还没轮到、但已经在 resumeAll 名单里的任务。
        taskMap.keys.forEach { bumpPauseEpoch(it) }
        // 逐个暂停时不再链式调度：否则「暂停 A → 链路拉起 B → 暂停 B」会白白启动再取消，
        // 在 Service.onTimeout（主线程）这种场景下还会放大停顿。统一在最后调度一次。
        active.forEach { pauseDownloadInternal(it, scheduleNext = false) }
        startNextPendingTask()
        AppLogger.log("DownloadManager", "pauseAll: 已暂停 " + active.size + " 个任务")
    }

    /**
     * 系统回收 dataSync 前台服务配额时的兜底暂停（由 DownloadService.onTimeout 调用）。
     *
     * 与用户主动「全部暂停」等价，单独命名只是为了在日志里能区分触发来源：前台服务被系统收回后
     * 进程里的协程并不会停，任务必须显式转 PAUSED，否则会失去前台豁免、随后被系统静默杀掉。
     */
    fun pauseAllForSystemQuota() {
        AppLogger.log("DownloadManager", "系统回收前台服务配额，暂停全部下载")
        pauseAll()
    }

    override fun resumeAll() {
        val paused = tasksLock.withLock {
            taskMap.values.filter { it.status == DownloadStatus.PAUSED }.map { it.id }
        }
        paused.forEach { id ->
            val task = taskMap[id] ?: return@forEach
            // 取号后再推导：期间用户若暂停（哪怕对象本来就是 PAUSED，status 不会变）或点了
            // 「全部暂停」，序号都会变，据此跳过，不会出现「暂停全部」之后仍在拉起任务的情况。
            val epochAtSchedule = pauseEpochOf(id)
            val (rb, rm) = deriveResumeState(task)
            tasksLock.withLock {
                startDownloadWithConcurrencyInternal(
                    task,
                    rb,
                    rm,
                    expectedStatus = DownloadStatus.PAUSED,
                    scheduledPauseEpoch = epochAtSchedule
                )
            }
        }
        AppLogger.log("DownloadManager", "resumeAll: 已恢复 " + paused.size + " 个任务")
    }

    override fun reevaluateGate() {
        if (refreshGateState().allowed) startNextPendingTask()
    }

    /** 因闸门阻塞的等待任务数；0 表示当前没有任务在等条件。 */
    override fun pendingCount(): Int = taskMap.values.count { it.status == DownloadStatus.PENDING }

    /** 重新求值并广播；返回本次结论。 */
    internal fun refreshGateState(): DownloadGateState {
        val state = DownloadGate.evaluate(
            context,
            Preferences.downloadWifiOnly,
            Preferences.downloadChargingOnly,
            Preferences.downloadNightOnly
        )
        _gateState.value = state
        return state
    }

    fun updateConcurrencyLimit(maxConcurrent: Int) {
        val safeMax = maxConcurrent.coerceIn(1, MAX_CONCURRENT)
        // 并发门控只有 activeSlots + concurrencyLimit 一套
        concurrencyLimit = safeMax
        AppLogger.log("DownloadManager", "并发下载数已更新为: $safeMax")

        // 锁内挑任务、锁外推导续传起点，避免批量启动时长时间持锁阻塞主线程
        while (activeSlots.get() < concurrencyLimit) {
            val candidate = tasksLock.withLock { pickPendingTaskLocked(exclude = emptySet()) }
                ?: break
            val epochAtSchedule = pauseEpochOf(candidate.id)
            val (rb, rm) = deriveResumeState(candidate)
            val launched = tasksLock.withLock {
                startDownloadWithConcurrencyInternal(
                    candidate,
                    rb,
                    rm,
                    expectedStatus = DownloadStatus.PENDING,
                    scheduledPauseEpoch = epochAtSchedule
                )
                downloadJobs.containsKey(candidate.id)
            }
            if (!launched) break
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
        // 已知总量时先做一次磁盘空间预检：空间不足立即失败，避免把 ENOSPC 当成可重试的网络
        // 错误反复退避重试（每次重试都会再失败），让用户以为在下载却毫无进展。
        val knownTotal = taskMap[taskId]?.totalBytes ?: 0L
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
        downloadFileChunked(taskId, url, filePath, probe.totalBytes, singleBps = probe.bps, resumeMap = null)
    }

    /** 读取任务已知的总字节数（未知时为 0），供引擎做空间预检。 */
    internal fun knownTotalBytes(taskId: Int): Long = taskMap[taskId]?.totalBytes ?: 0L

    /**
     * 磁盘空间预检：目标目录可用空间不足以完成 [needBytes] 时抛出 [NotEnoughSpaceException]。
     *
     * 保留 [SPACE_RESERVE_BYTES] 余量，避免写满整块分区后拖垮系统其它写入（日志、数据库）。
     * 仅对「已知总量」的下载生效；总量未知的流式下载无法预判，只能依赖写入失败时的错误归类。
     */
    internal fun requireFreeSpace(target: File, needBytes: Long) {
        if (needBytes <= 0L) return
        val dir = target.parentFile ?: return
        val available = runCatching { android.os.StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < needBytes + SPACE_RESERVE_BYTES) {
            throw NotEnoughSpaceException(
                "存储空间不足：需要 ${needBytes / 1024 / 1024}MB，可用 ${available / 1024 / 1024}MB"
            )
        }
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
        pendingDelete.remove(task.id)
        pendingPersist[task.id] = task
        // CONFLATED：队列里已有未消费信号时直接丢弃本次，合并效果等价但无内存积压
        persistSignal.trySend(Unit)
    }

    /** 提交一次删除。与 [persistTask] 共用同一队列，避免删除与在途更新竞争。 */
    private fun persistDelete(taskId: Int) {
        // 删除哨兵必须覆盖同 id 的在途 upsert，否则旧快照可能在删除之后才落库，任务「复活」
        pendingPersist.remove(taskId)
        pendingDelete.add(taskId)
        persistSignal.trySend(Unit)
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

    /** 读取任务当前的暂停序号；从未暂停过为 0。 */
    private fun pauseEpochOf(taskId: Int): Long = pauseEpoch[taskId]?.get() ?: 0L

    /** 记录一次「暂停意图」并返回新序号。 */
    private fun bumpPauseEpoch(taskId: Int): Long =
        pauseEpoch.computeIfAbsent(taskId) { AtomicLong() }.incrementAndGet()

    override fun pauseDownload(taskId: Int) = pauseDownloadInternal(taskId, scheduleNext = true)

    /**
     * @param scheduleNext 是否在暂停后立刻调度下一个 PENDING 任务。[pauseAll] 会传 false，
     *   把 N 次链式调度收敛成一次，避免「暂停 A 拉起 B、再暂停 B」的无谓启动/取消。
     */
    private fun pauseDownloadInternal(taskId: Int, scheduleNext: Boolean) {
        // 先记暂停意图：调度链可能在几毫秒后才发现这次暂停（见 [pauseEpoch] 的说明）
        bumpPauseEpoch(taskId)
        // 用户/系统主动暂停时移出自动暂停集合：这不是断网触发的，不应在网络恢复后被自动拉起
        autoPausedByNetwork.remove(taskId)

        cancelTaskCalls(taskId)
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        // 复用 updateTask：状态切换（→PAUSED）会立即落盘
        updateTask(taskId) { it.copy(status = DownloadStatus.PAUSED) }
        // 避免空出的槽位不拾起 PENDING 任务导致其余任务永久挂起。
        if (scheduleNext) startNextPendingTask()
        AppLogger.log("DownloadManager", "Download paused: $taskId")
    }

    override fun resumeDownload(taskId: Int) {
        tasksLock.withLock {
            resumeDownloadInternal(taskId)
        }
    }

    /**
     * 一键重试全部失败任务。仅对 FAILED 状态任务触发续传，复用 resumeDownloadInternal
     * （含续传起点推导与并发槽位门控），受并发上限约束依次进入调度。
     * 调用方需持有 tasksLock，resumeDownloadInternal 不再重复取锁。
     */
    override fun retryAllFailed() {
        // 锁内只取失败列表，磁盘 I/O 与调度都在锁外；失败任务可能多达数十个，
        // 原实现在锁内逐个 deriveResumeState，会把主线程的 pause/cancel 长时间挡住。
        val failedIds = tasksLock.withLock {
            taskMap.values.filter { it.status == DownloadStatus.FAILED }.map { it.id }
        }
        failedIds.forEach { id ->
            val task = taskMap[id] ?: return@forEach
            val epochAtSchedule = pauseEpochOf(id)
            val (rb, rm) = deriveResumeState(task)
            tasksLock.withLock {
                val current = taskMap[id] ?: return@withLock
                if (current.status != DownloadStatus.FAILED) return@withLock
                startDownloadWithConcurrencyInternal(
                    current,
                    rb,
                    rm,
                    expectedStatus = DownloadStatus.FAILED,
                    scheduledPauseEpoch = epochAtSchedule
                )
            }
        }
        AppLogger.log("DownloadManager", "retryAllFailed: 已触发 " + failedIds.size + " 个失败任务重试")
    }

    /**
     * 启动单个任务的续传：先在锁外推导续传状态，再持锁启动。
     * 调用方**不要**持有 tasksLock（推导含磁盘 I/O）。
     */
    private fun resumeDownloadInternal(taskId: Int) {
        val task = taskMap[taskId] ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        if (downloadJobs.containsKey(taskId)) return
        // 推导含磁盘 IO：期间可能被暂停/取消，故先记录期望状态与暂停序号，开跑前复核
        val expectedStatus = task.status
        val epochAtSchedule = pauseEpochOf(taskId)
        val (resumeBytes, resumeMap) = deriveResumeState(task)
        tasksLock.withLock {
            startDownloadWithConcurrencyInternal(
                task,
                resumeBytes,
                resumeMap,
                expectedStatus = expectedStatus,
                scheduledPauseEpoch = epochAtSchedule
            )
        }
        if (resumeMap != null) {
            AppLogger.log("DownloadManager", "Resume (chunked) download: $taskId, undone chunks=${resumeMap.count { !it }}/${resumeMap.size}")
        } else {
            AppLogger.log("DownloadManager", "Resume download started: $taskId (resumeBytes=$resumeBytes)")
        }
    }

    override fun cancelDownload(taskId: Int) {
        cancelTaskCalls(taskId)
        val job = downloadJobs.remove(taskId)
        job?.cancel()

        // 锁内只做内存移除；文件删除移到锁外（delete 会触发 FS 元数据更新，持锁期间
        // 主线程的 pauseDownload / startDownload 会长时间等锁）
        val removed = tasksLock.withLock {
            val task = taskMap.remove(taskId)
            emitTasks()
            task
        }
        // 走与 persistTask 相同的串行队列，避免 DELETE 与在途 UPSERT 竞争导致任务复活
        persistDelete(taskId)
        lastNotifiedPercent.remove(taskId)
        pauseEpoch.remove(taskId)
        // 任务记录已删除，无法再走 updateTask 进度通道；必须显式通知前台服务撤销该任务的
        // 常驻通知并重算活动集合，否则通知（ongoing 不可划掉）与 dataSync 前台服务会永久残留。
        forwardRemoveToService(taskId)
        AppLogger.log("DownloadManager", "Download cancelled: $taskId")

        if (removed == null) {
            if (job == null) taskCancelFlags.remove(taskId)
            return
        }
        // 文件删除必须等 Job 真正结束：Job.cancel() 是协作式的，写循环里检查到取消后仍有
        // 正在进行的 write。若此时就删文件，写入会落到已 unlink 的 inode 上，随后新建的
        // 同名任务会指向另一个 inode，表现为「新任务下载到旧数据」并泄漏磁盘空间。
        // 原实现用 runBlocking { join } 在调用线程上等最多 5s：调用点虽在 IO 线程，但
        // DownloadViewModel 用 Mutex 串行化全部下载操作，一次取消会连带卡住同一时刻的
        // 暂停 / 恢复 / 批量重试。改为在内部作用域里等待，调用方立即返回。
        scope.launch {
            withTimeoutOrNull(CANCEL_JOIN_TIMEOUT_MS) { job?.join() }
            if (job == null) taskCancelFlags.remove(taskId)
            // 等待期间用户可能对同一部视频重新发起下载并复用了这个路径：
            // 此时文件归新任务所有，绝不能删（新任务可能正靠它续传）。
            val pathTaken = tasksLock.withLock {
                taskMap.values.any { it.filePath == removed.filePath }
            }
            if (pathTaken) {
                AppLogger.log(
                    "DownloadManager",
                    "取消后该路径已被新任务接管，跳过文件删除: ${removed.filePath}"
                )
                return@launch
            }
            deleteTaskFiles(removed)
        }
    }

    override suspend fun getStorageInfo(): DownloadStorageInfo = withContext(Dispatchers.IO) {
        val dir = DownloadStorageInspector.currentDirectory(context.getExternalFilesDir(null))
        val activePaths = tasks.value.mapTo(HashSet()) { it.filePath }
        DownloadStorageInspector.inspect(dir, activePaths)
    }

    override suspend fun clearOrphanPartmaps(): Long = withContext(Dispatchers.IO) {
        val dir = DownloadStorageInspector.currentDirectory(context.getExternalFilesDir(null))
        val activePaths = tasks.value.mapTo(HashSet()) { it.filePath }
        DownloadStorageInspector.clearOrphanPartmaps(dir, activePaths)
    }

    override suspend fun rescanExistingDownloads(): Int = withContext(Dispatchers.IO) {
        adoptOrphanFiles()
    }

    /** 删除任务的本地文件：正片 + 分块位图表 + 其写入中间文件。幂等，失败仅记录。 */
    private fun deleteTaskFiles(task: DownloadTask) {
        runCatching { File(task.filePath).takeIf { it.exists() }?.delete() }
        runCatching { File(task.filePath + PARTMAP_SUFFIX).delete() }
        runCatching { File(task.filePath + PARTMAP_SUFFIX + ".tmp").delete() }
        clearChunkedMark(task.id)
    }

    /**
     * 注册网络变化监听：断网时把在途任务转为 PAUSED，恢复后自动续传。
     *
     * 原实现没有任何网络感知：断网后分块路径的块会被无限重新入队（在 30 秒零进度
     * 看门狗下反复全量重建连接），单连接路径要等 readTimeout=120s 才判失败；期间通知
     * 一直显示「下载中」，实际没有任何数据流动，纯耗电。
     */
    private fun registerNetworkObserver() {
        if (networkCallbackRegistered) return
        val cm = runCatching {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull() ?: run {
            AppLogger.log("DownloadManager", "无法获取 ConnectivityManager，跳过网络监听")
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                // 只在真的可用时才触发；onCapabilitiesChanged 会更晚且频繁，onAvailable 足够
                scope.launch {
                    // 换到 Wi-Fi 后闸门可能刚好放行，先重算再决定要不要续传
                    if (refreshGateState().allowed) startNextPendingTask()
                    val paused = tasksLock.withLock {
                        // 只恢复「因断网自动暂停」的那些，手动暂停的不动
                        taskMap.values.filter {
                            it.status == DownloadStatus.PAUSED && it.id in autoPausedByNetwork
                        }.map { it.id }
                    }
                    if (paused.isEmpty()) return@launch
                    AppLogger.log("DownloadManager", "网络已恢复，自动续传 ${paused.size} 个已暂停任务")
                    // resumeDownloadInternal 现在不持锁（内部自行处理），from within launch 无需 tasksLock
                    paused.forEach { id ->
                        autoPausedByNetwork.remove(id)
                        resumeDownloadInternal(id)
                    }
                }
            }

            override fun onLost(network: android.net.Network) {
                scope.launch {
                    val active = tasksLock.withLock {
                        taskMap.values.filter {
                            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                        }.map { it.id }
                    }
                    if (active.isEmpty()) return@launch
                    AppLogger.log("DownloadManager", "网络已断开，暂停 ${active.size} 个任务等待恢复")
                    // 用 pauseDownload（含取消在途调用），使位图表按暂停语义落盘；
                    // 同时记入 autoPausedByNetwork，供网络恢复后只续传这些任务。
                    active.forEach {
                        autoPausedByNetwork.add(it)
                        pauseDownload(it)
                    }
                }
            }
        }
        networkCallbackRegistered = runCatching {
            cm.registerDefaultNetworkCallback(callback)
            true
        }.getOrDefault(false)
        if (!networkCallbackRegistered) {
            AppLogger.log("DownloadManager", "注册网络监听失败，将依赖下载自身的重试与超时")
        }
    }

    /**
     * 闸门的「条件变化」触发源。
     *
     * 此前闸门只在任务启动时求值（[reevaluateGate] 全仓没有调用方），而充电状态与夜间时段都不会
     * 触发任何既有回调 —— 「仅充电 / 仅夜间」排队的任务会一直停在 PENDING，直到网络抖动。
     * 这里补上两类触发：
     * - 充电插拔：ACTION_POWER_CONNECTED / DISCONNECTED（系统保护广播，只能动态注册）；
     * - 夜间窗口边界：进程内定时器，到 00:00 / 06:00 重新求值。设备休眠会延后唤醒但不会漏，
     *   延迟到也就意味着窗口已经打开，正好可以放行。
     *
     * Wi-Fi 条件不在这里处理：网络恢复已由 [registerNetworkObserver] 的 onAvailable 触发。
     */
    private fun registerGateObservers() {
        if (gateReceiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                AppLogger.log("DownloadManager", "电源状态变化，重新求值调度闸门: " + intent?.action)
                reevaluateGate()
            }
        }
        gateReceiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrDefault(false)
        if (gateReceiverRegistered) {
            gateReceiver = receiver
        } else {
            AppLogger.log("DownloadManager", "注册电源状态监听失败，闸门仍只在任务启动时求值")
        }

        // 夜间窗口边界：只做「下一次切换」，到点后重新计算，无需每天固定闹钟；
        // 设备休眠导致延迟唤醒时会自然落到边界之后，不会累积漂移。
        scope.launch {
            while (true) {
                val waitMs = DownloadGate.msUntilNextNightBoundary()
                delay(waitMs)
                if (Preferences.downloadNightOnly) {
                    AppLogger.log("DownloadManager", "夜间时段边界切换，重新求值调度闸门")
                    reevaluateGate()
                }
            }
        }
    }

    /**
     * 显式生命周期出口。单例随进程存在是预期行为，但提供结构化取消入口：进程退出、测试拆卸、
     * 或需要强制中断所有在途下载时调用，保证协程与在途请求被确定性回收。
     * scope 根使用 SupervisorJob，单个下载任务的异常不会株连其它在途任务。
     */
    fun shutdown() {
        // 动态注册的接收器必须显式注销，否则单例被重建时会累积监听
        gateReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        gateReceiver = null
        gateReceiverRegistered = false
        scopeJob.cancel()
        downloadJobs.clear()
        taskCalls.clear()
        taskCancelFlags.clear()
        activeSlots.set(0)
        AppLogger.log("DownloadManager", "shutdown: 已取消下载作用域并清理在途状态")
    }

    override suspend fun exportMigration(): ByteArray? = withContext(Dispatchers.IO) {
        val migratable = taskMap.values.filter {
            it.status != DownloadStatus.COMPLETED && File(it.filePath + PARTMAP_SUFFIX).exists()
        }
        if (migratable.isEmpty()) return@withContext null
        val manifest = DownloadMigrationCodec.encode(migratable) { true }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            migratable.forEach { task ->
                val partmap = File(task.filePath + PARTMAP_SUFFIX)
                if (!partmap.exists()) return@forEach
                zip.putNextEntry(ZipEntry(PARTMAP_DIR + partmap.name))
                partmap.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        AppLogger.log("DownloadManager", "导出迁移包: " + migratable.size + " 个任务")
        out.toByteArray()
    }

    override suspend fun importMigration(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val dir = resolveDownloadDir()
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                // entry.size 在流式读取时常为 -1，必须自己计边读边限长，否则异常包可撑爆内存
                val data = readEntryLimited(zip) ?: continue
                entries[entry.name] = data
            }
        }
        val manifest = entries[MANIFEST_ENTRY]?.let { String(it) }
            ?: return@withContext 0
        val migrated = DownloadMigrationCodec.decode(manifest)
        if (migrated.isEmpty()) return@withContext 0

        // 位图落盘是磁盘 I/O，必须先做完再进锁：持锁写文件会挡住主线程的暂停 / 取消
        val pending = migrated.mapNotNull { entry ->
            val newPath = File(dir, File(entry.filePath).name).absolutePath
            if (tasksLock.withLock { taskMap.values.any { it.filePath == newPath } }) {
                return@mapNotNull null
            }
            val partmapBytes = entry.partmapName?.let { entries[PARTMAP_DIR + it] }
            if (partmapBytes != null) {
                runCatching { File(newPath + PARTMAP_SUFFIX).writeBytes(partmapBytes) }
            }
            entry to newPath
        }
        if (pending.isEmpty()) return@withContext 0

        var imported = 0
        tasksLock.withLock {
            pending.forEach { (entry, newPath) ->
                // 写盘期间可能有同路径任务被创建，入锁后再确认一次
                if (taskMap.values.any { it.filePath == newPath }) return@forEach
                val id = nextTaskId()
                val task = DownloadMigrationCodec.toTask(entry, newPath).copy(id = id)
                taskMap[id] = task
                persistTask(task)
                imported++
            }
            if (imported > 0) emitTasks()
        }
        AppLogger.log("DownloadManager", "导入迁移包: 成功 " + imported + "/" + migrated.size)
        imported
    }

    /** 逐条读入并限长；超过上限返回 null 并跳过该条目。 */
    private fun readEntryLimited(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_MIGRATION_ENTRY_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
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

    override suspend fun getCompletedDownloadCount(): Int {
        return try {
            downloadDao.getCompletedCount()
        } catch (e: SQLiteException) {
            getCompletedDownloads().size
        }
    }

    override fun isVideoDownloaded(videoId: String, quality: String): Boolean {
        return if (videoId.isBlank()) {
            false
        } else {
            taskMap.values.any {
                it.videoId == videoId && it.status == DownloadStatus.COMPLETED &&
                        (quality.isBlank() || it.quality == quality)
            }
        }
    }

    override fun isVideoDownloading(videoId: String, quality: String): Boolean {
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

    override fun getDownloadStatus(videoId: String): DownloadStatus? {
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

        /** 分块下载标记目录（放在 filesDir 下，与公共下载目录里的位图表分开）。 */
        private const val CHUNKED_MARKER_DIR = "download_chunked"
    }
}
