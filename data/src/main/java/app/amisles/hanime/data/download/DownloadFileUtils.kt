package app.amisles.hanime.data.download

import android.os.Environment
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

/**
 * 默认下载目录：公开的「下载」文件夹 `Download/Hanime`。
 *
 * 原先落在 `getExternalFilesDir()/Downloads`（/storage/emulated/0/Android/data/<包名>/files/Downloads），
 * 卸载应用即被系统一并删除，且多数文件管理器默认看不到 —— 对用户来说等于「下载完就找不到」。
 * 改为公共 Download 目录下的 `Hanime` 子目录：Android 10+ 的分区存储允许应用直接在公共 Download
 * 集合下建目录、写文件，无需申请存储权限。
 *
 * 主外部存储未挂载或不可写时退回应用外部目录，保证下载功能仍然可用。
 */
internal fun resolveDefaultDownloadDir(appExternalFilesDir: File?): File {
    val primary = runCatching {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }.getOrNull()
    val preferred = primary?.let { File(it, DEFAULT_DOWNLOAD_SUBDIR) }
    if (preferred != null && ensureDirWritable(preferred)) return preferred
    val fallback = appExternalFilesDir?.let { File(it, DEFAULT_DOWNLOAD_SUBDIR) }
    if (fallback != null && ensureDirWritable(fallback)) return fallback
    // 极端情况（主外部存储与应用外部目录都不可用）：仍返回首选路径，由后续写文件报错走失败分支
    return preferred ?: fallback ?: File(DEFAULT_DOWNLOAD_SUBDIR)
}

/** 默认下载目录名：公共 Download 下的子目录，避免与用户其它下载混在一起。 */
internal const val DEFAULT_DOWNLOAD_SUBDIR = "Hanime"
