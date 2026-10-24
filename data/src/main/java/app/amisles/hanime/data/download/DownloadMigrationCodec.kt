package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 迁移包内的清单条目：只带恢复任务必需的信息，直链带时效签名故原样保留。 */
internal data class MigratedTask(
    val id: Int,
    val title: String,
    val quality: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val filePath: String,
    val thumbnailUrl: String,
    val videoId: String,
    /** 续传位图文件名（不含目录），导入时与新目录拼接。 */
    val partmapName: String?
)

/**
 * 下载列表迁移的编解码。
 *
 * 与备份包分开：迁移只需要「任务 + 续传位图」，不含收藏、历史等数据，
 * 且位图是二进制，不能塞进纯 JSON 备份。
 */
internal object DownloadMigrationCodec {

    private const val VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_TASKS = "tasks"

    fun encode(tasks: List<DownloadTask>, partmapExists: (String) -> Boolean): String {
        val array = JSONArray()
        tasks.forEach { task ->
            // 没有位图的条目无法续传，导出也无意义；只迁移有进度的任务
            if (!partmapExists(task.filePath)) return@forEach
            val partmap = File(task.filePath + PARTMAP_SUFFIX)
            array.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("quality", task.quality)
                    put("url", task.url)
                    put("totalBytes", task.totalBytes)
                    put("downloadedBytes", task.downloadedBytes)
                    put("filePath", task.filePath)
                    put("thumbnailUrl", task.thumbnailUrl)
                    put("videoId", task.videoId)
                    put("partmapName", partmap.name)
                }
            )
        }
        return JSONObject().apply {
            put(KEY_VERSION, VERSION)
            put(KEY_TASKS, array)
        }.toString()
    }

    /** 解析清单；版本高于当前直接拒绝，避免用旧版应用读到不认识的字段后写出错误状态。 */
    fun decode(raw: String): List<MigratedTask> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val version = root.optInt(KEY_VERSION, 0)
        if (version <= 0 || version > VERSION) return emptyList()
        val array = root.optJSONArray(KEY_TASKS) ?: return emptyList()
        val out = ArrayList<MigratedTask>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = o.optString("url")
            val title = o.optString("title")
            if (url.isBlank() || title.isBlank()) continue
            out += MigratedTask(
                id = o.optInt("id"),
                title = title,
                quality = o.optString("quality"),
                url = url,
                totalBytes = o.optLong("totalBytes"),
                downloadedBytes = o.optLong("downloadedBytes"),
                filePath = o.optString("filePath"),
                thumbnailUrl = o.optString("thumbnailUrl"),
                videoId = o.optString("videoId"),
                partmapName = o.optString("partmapName").takeIf { it.isNotBlank() }
            )
        }
        return out
    }

    /** 导入后统一落到 PAUSED：新设备上需要用户确认路径后再手动继续。 */
    fun toTask(entry: MigratedTask, newFilePath: String): DownloadTask = DownloadTask(
        id = entry.id,
        title = entry.title,
        quality = entry.quality,
        url = entry.url,
        totalBytes = entry.totalBytes,
        downloadedBytes = entry.downloadedBytes,
        status = DownloadStatus.PAUSED,
        filePath = newFilePath,
        thumbnailUrl = entry.thumbnailUrl,
        videoId = entry.videoId
    )
}
