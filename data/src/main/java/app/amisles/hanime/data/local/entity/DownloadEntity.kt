package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask

/**
 * 下载任务表实体。
 *
 * 表名与列名与 v6 schema 完全一致（由 domain 迁入 data，仅包名与类名变化），故无需新增 Migration。
 * 实体属于持久化实现细节，不应放在 domain 层。
 */
@Entity(tableName = "download_tasks")
data class DownloadEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val quality: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    /** [app.amisles.hanime.domain.model.DownloadStatus] 的 name，反序列化依赖常量名不被混淆 */
    val status: String,
    val filePath: String,
    val thumbnailUrl: String = "",
    val videoId: String = "",
    val errorMessage: String = ""
)

fun DownloadTask.toEntity(): DownloadEntity = DownloadEntity(
    id = id,
    title = title,
    quality = quality,
    url = url,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    status = status.name,
    filePath = filePath,
    thumbnailUrl = thumbnailUrl,
    videoId = videoId,
    errorMessage = errorMessage
)

fun DownloadEntity.toTask(): DownloadTask = DownloadTask(
    id = id,
    title = title,
    quality = quality,
    url = url,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.PENDING),
    filePath = filePath,
    thumbnailUrl = thumbnailUrl,
    videoId = videoId,
    errorMessage = errorMessage
)
