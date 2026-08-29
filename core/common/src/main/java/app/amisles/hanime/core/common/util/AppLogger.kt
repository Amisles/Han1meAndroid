package app.amisles.hanime.core.common.util

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一日志工具，写入 filesDir/hanime_app.log。
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

    private lateinit var logFile: File

    // 仅在持有 lock 时使用：SimpleDateFormat 不是线程安全的
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var linesSinceFlush = 0

    fun init(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        synchronized(lock) {
            logFile = file
            closeWriterLocked()
        }
        log("Logger", "initialized, file: ${file.absolutePath}")
    }

    fun d(tag: String, message: String) = log(tag, message)

    fun i(tag: String, message: String) = log(tag, "[INFO] $message")

    fun w(tag: String, message: String) = log(tag, "[WARN] $message")

    fun e(tag: String, message: String, throwable: Throwable? = null) = logError(tag, message, throwable)

    fun log(tag: String, message: String) {
        appendLine(prefix = "", tag = tag, message = message, stackTrace = null, force = false)
    }

    fun logError(tag: String, message: String, e: Throwable? = null) {
        appendLine(
            prefix = "ERROR ",
            tag = tag,
            message = message,
            stackTrace = e?.stackTraceToString(),
            force = true
        )
    }

    /** 将缓冲强制写入磁盘。正常退出或需要确保日志完整时调用。 */
    fun flush() {
        synchronized(lock) { flushLocked() }
    }

    /** 读取日志内容（历史分片在前，当前文件在后，按时间正序）。 */
    fun getLogContent(): String {
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
                // 轮转已保证总量上限（MAX_FILE_SIZE × (MAX_BACKUP_FILES + 1)），
                // 此处再截一次尾部，防止诊断页加载超大文本阻塞主线程。
                if (sb.length > MAX_READ_CHARS) sb.delete(0, sb.length - MAX_READ_CHARS)
                if (sb.isEmpty()) "Log is empty" else sb.toString()
            } catch (e: IOException) {
                "Error reading log: ${e.message}"
            }
        }
    }
    private fun appendLine(
        prefix: String,
        tag: String,
        message: String,
        stackTrace: String?,
        force: Boolean
    ) {
        synchronized(lock) {
            if (!::logFile.isInitialized) return
            try {
                rotateIfNeededLocked()
                val out = ensureWriterLocked() ?: return
                out.write("[${timestampLocked()}] $prefix$tag: $message")
                if (stackTrace != null) {
                    out.newLine()
                    out.write(stackTrace)
                }
                out.newLine()
                linesSinceFlush++
                if (force || linesSinceFlush >= FLUSH_EVERY_LINES) flushLocked()
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
            File("${logFile.absolutePath}.$MAX_BACKUP_FILES").delete()
            for (i in MAX_BACKUP_FILES - 1 downTo 1) {
                val from = File("${logFile.absolutePath}.$i")
                if (from.exists()) from.renameTo(File("${logFile.absolutePath}.${i + 1}"))
            }
            logFile.renameTo(File("${logFile.absolutePath}.1"))
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Failed to rotate app log: ${e.message}")
        }
    }

    /** 生成时间戳。SimpleDateFormat 非线程安全，调用方必须持有 [lock]。 */
    private fun timestampLocked(): String = dateFormat.format(Date())
}
