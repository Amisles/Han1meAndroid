package app.amisles.hanime.data.download

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.DownloadStorageInfo
import java.io.File

/** 目录扫描到的、尚未登记为任务的成品文件。 */
internal data class DiscoveredDownload(
    val videoId: String,
    val title: String,
    val quality: String,
    val filePath: String,
    val totalBytes: Long
)

/**
 * 列出目录内容。
 *
 * Android 11+ 的分区存储会按应用授予的访问范围过滤结果：应用自己写入的文件始终可见，
 * 但卸载重装后由「上一个安装」写入的文件不再属于本应用，`listFiles()` 可能返回空数组。
 * 这是识别不到历史下载的主要成因，调用方需能区分「目录不存在」与「目录存在但读不到」，
 * 故这里不吞掉 null（读不到）与空数组（确实没有文件）的差别。
 */
internal fun listFilesOrEmpty(dir: File): Array<File> =
    runCatching { dir.listFiles() }.getOrNull() ?: emptyArray()

/** 目录可读性诊断：用于向用户说明「识别不到」是权限问题还是真的没有文件。 */
internal enum class DirectoryAccess {
    MISSING,
    DENIED,
    READABLE
}

internal fun probeAccess(dir: File): DirectoryAccess = when {
    !dir.exists() -> DirectoryAccess.MISSING
    !dir.isDirectory -> DirectoryAccess.MISSING
    !dir.canRead() -> DirectoryAccess.DENIED
    else -> DirectoryAccess.READABLE
}

/** 存储面板的数据来源：目录解析、用量统计与孤儿位图清理。 */
internal object DownloadStorageInspector {

    fun currentDirectory(appExternalFilesDir: File?): File {
        val custom = Preferences.downloadStoragePath
        if (custom.isNotBlank()) {
            val dir = File(custom)
            if (ensureDirWritable(dir)) return dir
        }
        return resolveDefaultDownloadDir(appExternalFilesDir)
    }

    /**
     * 统计目录用量。
     *
     * 只做一次浅层遍历：下载目录不嵌套子目录，逐层递归在任务多时会造成明显卡顿，
     * 而收益为零。位图按后缀单独归类，供「清理孤儿」使用。
     *
     * @param activeBasePaths 仍被任务引用的文件路径集合（不含 .partmap 后缀）。
     */
    fun inspect(dir: File, activeBasePaths: Set<String>): DownloadStorageInfo {
        val files = listFilesOrEmpty(dir)
        var completed = 0L
        var partial = 0L
        var orphanCount = 0
        var orphanBytes = 0L
        for (file in files) {
            if (!file.isFile) continue
            if (file.name.endsWith(PARTMAP_SUFFIX)) {
                val owner = file.absolutePath.removeSuffix(PARTMAP_SUFFIX)
                if (owner !in activeBasePaths) {
                    orphanCount++
                    orphanBytes += file.length()
                }
                partial += file.length()
            } else if (file.name.endsWith(".mp4")) {
                completed += file.length()
            }
        }
        return DownloadStorageInfo(
            directory = dir.absolutePath,
            freeBytes = runCatching { dir.usableSpace }.getOrDefault(0L),
            completedBytes = completed,
            partialBytes = partial,
            orphanPartmapCount = orphanCount,
            orphanPartmapBytes = orphanBytes
        )
    }

    /**
     * 扫描目录中已下载的 mp4，供卸载重装后重建任务列表。
     *
     * 文件名由 [app.amisles.hanime.data.download.DownloadManager.startDownload] 按
     * `标题_画质_<videoId>.mp4` 生成，videoId 固定是最后一段下划线之后、扩展名之前的内容。
     * 解析不出 videoId 的文件（用户自己放进去的、旧版命名）直接跳过，避免误收无关文件。
     * 带同名位图的文件同样跳过：下载直接写入最终 .mp4，位图存在即表示该文件尚未下完。
     *
     * @param knownNames 已有任务引用的文件名，这些文件不再重复登记。
     */
    fun scanDownloadedFiles(dir: File, knownNames: Set<String>): List<DiscoveredDownload> {
        val files = listFilesOrEmpty(dir)
        val inProgress = files
            .filter { it.name.endsWith(PARTMAP_SUFFIX) }
            .mapTo(mutableSetOf()) { it.name.removeSuffix(PARTMAP_SUFFIX) }
        val result = mutableListOf<DiscoveredDownload>()
        for (file in files) {
            if (!file.isFile || !file.name.endsWith(".mp4")) continue
            if (file.length() <= 0) continue
            if (file.name in knownNames) continue
            if (file.name in inProgress) continue
            val parsed = parseDownloadedFileName(file.name) ?: continue
            result += DiscoveredDownload(
                videoId = parsed.first,
                title = parsed.second,
                quality = parsed.third,
                filePath = file.absolutePath,
                totalBytes = file.length()
            )
        }
        return result
    }

    /** 从 `标题_画质_<videoId>.mp4` 反解 (videoId, 标题, 画质)；不合规返回 null。 */
    internal fun parseDownloadedFileName(name: String): Triple<String, String, String>? {
        val stem = name.removeSuffix(".mp4")
        val lastSep = stem.lastIndexOf('_')
        if (lastSep <= 0 || lastSep == stem.length - 1) return null
        val videoId = stem.substring(lastSep + 1)
        if (videoId.isEmpty() || !videoId.all { it.isLetterOrDigit() }) return null
        val head = stem.substring(0, lastSep)
        val qualitySep = head.lastIndexOf('_')
        if (qualitySep <= 0) return null
        val title = head.substring(0, qualitySep)
        val quality = head.substring(qualitySep + 1)
        if (title.isEmpty() || quality.isEmpty()) return null
        return Triple(videoId, title, quality)
    }

    /** 删除无人引用的位图文件，返回释放的字节数。 */
    fun clearOrphanPartmaps(dir: File, activeBasePaths: Set<String>): Long {
        val files = listFilesOrEmpty(dir)
        var freed = 0L
        for (file in files) {
            if (!file.isFile || !file.name.endsWith(PARTMAP_SUFFIX)) continue
            val owner = file.absolutePath.removeSuffix(PARTMAP_SUFFIX)
            if (owner in activeBasePaths) continue
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) freed += size
        }
        return freed
    }
}
