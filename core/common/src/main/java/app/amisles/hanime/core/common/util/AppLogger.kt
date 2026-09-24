package app.amisles.hanime.core.common.util

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 统一日志工具，写入 filesDir/hanime_app.log。
 *
 * 所有磁盘写入都在专用日志线程完成：调用方（含主线程）只做一次非阻塞入队，
 * 不再被文件 IO 与日志轮转阻塞。此前为同步写盘，而调用点遍布解析、下载、网络与
 * ViewModel 的异常路径，主线程被阻塞的风险不可控。
 */
object AppLogger {

    private const val LOG_TAG = "AppLogger"
    private const val LOG_FILE_NAME = "hanime_app.log"

    /** 单个日志文件上限，超过即轮转 */
    private const val MAX_FILE_SIZE = 2L * 1024 * 1024

    /** 保留的历史分片数量（.1 最新，.N 最旧） */
    private const val MAX_BACKUP_FILES = 2

    /** 写缓冲大小 */
    private const val WRITE_BUFFER_SIZE = 8 * 1024

    /** 每写入这么多行强制刷盘一次，兼顾性能与进程被杀时的日志留存 */
    private const val FLUSH_EVERY_LINES = 20

    /** getLogContent 返回的最大字符数，避免一次性读入超大文本导致 OOM */
    private const val MAX_READ_CHARS = 256 * 1024

    /** 待写队列上限。写盘慢于产生速度时丢弃新日志，避免队列无界增长占用内存。 */
    private const val QUEUE_CAPACITY = 4096

    /** getLogContent 等待队列落地的上限，避免调用方被长时间阻塞。 */
    private const val DRAIN_WAIT_MS = 1000L

    /** 待写入的一行。force 表示立即刷盘（错误日志需要保证崩溃后仍可读）。 */
    private class Line(val text: String, val force: Boolean)

    private lateinit var logFile: File

    // DateTimeFormatter 不可变且线程安全，因此入队路径无需加锁
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var linesSinceFlush = 0

    /** 待写入队列。消费端为单一日志线程，写入顺序即入队顺序。 */
    private val queue = ArrayBlockingQueue<Line>(QUEUE_CAPACITY)
    private val drainGate = Any()

    @Volatile
    private var drainScheduled = false

    private var handler: Handler? = null

    fun init(context: Context) {
        synchronized(lock) {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            closeWriterLocked()
        }
        if (handler == null) {
            val thread = HandlerThread("hanime-logger")
            thread.start()
            handler = Handler(thread.looper)
        }
        log("Logger", "initialized, file: ${logFile.absolutePath}")
    }

    fun d(tag: String, message: String) = log(tag, message)

    fun i(tag: String, message: String) = log(tag, "[INFO] $message")

    fun w(tag: String, message: String) = log(tag, "[WARN] $message")

    fun e(tag: String, message: String, throwable: Throwable? = null) = logError(tag, message, throwable)

    fun log(tag: String, message: String) {
        enqueue("", tag, message, null, force = false)
    }

    fun logError(tag: String, message: String, e: Throwable? = null) {
        enqueue("ERROR ", tag, message, e?.stackTraceToString(), force = true)
    }

    /** 请求把队列中的日志写入磁盘。非阻塞：实际写盘由日志线程完成。 */
    fun flush() {
        scheduleDrain()
    }

    /** 读取日志内容（历史分片在前，当前文件在后，按时间正序）。 */
    fun getLogContent(): String {
        // 先等待队列落地，否则会读到不含最新日志的内容
        awaitDrain()
        synchronized(lock) {
            if (!::logFile.isInitialized) return "Logger not initialized"
            flushLocked()
            return try {
                val files = mutableListOf<File>()
                for (i in MAX_BACKUP_FILES downTo 1) {
                    val backup = File("${logFile.absolutePath}.$i")
                    if (backup.exists()) files.add(backup)
                }
                if (logFile.exists()) files.add(logFile)

                val sb = StringBuilder()
                for (file in files) {
                    sb.append(file.readText())
                    if (sb.length >= MAX_READ_CHARS) break
                }
                // 轮转已保证总量上限，此处截尾防止一次性读入超大文本
                if (sb.length > MAX_READ_CHARS) sb.delete(0, sb.length - MAX_READ_CHARS)
                if (sb.isEmpty()) "Log is empty" else sb.toString()
            } catch (e: IOException) {
                "Error reading log: ${e.message}"
            }
        }
    }

    // ── 入队与消费 ───────────────────────────────────────────────────────────

    private fun enqueue(prefix: String, tag: String, message: String, stackTrace: String?, force: Boolean) {
        val line = Line(buildLine(prefix, tag, message, stackTrace), force)
        if (!queue.offer(line)) {
            // 队列满说明写盘已明显落后，丢弃而非阻塞调用方
            return
        }
        scheduleDrain()
    }

    private fun buildLine(prefix: String, tag: String, message: String, stackTrace: String?): String {
        val sb = StringBuilder()
        sb.append('[')
            .append(LocalDateTime.now().format(dateFormatter))
            .append("] ")
            .append(prefix)
            .append(tag)
            .append(": ")
            .append(message)
        if (stackTrace != null) {
            sb.append('\n').append(stackTrace)
        }
        return sb.toString()
    }

    /** 保证「日志线程上最多存在一个待执行的 drain 任务」，避免每行日志都 post 一次。 */
    private fun scheduleDrain() {
        synchronized(drainGate) {
            if (drainScheduled) return
            drainScheduled = true
        }
        val h = handler
        if (h == null || !h.post { drain() }) {
            // 日志线程尚未启动或已退出：复位标记，避免后续日志永远无法调度
            synchronized(drainGate) { drainScheduled = false }
        }
    }

    private fun drain() {
        try {
            while (true) {
                val line = queue.poll() ?: break
                writeLine(line)
            }
        } finally {
            synchronized(drainGate) { drainScheduled = false }
            // 兜底：drain 期间新入队的日志可能错过调度，此处补一次
            if (queue.isNotEmpty()) scheduleDrain()
        }
    }

    private fun awaitDrain() {
        val h = handler ?: return
        if (Looper.myLooper() == h.looper) return // 已在日志线程，自等待会死锁
        val latch = CountDownLatch(1)
        val posted = h.post {
            drain()
            latch.countDown()
        }
        if (!posted) return
        runCatching { latch.await(DRAIN_WAIT_MS, TimeUnit.MILLISECONDS) }
    }

    // ── 实际写盘（仅日志线程） ───────────────────────────────────────────────

    private fun writeLine(line: Line) {
        synchronized(lock) {
            if (!::logFile.isInitialized) return
            try {
                rotateIfNeededLocked()
                val out = ensureWriterLocked() ?: return
                out.write(line.text)
                out.newLine()
                linesSinceFlush++
                if (line.force || linesSinceFlush >= FLUSH_EVERY_LINES) {
                    flushLocked()
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Failed to write app log: ${e.message}")
            }
        }
    }

    private fun ensureWriterLocked(): BufferedWriter? {
        writer?.let { return it }
        return try {
            BufferedWriter(FileWriter(logFile, true), WRITE_BUFFER_SIZE).also { writer = it }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Failed to open app log file: ${e.message}")
            null
        }
    }

    private fun flushLocked() {
        try {
            writer?.flush()
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Failed to flush app log: ${e.message}")
        }
        linesSinceFlush = 0
    }

    private fun closeWriterLocked() {
        try {
            writer?.close()
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Failed to close app log writer: ${e.message}")
        }
        writer = null
        linesSinceFlush = 0
    }

    /** 超过大小上限时轮转：当前 → .1，.1 → .2，最旧的分片丢弃。 */
    private fun rotateIfNeededLocked() {
        if (!logFile.exists() || logFile.length() < MAX_FILE_SIZE) return
        closeWriterLocked()
        try {
            val oldest = File("${logFile.absolutePath}.$MAX_BACKUP_FILES")
            if (oldest.exists() && !oldest.delete()) {
                Log.w(LOG_TAG, "Failed to delete oldest log shard: ${oldest.name}")
            }
            for (i in MAX_BACKUP_FILES - 1 downTo 1) {
                val from = File("${logFile.absolutePath}.$i")
                if (from.exists() && !from.renameTo(File("${logFile.absolutePath}.${i + 1}"))) {
                    Log.w(LOG_TAG, "Failed to rotate log shard: ${from.name}")
                }
            }
            if (!logFile.renameTo(File("${logFile.absolutePath}.1"))) {
                // 轮转失败：writer 已在 closeWriterLocked 中关闭，后续写会重新打开并继续追加
                Log.w(LOG_TAG, "Failed to rotate current log file")
            }
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Failed to rotate app log: ${e.message}")
        }
    }
}
