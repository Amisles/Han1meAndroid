package app.amisles.hanime.domain.model

/**
 * 评论回复数据模型
 *
 * 解析自官网 loadReply 接口返回的 HTML。
 * 回复内容可能以 "@用户名 " 开头（回复其他回复时），此时 replyTo 字段保存被回复的用户名；
 * 直接回复评论时 replyTo 为 null。
 */
data class Reply(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val time: String,
    val content: String,
    val likeCount: Int,
    /** 被回复的用户名，null 表示直接回复评论 */
    val replyTo: String?
)
