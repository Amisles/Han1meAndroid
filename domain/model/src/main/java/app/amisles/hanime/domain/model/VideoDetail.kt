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
    // 详情页 CSRF Token
    val csrfToken: String = "",
    // 当前评论数
    val commentCount: Int = 0,
    // 当前登录用户的数字 ID（从详情页解析，用于评论/点赞等需要用户标识的接口）
    val currentUserId: String = "",
    // 订阅作者相关字段（由详情页 #video-subscribe-form 解析）
    // 被订阅作者的数字 ID；缺表单时回退 authorPageUrl 的 /user/{id}
    val subscribeArtistId: String = "",
    // 当前登录用户的数字 ID；缺表单时回退 currentUserId
    val subscribeUserId: String = "",
    // 订阅状态，"" = 未订阅，"1" = 已订阅（与官网 subscribe-status 一致）
    val subscribeStatus: String = ""
)

/** 订阅/取消订阅接口（官网 /subscribe）的返回结果：新订阅状态 + 新的 CSRF Token。 */
data class SubscribeResult(
    val subscribeStatus: String = "",
    val csrfToken: String = ""
)