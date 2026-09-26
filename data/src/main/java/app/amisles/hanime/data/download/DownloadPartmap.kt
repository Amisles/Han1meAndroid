package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadTask
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 分块续传位图表的读写与分块几何推导。
 *
 * 位图表是分块续传的唯一依据：写入须先写 .tmp + fsync 再原子 rename，读取须对块数做上界钳制，
 * 推导续传起点前须先与磁盘实际长度对齐（位图说完成但数据不在，必须清位重下）。
 */
/**
 * 把位图表与实际文件长度对齐：位图标记完成、但区间末尾字节尚未落盘的块一律清位重下。
 * totalBytes 未知（<=0）时无法推导块边界，保持原样。
 */
internal fun sanitizePartmap(task: DownloadTask, file: File, chunkCount: Int, bitmap: BooleanArray): BooleanArray {
    val total = task.totalBytes
    if (total <= 0) return bitmap
    val fileLen = runCatching { file.length() }.getOrDefault(0L)
    var cleared = 0
    val sanitized = BooleanArray(chunkCount) { i ->
        val ok = bitmap[i] && chunkEndExclusive(i, chunkCount, total) <= fileLen
        if (!ok && bitmap[i]) cleared++
        ok
    }
    if (cleared > 0) {
        AppLogger.log(
            "DownloadManager",
            "续传一致性校验：$cleared/$chunkCount 个已完成块的数据不在磁盘上" +
                "(fileLen=$fileLen, total=$total)，已清位重下"
        )
    }
    return sanitized
}

/** 第 index 块的「区间末尾 + 1」，即该块完成时文件长度应达到的下界。 */
private fun chunkEndExclusive(index: Int, chunkCount: Int, totalBytes: Long): Long {
    if (chunkCount <= 0) return 0L
    return if (index == chunkCount - 1) totalBytes else (index + 1) * (totalBytes / chunkCount)
}

/** 向上取整的整数除法（分块数推导用，避免浮点误差）。 */
internal fun ceilDiv(value: Long, divisor: Long): Long {
    if (divisor <= 0L) return 0L
    return (value + divisor - 1L) / divisor
}

/**
 * 已完成块的「连续前缀」字节数。
 *
 * 降级单连接续传时只能从一段无空洞的前缀处接续（单连接顺序写，起点若落在有空洞的位置，
 * 空洞会永远保留）。块边界复用 [chunkEndExclusive]，与分块布局同源。
 */
internal fun contiguousDoneBytes(chunkDone: BooleanArray, chunkCount: Int, totalBytes: Long): Long {
    var prefix = 0L
    for (i in 0 until chunkCount) {
        if (!chunkDone[i]) break
        prefix = chunkEndExclusive(i, chunkCount, totalBytes)
    }
    return prefix
}

/**
 * 写位图表，返回是否成功。
 *
 * 先写同名 `.tmp` 并 flush + fsync 落盘，再用 [moveAtomically] 原子替换，避免进程在写入中途被杀留下半截内容。
 *
 * 注意不能用「先 delete 再 renameTo」：两步之间存在窗口，进程恰在此刻被杀会同时失去 `.partmap`
 * （只剩 `.partmap.tmp`），下次续传将退化为按稀疏文件的 file.length() 做单连接续传 —— 空洞永远
 * 不会被回填，而收尾长度校验仍会通过，静默产出「能播但花屏」的损坏文件。
 */
internal fun writePartmap(file: File, chunkCount: Int, done: BooleanArray): Boolean {
    val payload = "$chunkCount\n${done.joinToString("") { if (it) "1" else "0" }}"
    return runCatching {
        val tmp = File(file.absolutePath + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.flush()
            // fsync 保证数据先于 rename 落盘，否则崩溃后可能出现「新名字 + 空内容」
            out.fd.sync()
        }
        moveAtomically(tmp, file)
    }.onFailure { e ->
        AppLogger.logError("DownloadManager", "位图表写入失败(${file.name}): ${e.message}", e)
    }.isSuccess
}

/**
 * 原子替换目标文件。优先 [StandardCopyOption.ATOMIC_MOVE]（POSIX rename(2)，覆盖语义原子）；
 * 个别文件系统不支持时退化为 [StandardCopyOption.REPLACE_EXISTING]。
 */
private fun moveAtomically(src: File, dst: File) {
    try {
        Files.move(
            src.toPath(),
            dst.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (e: AtomicMoveNotSupportedException) {
        AppLogger.log("DownloadManager", "文件系统不支持原子移动，退化为非原子替换: ${dst.name} (${e.message})")
        Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun readPartmap(file: File): Pair<Int, BooleanArray?> {
    return runCatching {
        val lines = file.readLines()
        val cc = lines.getOrNull(0)?.toIntOrNull() ?: return@runCatching (0 to null)
        // 位图表是磁盘上的外部输入：块数行若损坏可能是个极大值，先做上界钳制再分配数组，避免 OOM
        if (cc <= 0 || cc > MAX_PARTMAP_CHUNKS) return@runCatching (0 to null)
        val bits = lines.getOrNull(1) ?: return@runCatching (0 to null)
        val arr = BooleanArray(cc) { i -> bits.getOrNull(i) == '1' }
        cc to arr
    }.getOrDefault(0 to null)
}
