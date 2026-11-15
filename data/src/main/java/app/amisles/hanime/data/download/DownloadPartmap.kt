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
 * 除「已完成块」位图外，位图还携带各块块内已写字节数：块完成粒度的检查点会让暂停后恢复把
 * 未完成块整体重下，暂停发生在首个块内时进度直接归零，表现为「从头开始」。
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

/**
 * 把块内已写字节数与实际文件长度对齐：字节区间末尾超出磁盘长度的块一律清零，从块首重写。
 * 已完成块的块内字节数无意义（恢复时按整块长度预置），返回 0。
 */
internal fun sanitizePartials(
    task: DownloadTask,
    file: File,
    chunkCount: Int,
    bitmap: BooleanArray,
    partial: LongArray?
): LongArray {
    val total = task.totalBytes
    val out = LongArray(chunkCount)
    if (total <= 0 || partial == null) return out
    val fileLen = runCatching { file.length() }.getOrDefault(0L)
    for (i in 0 until chunkCount) {
        if (bitmap[i]) continue
        val bytes = partial.getOrElse(i) { 0L }
        // 超过块长的值视为位图表损坏：钳制成整块完成会让未经校验的磁盘区间被当作有效数据
        if (bytes <= 0L || bytes > chunkLength(i, chunkCount, total)) continue
        if (chunkStartOffset(i, chunkCount, total) + bytes <= fileLen) out[i] = bytes
    }
    return out
}

/** 第 index 块的起始偏移。 */
internal fun chunkStartOffset(index: Int, chunkCount: Int, totalBytes: Long): Long =
    if (chunkCount <= 0) 0L else index * (totalBytes / chunkCount)

/** 第 index 块的长度：末块吃掉整除后的全部余数。 */
internal fun chunkLength(index: Int, chunkCount: Int, totalBytes: Long): Long {
    if (chunkCount <= 0) return 0L
    return if (index == chunkCount - 1) {
        totalBytes - chunkStartOffset(index, chunkCount, totalBytes)
    } else {
        totalBytes / chunkCount
    }
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
 * 写位图表，返回是否成功。
 *
 * 格式为三行：块数、已完成块位图、各块块内已写字节数（逗号分隔，与位图等长）。
 * 第三行是暂停检查点的关键——只记位图的话，未完成块的块内进度会在恢复时整体丢弃；
 * 读取侧把缺行按全 0 兼容旧格式。块内字节数只增不减且写入后即落盘（RandomAccessFile 无缓冲），
 * 快照不会大于磁盘实际长度，校验只用于防外部截断。
 *
 * 先写同名 `.tmp` 并 flush + fsync 落盘，再用 [moveAtomically] 原子替换，避免进程在写入中途被杀留下半截内容。
 *
 * 注意不能用「先 delete 再 renameTo」：两步之间存在窗口，进程恰在此刻被杀会同时失去 `.partmap`
 * （只剩 `.partmap.tmp`），下次续传将退化为按稀疏文件的 file.length() 做单连接续传 —— 空洞永远
 * 不会被回填，而收尾长度校验仍会通过，静默产出「能播但花屏」的损坏文件。
 */
internal fun writePartmap(file: File, chunkCount: Int, done: BooleanArray, partial: LongArray): Boolean {
    val payload = buildString {
        append(chunkCount).append('\n')
        append(done.joinToString("") { if (it) "1" else "0" }).append('\n')
        append(partial.take(chunkCount).joinToString(","))
    }
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

internal fun readPartmap(file: File): Triple<Int, BooleanArray?, LongArray?> {
    return runCatching {
        val lines = file.readLines()
        val cc = lines.getOrNull(0)?.toIntOrNull() ?: return@runCatching Triple(0, null, null)
        // 位图表是磁盘上的外部输入：块数行若损坏可能是个极大值，先做上界钳制再分配数组，避免 OOM
        if (cc <= 0 || cc > MAX_PARTMAP_CHUNKS) return@runCatching Triple(0, null, null)
        val bits = lines.getOrNull(1) ?: return@runCatching Triple(0, null, null)
        val arr = BooleanArray(cc) { i -> bits.getOrNull(i) == '1' }
        // 旧版位图只有两行；缺第三行按「无块内进度」处理，不得当作损坏
        val partials = lines.getOrNull(2)?.let { line ->
            val raw = line.split(',')
            LongArray(cc) { i -> raw.getOrNull(i)?.toLongOrNull() ?: 0L }
        }
        Triple(cc, arr, partials)
    }.getOrDefault(Triple(0, null, null))
}
