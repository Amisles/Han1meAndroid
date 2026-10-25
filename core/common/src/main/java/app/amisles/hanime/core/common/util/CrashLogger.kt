package app.amisles.hanime.core.common.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一条崩溃记录的元信息。 */
data class CrashRecord(
    val file: File,
    val occurredAt: Long,
    val versionName: String,
    val device: String,
    val preview: String
)

/**
 * 崩溃日志的本地落盘与读取。
 *
 * 只写本地，**不上传任何数据**。崩溃发生在主线程未捕获异常的路径上，
 * 因此这里的所有操作都必须足够轻：一次小文件写入 + 一次日志追加，不做压缩与网络。
 */
object CrashLogger {

    private const val DIR_NAME = "crash"
    private const val MAX_RECORDS = 10
    private const val MAX_FILE_BYTES = 256 * 1024
    private const val HEADER_DEVICE_MAX = 120

    /** SimpleDateFormat 非线程安全；崩溃可能同时来自多个线程，故加锁。 */
    private val nameFormatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val nameLock = Any()

    @Volatile
    private var crashDir: File? = null

    @Volatile
    private var installed = false

    fun init(context: Context) {
        val dir = File(context.filesDir, DIR_NAME)
        runCatching { dir.mkdirs() }
        crashDir = dir
        prune(dir)
        installHandler(context)
    }

    private fun installHandler(context: Context) {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 写盘失败也绝不能吞掉异常：必须继续交给系统，否则应用会「卡死」而不是崩溃
            runCatching { write(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir ?: return
        val now = System.currentTimeMillis()
        val stamp = synchronized(nameLock) { nameFormatter.format(Date(now)) }
        val file = File(dir, "crash-" + stamp + ".txt")
        val text = buildString {
            append("time: ").append(Date(now)).append('\n')
            append("version: ").append(versionName(context)).append('\n')
            append("android: ").append(android.os.Build.VERSION.RELEASE)
            append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
            append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
            append(android.os.Build.MODEL).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append('\n')
            append(throwable.stackTraceToString())
        }.take(MAX_FILE_BYTES)
        runCatching { file.writeText(text) }
        // 同时追加到主日志，便于用户只导出一份文件就能定位
        runCatching { AppLogger.logError("Crash", "未捕获异常: " + throwable.message, throwable) }
        runCatching { AppLogger.flush() }
        prune(dir)
    }

    private fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 只保留最近 [MAX_RECORDS] 条；崩溃时也要执行，故只做删除不排序输出。 */
    private fun prune(dir: File) {
        val files = runCatching { dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } }
            .getOrNull()
            ?.sortedByDescending { it.name }
            ?: return
        files.drop(MAX_RECORDS).forEach { runCatching { it.delete() } }
    }

    /** 最近一次崩溃；null 表示没有记录。用于启动时提示「上次异常退出」。 */
    fun latest(): CrashRecord? = records().firstOrNull()

    /**
     * 取出「尚未告知过用户」的最近一次崩溃。
     *
     * 用文件名做已读标记：崩溃文件名带时间戳，天然唯一，不必额外生成 id。
     * 每次只提示一次，避免每次启动都弹。
     */
    fun takeUnseen(acknowledgedName: String?): CrashRecord? {
        val latest = latest() ?: return null
        return if (latest.file.name == acknowledgedName) null else latest
    }

    fun records(): List<CrashRecord> {
        val dir = crashDir ?: return emptyList()
        val files = runCatching { dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } }
            .getOrNull()
            ?.sortedByDescending { it.name }
            ?: return emptyList()
        return files.mapNotNull { file ->
            val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            CrashRecord(
                file = file,
                occurredAt = runCatching { file.lastModified() }.getOrDefault(0L),
                versionName = text.lineSequence()
                    .firstOrNull { it.startsWith("version: ") }
                    ?.removePrefix("version: ")
                    ?.trim()
                    .orEmpty(),
                device = text.lineSequence()
                    .firstOrNull { it.startsWith("device: ") }
                    ?.removePrefix("device: ")
                    ?.take(HEADER_DEVICE_MAX)
                    ?.trim()
                    .orEmpty(),
                // 预览取堆栈第一行（异常类名与消息），头部字段都是 key: value 形式
                preview = text.lineSequence()
                    .firstOrNull { it.contains("Exception") || it.contains("Error") }
                    ?.trim()
                    ?.take(HEADER_DEVICE_MAX)
                    .orEmpty()
            )
        }
    }

    /** 读取单条完整内容，供导出。 */
    fun read(file: File): String = runCatching { file.readText() }.getOrDefault("")

    fun clear() {
        val dir = crashDir ?: return
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** 全部记录合并后的文本，供「一键导出」。 */
    fun exportAll(): String {
        val list = records()
        if (list.isEmpty()) return ""
        return list.joinToString("\n\n" + "=".repeat(40) + "\n\n") { read(it.file) }
    }
}
