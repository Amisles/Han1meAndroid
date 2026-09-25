package app.amisles.hanime.domain.model

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
    // 所属视频 id，去重 / 状态判定以 videoId 为准
    val videoId: String = "",
    // 失败原因细分，供 UI 展示（进程重启后保留）
    val errorMessage: String = ""
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}
