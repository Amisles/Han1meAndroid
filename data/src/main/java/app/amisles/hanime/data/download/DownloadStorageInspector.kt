package app.amisles.hanime.data.download

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.DownloadStorageInfo
import java.io.File

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
        val files = runCatching { dir.listFiles() }.getOrNull().orEmpty()
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

    /** 删除无人引用的位图文件，返回释放的字节数。 */
    fun clearOrphanPartmaps(dir: File, activeBasePaths: Set<String>): Long {
        val files = runCatching { dir.listFiles() }.getOrNull().orEmpty()
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
