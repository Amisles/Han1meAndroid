package app.amisles.hanime.domain.model

data class VideoSource(
    val url: String,
    val resolution: String,
    val size: Int
)

data class PlaylistInfo(
    val title: String,
    val author: String,
    val videoCount: Int,
    val videos: List<HanimeVideo>
)

data class VideoDetail(
    val title: String,
    val posterUrl: String,
    val videoSources: List<VideoSource>,
    val defaultSourceUrl: String,
    val tags: List<String>,
    val releaseDate: String,
    val fileSize: String,
    val author: String,
    val authorAvatarUrl: String = "",
    val authorPageUrl: String = "",
    val description: String,
    val relatedVideos: List<HanimeVideo> = emptyList(),
    val playlist: PlaylistInfo? = null,
    // 详情页CSRF Token
    val csrfToken: String = "",
    // 当前评论数
    val commentCount: Int = 0,
    // 当前登录用户的数字 ID（从详情页解析，用于评论/点赞等需要用户标识的接口）
    val currentUserId: String = ""
)