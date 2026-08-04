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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    // 进度更新回调，供外部（如 DownloadService）订阅以更新通知栏
    var onProgressUpdate: ((taskId: Int, title: String, progress: Int, status: DownloadStatus) -> Unit)? = null

    private val downloadJobs = ConcurrentHashMap<Int, Job>()
    private val taskIdCounter = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val tasksLock = ReentrantLock()
    @Volatile
    private var downloadSemaphore: Semaphore = Semaphore(Preferences.maxDownloadConcurrent)
    private val semaphoreLock = Any()

    private val downloadDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "Downloads")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        onProgressUpdate = { _, title, progress, status ->
            forwardToService(context, title, progress, status)
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
                        thumbnailUrl = entity.thumbnailUrl
                    )
                }
                tasksLock.withLock {
                    _tasks.value = restoredTasks
                }
                taskIdCounter.set(entities.maxOfOrNull { it.id } ?: 0)
                AppLogger.log("DownloadManager", "Restored ${restoredTasks.size} tasks from DB, taskIdCounter=$taskIdCounter")
            } catch (e: Exception) {
                AppLogger.logError("DownloadManager", "Failed to restore tasks: ${e.message}", e)
            }
        }
    }

    fun startDownload(title: String, quality: String, url: String, thumbnailUrl: String = ""): Int {
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
                thumbnailUrl = thumbnailUrl
            )

            _tasks.value = _tasks.value + task
            persistTask(task)
            AppLogger.log("DownloadManager", "Starting download $taskId: $title ($quality)")

            startDownloadWithConcurrencyInternal(task)

            return taskId
        }
    }

    /**
     * 内部方法，调用者必须持有 tasksLock。
     * 防止同一任务被重复启动。
     */
    private fun startDownloadWithConcurrencyInternal(task: DownloadTask) {
        if (downloadJobs.containsKey(task.id)) return

        val job = scope.launch {
            try {
                // 等待获取信号量许可（使用synchronized读取）
                val semaphore = synchronized(semaphoreLock) { downloadSemaphore }
                semaphore.withPermit {
                    AppLogger.log("DownloadManager", "开始下载: ${task.title} (并发槽位已获取)")

                    updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }
                    downloadFile(task.id, task.url, task.filePath)
                    updateTask(task.id) { it.copy(status = DownloadStatus.COMPLETED) }
                    AppLogger.log("DownloadManager", "下载完成: ${task.title}")
                }

                // 任务完成后，尝试启动下一个等待中的任务
                startNextPendingTask()

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("DownloadManager", "下载失败 ${task.title}: ${e.message}", e)
                updateTask(task.id) { it.copy(status = DownloadStatus.FAILED) }

                // 任务失败后，尝试启动下一个等待中的任务
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
            if (pendingTask != null) {
                AppLogger.log("DownloadManager", "启动下一个等待任务: ${pendingTask.title}")
                startDownloadWithConcurrencyInternal(pendingTask)
            }
        }
    }

    fun updateConcurrencyLimit(maxConcurrent: Int) {
        val safeMax = maxConcurrent.coerceIn(1, 5)
        synchronized(semaphoreLock) {
            downloadSemaphore = Semaphore(safeMax)
        }
        AppLogger.log("DownloadManager", "并发下载数已更新为: $safeMax")

        tasksLock.withLock {
            // 如果有增加的槽位，启动等待中的任务
            val downloadingCount = _tasks.value.count { it.status == DownloadStatus.DOWNLOADING }
            val pendingTasks = _tasks.value.filter {
                it.status == DownloadStatus.PENDING && !downloadJobs.containsKey(it.id)
            }
            val availableSlots = safeMax - downloadingCount

            if (availableSlots > 0) {
                pendingTasks.take(availableSlots).forEach { task ->
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

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (resumeBytes > 0 && response.code != 206 && !response.isSuccessful) {
                throw Exception("Resume download failed with code ${response.code}")
            }
            if (resumeBytes == 0L && !response.isSuccessful) {
                throw Exception("Download failed with code ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val bodyLength = body.contentLength()
            val isPartial = response.code == 206
            val totalBytes = if (isPartial && resumeBytes > 0) {
                val contentRange = response.header("Content-Range")
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
        tasksLock.withLock {
            _tasks.value = _tasks.value.map { task ->
                if (task.id == taskId) {
                    val newTask = updater(task)
                    persistTask(newTask)
                    // 触发进度回调，通知外部更新通知栏
                    if (newTask.status == DownloadStatus.DOWNLOADING && newTask.totalBytes > 0) {
                        val progress = (newTask.downloadedBytes * 100 / newTask.totalBytes).toInt()
                        onProgressUpdate?.invoke(taskId, newTask.title, progress, newTask.status)
                    } else if (newTask.status == DownloadStatus.COMPLETED || newTask.status == DownloadStatus.FAILED) {
                        onProgressUpdate?.invoke(taskId, newTask.title, 100, newTask.status)
                    }
                    newTask
                } else {
                    task
                }
            }
        }
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
                        thumbnailUrl = task.thumbnailUrl
                    )
                )
            } catch (e: Exception) {
                AppLogger.logError("DownloadManager", "Failed to persist task ${task.id}: ${e.message}", e)
            }
        }
    }

    fun pauseDownload(taskId: Int) {
        downloadJobs[taskId]?.cancel()
        tasksLock.withLock {
            _tasks.value = _tasks.value.map { task ->
                if (task.id == taskId) {
                    val newTask = task.copy(status = DownloadStatus.PAUSED)
                    persistTask(newTask)
                    newTask
                } else {
                    task
                }
            }
        }
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

        val job = scope.launch {
            try {
                // 等待获取信号量许可
                val semaphore = synchronized(semaphoreLock) { downloadSemaphore }
                semaphore.withPermit {
                    AppLogger.log("DownloadManager", "恢复下载: ${task.title} (并发槽位已获取, resumeFrom=$resumeBytes)")

                    updateTask(taskId) { it.copy(status = DownloadStatus.DOWNLOADING) }
                    downloadFile(taskId, task.url, task.filePath, resumeBytes)
                    updateTask(taskId) { it.copy(status = DownloadStatus.COMPLETED) }
                    AppLogger.log("DownloadManager", "Download resumed and completed: ${task.title}")
                }

                startNextPendingTask()

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("DownloadManager", "Resume download failed for ${task.title}: ${e.message}", e)
                updateTask(taskId) { it.copy(status = DownloadStatus.FAILED) }

                startNextPendingTask()
            }
        }
        downloadJobs[taskId] = job
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
            } catch (e: Exception) {
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
        } catch (e: Exception) {
            getCompletedDownloads().size
        }
    }

    fun isVideoDownloaded(videoId: String, quality: String = ""): Boolean {
        return _tasks.value.any {
            it.status == DownloadStatus.COMPLETED &&
            (it.url.contains(videoId) || it.title.contains(videoId))
        }
    }

    fun isVideoDownloading(videoId: String, quality: String = ""): Boolean {
        return _tasks.value.any {
            (it.url.contains(videoId) || it.title.contains(videoId)) &&
            (it.status == DownloadStatus.DOWNLOADING ||
             it.status == DownloadStatus.PENDING ||
             it.status == DownloadStatus.PAUSED)
        }
    }

    fun getDownloadStatus(videoId: String): DownloadStatus? {
        val task = _tasks.value.find { it.url.contains(videoId) || it.title.contains(videoId) }
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
        status: DownloadStatus
    ) {
        try {
            val intent = Intent().apply {
                setClassName(context, DOWNLOAD_SERVICE_CLASS_NAME)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, status.name)
            }
            if (status == DownloadStatus.DOWNLOADING) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // 忽略转发异常，避免影响下载主流程
        }
    }

    private companion object {
        // DownloadService 的完整类名。data 模块不能直接依赖 app 模块，
        // 因此通过显式类名的 Intent 将进度转发给 app 模块中的 DownloadService。
        const val DOWNLOAD_SERVICE_CLASS_NAME = "app.amisles.hanime.service.DownloadService"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"
    }
}