package app.amisles.hanime.data.download

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadTask
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 一次续传的起点：单连接字节数、分块位图、各块块内已写字节数。 */
internal data class ResumePlan(
    val bytes: Long = 0L,
    val chunkMap: BooleanArray? = null,
    val partial: LongArray? = null
)

/**
 * 从磁盘残留推导续传起点，并维护「该文件曾分块写入」的私有标记。
 *
 * 位图表在公共下载目录，用户清理垃圾文件或删除动作被中断都会留下「无位图表、但有稀疏成品」；
 * 此时按 file.length() 做单连接续传会把空洞永久留在文件中间，而收尾只比对长度 →
 * 静默产出能播但花屏的坏文件。标记放在应用私有目录，即使位图表被外部删掉、进程重启，
 * 仍能判定「这个文件曾被分块写过，长度不可信」。
 */
internal class DownloadResumeResolver(
    context: Context
) {

    private val filesDir: File = (context.applicationContext ?: context).filesDir

    /**
     * 本进程内已判定「分块无收益」的文件（按 filePath）。
     *
     * 驱逐预算耗尽降级单连接后记入，使后续暂停/恢复不再重新进入分块路径。
     * 只记内存不落盘，进程重启后允许再试一次分块，避免一次误判导致永久降级。
     */
    val singleConnectionFiles: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun derive(task: DownloadTask): ResumePlan {
        val file = File(task.filePath)
        val partmapFile = File(task.filePath + PARTMAP_SUFFIX)
        // writePartmap 的中间文件：它的存在本身就是「上次位图表替换未完成」的证据
        val partmapTmpFile = File(task.filePath + PARTMAP_SUFFIX + ".tmp")
        if (partmapFile.exists() && file.exists()) {
            val (chunkCount, bitmap, partial) = readPartmap(partmapFile)
            if (chunkCount > 0 && bitmap != null) {
                // 位图表有效：顺手清理上次替换失败残留的 .tmp，避免孤儿文件堆积
                if (partmapTmpFile.exists()) runCatching { partmapTmpFile.delete() }
                // 位图表只是「上次写入时的意图记录」，与文件实际长度可能不一致。只有「该块区间末尾
                // 字节确实落在当前文件长度内」才承认完成，否则清位重下，避免「位图说完成、数据不在」
                // 导致收尾校验恒失败且无法自愈。块内字节数同样要与磁盘长度对齐。
                val sanitized = sanitizePartmap(task, file, chunkCount, bitmap)
                val sanitizedPartial = sanitizePartials(task, file, chunkCount, sanitized, partial)
                // 该文件已被判定分块无收益时，本应改走单连接续传。但分块写入是稀疏的：
                // contiguousDoneBytes 只保证「位图中的逻辑前缀连续」，且 sanitizePartmap 只校验
                // 各块末尾字节是否落在 file.length() 内 —— 文件中间仍可能有从未写入的空洞。
                // 若按前缀截断后单连接续写，空洞将永久保留，而收尾只比对长度，最终会得到
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
                    clearChunked(task.id)
                    return ResumePlan()
                }
                return ResumePlan(chunkMap = sanitized, partial = sanitizedPartial)
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
            clearChunked(task.id)
            return ResumePlan()
        }
        // 回退：文件级续传（单连接）或整文件重下
        if (partmapFile.exists()) {
            if (file.exists()) runCatching { file.delete() }
            runCatching { partmapFile.delete() }
            clearChunked(task.id)
            return ResumePlan()
        }

        // 位图表缺失、但本任务此前确实走过分块下载：成品可能是稀疏的（file.length() 只是最大
        // 已写偏移，其下可能有大片空洞）。按长度做单连接续传，空洞永远回填不了，而收尾只比对
        // 长度 → 静默产出「能播但花屏」的坏文件。标记在私有目录，不受公共目录里的删除动作影响。
        if (hasChunked(task.id)) {
            AppLogger.logError(
                "DownloadManager",
                "位图表缺失但该文件曾分块写入，磁盘长度不可信，丢弃后整份重下: " + task.title
            )
            if (file.exists()) runCatching { file.delete() }
            clearChunked(task.id)
            return ResumePlan()
        }

        val fileLen = if (file.exists()) file.length() else 0L
        val totalKnown = task.totalBytes > 0
        val safeResume = fileLen > 0 &&
            fileLen >= task.downloadedBytes.coerceAtLeast(0L) &&
            (!totalKnown || fileLen < task.totalBytes)
        if (safeResume) return ResumePlan(bytes = fileLen)
        if (file.exists()) runCatching { file.delete() }
        return ResumePlan()
    }

    /** 分块写入开始前打标记，成功后清除。 */
    fun markChunked(taskId: Int) {
        runCatching {
            val marker = chunkedMarkerFile(taskId)
            marker.parentFile?.mkdirs()
            marker.writeText("")
        }.onFailure { AppLogger.logError("DownloadManager", "写分块标记失败: " + it.message, it) }
    }

    fun clearChunked(taskId: Int) {
        runCatching { chunkedMarkerFile(taskId).delete() }
    }

    /** 删除任务的本地文件：正片 + 分块位图表 + 其写入中间文件。幂等，失败仅记录。 */
    fun deleteFiles(task: DownloadTask) {
        runCatching { File(task.filePath).takeIf { it.exists() }?.delete() }
        runCatching { File(task.filePath + PARTMAP_SUFFIX).delete() }
        runCatching { File(task.filePath + PARTMAP_SUFFIX + ".tmp").delete() }
        clearChunked(task.id)
    }

    private fun hasChunked(taskId: Int): Boolean =
        runCatching { chunkedMarkerFile(taskId).exists() }.getOrDefault(false)

    private fun chunkedMarkerFile(taskId: Int): File =
        File(File(filesDir, CHUNKED_MARKER_DIR), taskId.toString())

    private companion object {
        /** 分块下载标记目录（放在 filesDir 下，与公共下载目录里的位图表分开）。 */
        const val CHUNKED_MARKER_DIR = "download_chunked"
    }
}
