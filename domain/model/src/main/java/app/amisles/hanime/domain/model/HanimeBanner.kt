package app.amisles.hanime.domain.model

data class HanimeBanner(
    val title: String,
    val author: String,
    val viewCount: String,
    val publishTime: String,
    val tags: List<String>,
    val videoUrl: String,
    val imageUrl: String = ""
)