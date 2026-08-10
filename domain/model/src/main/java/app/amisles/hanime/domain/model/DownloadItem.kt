package app.amisles.hanime.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class DownloadQuality(
    val quality: String,
    val resolution: String,
    val fileType: String,
    val fileSize: String,
    val downloadUrl: String
)

data class DownloadTask(
    val id: Int,
    val title: String,
    val quality: String,
    val url: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val filePath: String = "",
    val thumbnailUrl: String = "",
    // F2：所属视频 id，去重/状态判定以 videoId 为准（CDN 直链不含 id 时旧逻辑误判）
    val videoId: String = "",
    // C3：失败原因细分，供 UI 展示（进程重启后保留）
    val errorMessage: String = ""
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(tableName = "download_tasks")
data class DownloadEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val quality: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val filePath: String,
    val thumbnailUrl: String = "",
    val videoId: String = "",
    val errorMessage: String = ""
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long
)