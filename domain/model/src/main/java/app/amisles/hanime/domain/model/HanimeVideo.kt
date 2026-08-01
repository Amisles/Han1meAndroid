package app.amisles.hanime.domain.model

data class HanimeVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val likeRate: String,
    val viewCount: String,
    val author: String,
    val publishTime: String,
    val videoUrl: String
)