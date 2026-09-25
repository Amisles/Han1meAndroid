package app.amisles.hanime.domain.model

/**
 * 收藏的视频（领域模型）。
 *
 * 持久化由 `:data` 的 `FavoriteVideoEntity` 负责，本类不再携带 Room 注解：
 * 领域层不应感知持久化框架，否则 `:domain:model` 无法作为纯 Kotlin 模块被复用与快速测试。
 */
data class FavoriteVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val likeRate: String,
    val viewCount: String,
    val createdAt: Long
)
