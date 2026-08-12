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
    val currentUserId: String = "",
    // 订阅作者相关字段（由详情页 #video-subscribe-form 解析）
    // subscribeArtistId：被订阅作者的数字 ID（subscribe-artist-id）；缺表单时回退 authorPageUrl 的 /user/{id}
    val subscribeArtistId: String = "",
    // subscribeUserId：当前登录用户的数字 ID（subscribe-user-id）；缺表单时回退 currentUserId
    val subscribeUserId: String = "",
    // subscribeStatus：订阅状态，"" = 未订阅，"1" = 已订阅（与官网 subscribe-status 一致）
    val subscribeStatus: String = ""
)

/**
 * 订阅/取消订阅接口的返回结果。
 *
 * 官网 /subscribe 返回 JSON：{"subscribeBtn": "<更新后的订阅表单 HTML>", "csrf_token": "..."}
 * - subscribeStatus：从 subscribeBtn 内 input[name="subscribe-status"] 解析出的新状态（"" / "1"）
 * - csrfToken：接口回传的新 CSRF Token，可用于刷新详情页后续请求的令牌
 */
data class SubscribeResult(
    val subscribeStatus: String = "",
    val csrfToken: String = ""
)