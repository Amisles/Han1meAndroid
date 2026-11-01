package app.amisles.hanime.domain.model

data class WatchLaterVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val addedAt: Long
)
