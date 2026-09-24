package app.amisles.hanime.domain.model

/** 视频评论数据模型，解析自官网 loadComment API 返回的 HTML；二级回复需另行请求 loadReply。 */
data class Comment(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val time: String,
    val content: String,
    val likeCount: Int,
    val replyCount: Int,
    /** 当前用户对该评论的点赞状态：0 = 未点赞，1 = 已点赞（本地乐观更新，重载时由服务端覆盖）。 */
    val likeStatus: Int = 0
)
