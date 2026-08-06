package app.amisles.hanime.domain.model

/**
 * 视频评论数据模型
 *
 * 解析自官网 loadComment API 返回的 HTML。
 * 每条评论包含用户名、头像、时间、内容、点赞数和回复数。
 * 回复（二级评论）需单独请求 loadReply 接口，此处不包含。
 */
data class Comment(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val time: String,
    val content: String,
    val likeCount: Int,
    val replyCount: Int
)
