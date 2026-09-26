package app.amisles.hanime.data.download

import java.io.File

/**
 * 落盘文件名/目录工具：按 UTF-8 字节预算截断文件名、校验目录可写。
 */
/**
 * 按 UTF-8 字节预算截断文件名主体。
 *
 * ext4 等文件系统单个文件名上限 255 字节，中文标题按每字 3 字节极易越界（越界时
 * createNewFile 抛 ENAMETOOLONG）。尾部 `_<videoId>.mp4` 需完整保留，故预算从尾部反推。
 */
internal fun clampFileNameBase(baseName: String, uniqueSuffix: String): String {
    val tailBytes = "_$uniqueSuffix.mp4".toByteArray(Charsets.UTF_8).size
    val budget = (MAX_FILE_NAME_BYTES - tailBytes).coerceAtLeast(16)
    if (baseName.toByteArray(Charsets.UTF_8).size <= budget) return baseName
    val sb = StringBuilder()
    var used = 0
    for (ch in baseName) {
        val len = ch.toString().toByteArray(Charsets.UTF_8).size
        if (used + len > budget) break
        sb.append(ch)
        used += len
    }
    return sb.toString()
}

internal fun ensureDirWritable(dir: File): Boolean {
    return runCatching {
        if (!dir.exists()) dir.mkdirs()
        dir.exists() && dir.isDirectory && dir.canWrite()
    }.getOrDefault(false)
}
