package app.amisles.hanime.core.common.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一日志工具
 * 写入到 app's filesDir/hanime_app.log
 */
object AppLogger {
    private const val LOG_FILE_NAME = "hanime_app.log"
    private lateinit var logFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        log("Logger", "initialized, file: ${logFile.absolutePath}")
    }

    fun d(tag: String, message: String) {
        log(tag, message)
    }

    fun i(tag: String, message: String) {
        log(tag, "[INFO] $message")
    }

    fun w(tag: String, message: String) {
        log(tag, "[WARN] $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        logError(tag, message, throwable)
    }

    fun log(tag: String, message: String) {
        try {
            val time = dateFormat.format(Date())
            val logLine = "[$time] $tag: $message\n"
            PrintWriter(FileWriter(logFile, true)).use { it.print(logLine) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logError(tag: String, message: String, e: Throwable? = null) {
        try {
            val time = dateFormat.format(Date())
            val logLine = "[$time] ERROR $tag: $message" +
                (if (e != null) "\n${e.stackTraceToString()}" else "") + "\n"
            PrintWriter(FileWriter(logFile, true)).use { it.print(logLine) }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun getLogContent(): String {
        return try {
            if (logFile.exists()) logFile.readText() else "Log file not found"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }
}