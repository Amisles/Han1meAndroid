package app.amisles.hanime.data.download

import android.content.Context
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.DownloadEntity
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import android.content.Intent
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// D2：用于把进度回调信息带出 tasksLock 后异步派发，避免持锁做跨进程 IPC
private data class ProgressUpdate(
    val taskId: Int,
    val title: String,
    val progress: Int,
    val status: DownloadStatus
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    // 进度更新回调
    var onProgressUpdate: ((taskId: Int, title: String, progress: Int, status: DownloadStatus) -> Unit)? = null

    private val downloadJobs = ConcurrentHashMap<Int, Job>()
    private val taskIdCounter = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO)
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
    @Volatile
    private var downloadServiceStarted = false

    private val downloadDir: File
        get() {
            // D1：外部存储不可用时（部分设备/限存储场景 getExternalFilesDir 返回 null）
            // 降级到应用私有内部存储，避免 NPE。
            val base = context.getExternalFilesDir(null)
                ?.let { File(it, "Downloads") }
                ?: File(context.filesDir, "Downloads")
            if (!base.exists()) base.mkdirs()
            return base
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
                    _tasks.value = restoredTasks
                }
                taskIdCounter.set(entities.maxOfOrNull { it.id } ?: 0)
                AppLogger.log("DownloadManager", "Restored ${restoredTasks.size} tasks from DB, taskIdCounter=$taskIdCounter")
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
        tasksLock.withLock {
            val existingTask = _tasks.value.find { it.url == url && it.quality == quality }
            if (existingTask != null) {
                AppLogger.log("DownloadManager", "Download already exists for $quality: $title (taskId=${existingTask.id})")
                if (existingTask.status == DownloadStatus.PAUSED || existingTask.status == DownloadStatus.FAILED) {
                    resumeDownloadInternal(existingTask.id)
                }
                return existingTask.id
            }

            val taskId = taskIdCounter.incrementAndGet()
            val fileName = "${title}_$quality.mp4".replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val filePath = File(downloadDir, fileName).absolutePath

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

            _tasks.value = _tasks.value + task
            persistTask(task)
            AppLogger.log("DownloadManager", "Starting download $taskId: $title ($quality)")

            // 槽位未满则立即开始；否则保持 PENDING，由完成后的链式调度拾起
            if (activeSlots.get() < concurrencyLimit) {
                startDownloadWithConcurrencyInternal(task)
            } else {
                AppLogger.log("DownloadManager", "下载槽位已满($concurrencyLimit)，任务 $taskId 进入等待队列")
            }

            return taskId
        }
    }

    /**
     * 内部方法，调用者必须持有 tasksLock。
     * 防止同一任务被重复启动。统一负责 activeSlots 的 +1 与（终态/取消时）-1，
     * 并在结束后调度下一个 PENDING 任务，修复 B1/B3/B4 并发门控失效问题。
     */
    private fun startDownloadWithConcurrencyInternal(task: DownloadTask, resumeBytes: Long = 0L) {
        if (downloadJobs.containsKey(task.id)) return
        // 占有槽位（同步计数，作为并发门控统一依据）
        activeSlots.incrementAndGet()

        val job = scope.launch {
            try {
                // 等待获取信号量许可（固定容量硬上限）
                downloadSemaphore.withPermit {
                    AppLogger.log("DownloadManager", "开始下载: ${task.title} (并发槽位已获取, resumeFrom=$resumeBytes)")

                    updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
                    downloadFile(task.id, task.url, task.filePath, resumeBytes)
                    // H5/H6 完整性校验：完成前断言下载字节数达标，避免写出不全文件却标记成功
                    updateTask(task.id) { t ->
                        if (t.totalBytes > 0 && t.downloadedBytes < t.totalBytes) {
                            AppLogger.logError(
                                "DownloadManager",
                                "完整性校验失败 ${task.title}: ${t.downloadedBytes}/${t.totalBytes}"
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
                AppLogger.logError("DownloadManager", "下载失败 ${task.title}: ${e.message}", e)
                updateTask(task.id) { it.copy(status = DownloadStatus.FAILED, errorMessage = classifyError(e)) }
            } finally {
                // 释放槽位并调度下一个等待任务（成功/失败/取消均执行）
                activeSlots.decrementAndGet()
                startNextPendingTask()
            }
        }

        downloadJobs[task.id] = job
    }

    private fun startNextPendingTask() {
        tasksLock.withLock {
            val pendingTask = _tasks.value.firstOrNull {
                it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id)
            }
            if (pendingTask != null && activeSlots.get() < concurrencyLimit) {
                AppLogger.log("DownloadManager", "启动下一个等待任务: ${pendingTask.title}")
                startDownloadWithConcurrencyInternal(pendingTask)
            }
        }
    }

    fun updateConcurrencyLimit(maxConcurrent: Int) {
        val safeMax = maxConcurrent.coerceIn(1, MAX_CONCURRENT)
        // 仅更新软门限；信号量固定容量，不再重建对象（避免旧许可成为孤儿）
        concurrencyLimit = safeMax
        AppLogger.log("DownloadManager", "并发下载数已更新为: $safeMax")

        tasksLock.withLock {
            val pendingTasks = _tasks.value.filter {
                it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id)
            }
            pendingTasks.forEach { task ->
                if (activeSlots.get() < concurrencyLimit) {
                    startDownloadWithConcurrencyInternal(task)
                }
            }
        }
    }

    private suspend fun downloadFile(taskId: Int, url: String, filePath: String, resumeBytes: Long = 0L) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36")
            .header("Referer", "https://hanimeone.me/")
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
                response = client.newCall(requestBuilder.build()).execute()
            } catch (e: IOException) {
                attempt++
                if (attempt <= maxRetries) {
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

            val body = resp.body ?: run {
                updateTask(taskId) { it.copy(status = DownloadStatus.FAILED, errorMessage = "响应体为空") }
                return
            }
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
            val outputStream = if (resumeBytes > 0 && isPartial) {
                java.io.FileOutputStream(outputFile, true)
            } else {
                java.io.FileOutputStream(outputFile, false)
            }

            val buffer = ByteArray(8192)
            var downloadedBytes = if (isPartial) resumeBytes else 0L
            var lastUpdate = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (true) {
                        yield()
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
     * 原子地更新单个任务。使用 tasksLock 保护读-改-写操作。
     */
    private fun updateTask(taskId: Int, updater: (DownloadTask) -> DownloadTask) {
        var progressUpdate: ProgressUpdate? = null
        tasksLock.withLock {
            _tasks.value = _tasks.value.map { task ->
                if (task.id == taskId) {
                    val newTask = updater(task)
                    val statusChanged = newTask.status != task.status
                    // 进度更新走内存态驱动 UI；仅状态切换或达到节流间隔才落盘，降低写放大
                    if (statusChanged || shouldPersistProgress(taskId)) {
                        lastPersistTime[taskId] = System.currentTimeMillis()
                        persistTask(newTask)
                    }
                    // 仅锁定内记录需回调的信息，真正回调移出锁外执行（见下方 D2 说明）
                    if (newTask.status == DownloadStatus.DOWNLOADING && newTask.totalBytes > 0) {
                        val progress = (newTask.downloadedBytes * 100 / newTask.totalBytes).toInt()
                        progressUpdate = ProgressUpdate(taskId, newTask.title, progress, newTask.status)
                    } else if (newTask.status == DownloadStatus.COMPLETED || newTask.status == DownloadStatus.FAILED) {
                        progressUpdate = ProgressUpdate(taskId, newTask.title, 100, newTask.status)
                    }
                    newTask
                } else {
                    task
                }
            }
        }
        // D2：将跨进程 IPC（startForegroundService，经由 onProgressUpdate→forwardToService）移出 tasksLock，
        // 避免锁被 Binder 调用长时间占用，拖慢 pause/cancel/updateConcurrencyLimit 等取锁操作。
        progressUpdate?.let { (id, title, progress, status) ->
            onProgressUpdate?.invoke(id, title, progress, status)
        }
    }

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
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        if (downloadJobs.containsKey(taskId)) return

        val file = File(task.filePath)
        // 文件完整性验证：如果文件存在但大小为0或远大于预期，删除重新下载
        val resumeBytes = if (file.exists() && file.length() > 0) {
            if (task.totalBytes > 0 && file.length() > task.totalBytes) {
                AppLogger.log("DownloadManager", "文件大小异常(${file.length()} > ${task.totalBytes})，删除重新下载")
                file.delete()
                0L
            } else {
                file.length()
            }
        } else {
            0L
        }

        // 复用统一启动函数：受 activeSlots 并发门控约束（修复 B3），并在结束后
        // 统一 decrement 槽位 + 调度下一个 PENDING 任务
        startDownloadWithConcurrencyInternal(task, resumeBytes)
        AppLogger.log("DownloadManager", "Resume download started: $taskId")
    }

    fun cancelDownload(taskId: Int) {
        downloadJobs[taskId]?.cancel()
        downloadJobs.remove(taskId)
        tasksLock.withLock {
            val task = _tasks.value.find { it.id == taskId }
            task?.let {
                val file = File(it.filePath)
                if (file.exists()) file.delete()
            }
            _tasks.value = _tasks.value.filter { it.id != taskId }
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

    fun getCompletedDownloads(): List<DownloadTask> {
        return _tasks.value.filter { it.status == DownloadStatus.COMPLETED }
    }

    fun getDownloadingTasks(): List<DownloadTask> {
        return _tasks.value.filter {
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
        // F2：有 videoId 时以 videoId 为准（CDN 直链不含 id 也不会误判）；
        // videoId 为空时回退到 url/title 子串匹配以兼容旧数据。
        return if (videoId.isBlank()) {
            _tasks.value.any {
                it.status == DownloadStatus.COMPLETED &&
                (it.url.contains(videoId) || it.title.contains(videoId))
            }
        } else {
            _tasks.value.any {
                it.videoId == videoId && it.status == DownloadStatus.COMPLETED &&
                (quality.isBlank() || it.quality == quality)
            }
        }
    }

    fun isVideoDownloading(videoId: String, quality: String = ""): Boolean {
        return if (videoId.isBlank()) {
            _tasks.value.any {
                (it.url.contains(videoId) || it.title.contains(videoId)) &&
                (it.status == DownloadStatus.DOWNLOADING ||
                 it.status == DownloadStatus.PENDING ||
                 it.status == DownloadStatus.PAUSED)
            }
        } else {
            _tasks.value.any {
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
            _tasks.value.find { it.url.contains(videoId) || it.title.contains(videoId) }
        } else {
            _tasks.value.find { it.videoId == videoId }
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
            val firstTime = !downloadServiceStarted
            if (firstTime) {
                context.startForegroundService(intent)
                downloadServiceStarted = true
            } else {
                context.startService(intent)
            }
            // 收到终态且已无活动任务时复位标记，允许下次新下载重新走 startForegroundService
            // （服务可能已停止，需重新拉起前台）。
            if (status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED) {
                val stillActive = _tasks.value.any {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
                }
                if (!stillActive) downloadServiceStarted = false
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

    private companion object {
        // 并发下载绝对硬上限（信号量固定容量），与用户可配置并发上限的上界一致
        const val MAX_CONCURRENT = 5
        // 进度类 DB 落盘节流间隔（ms）：进度更新走内存态，仅按此间隔写 DB
        const val PROGRESS_PERSIST_INTERVAL_MS = 3000L

        // DownloadService 的完整类名。data 模块不能直接依赖 app 模块，
        // 因此通过显式类名的 Intent 将进度转发给 app 模块中的 DownloadService。
        const val DOWNLOAD_SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"
    }
}