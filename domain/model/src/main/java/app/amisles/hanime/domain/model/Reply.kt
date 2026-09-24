package app.amisles.hanime.domain.model

/**
 * 评论回复数据模型，解析自官网 loadReply 接口返回的 HTML。
 * 回复其他回复时内容以 "@用户名 " 开头，replyTo 保存被回复用户名；直接回复评论时为 null。
 */
data class Reply(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val time: String,
    val content: String,
    val likeCount: Int,
    /** 被回复的用户名，null 表示直接回复评论。 */
    val replyTo: String?
)
