package app.amisles.hanime.domain.model

data class BatchVideoItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val duration: String = "",
    val author: String = "",
    val publishTime: String = "",
    val isSelected: Boolean = false,
    val qualities: List<DownloadQuality> = emptyList(),
    val selectedQualityIndex: Int = 0,
    val isLoadingQualities: Boolean = false,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false
)

data class UserVideoListResult(
    val videos: List<HanimeVideo>,
    val currentPage: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
    val authorName: String = "",
    val authorId: String = ""
)