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
    val thumbnailUrl: String = ""
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
    val thumbnailUrl: String = ""
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long
)