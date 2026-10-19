package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.AccountProfile
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.model.SubscribeResult
import app.amisles.hanime.domain.result.AppResult

/**
 * 账号与社区互动仓储：登录、账户资料、评论、回复、点赞、订阅。
 *
 * 与 [ContentRepository] 分开：这些操作全部依赖登录态（Cookie / CSRF Token），
 * 未登录时应当整体不可用；把它们混进内容接口会让"游客也能读首页"这件事在类型层面
 * 与"必须登录才能评论"纠缠在一起。
 */
interface AccountRepository {
    // ── 登录态 ──────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AppResult<String>

    /**
     * 保存 WebView / 手填登录拿到的 Cookie。
     *
     * [sourceDomain] 为实际完成登录的站点 host：会话只会注入回该域名，
     * 避免用户之后把 baseUrl 改成镜像站时把主站会话一并发送过去。
     */
    suspend fun saveLoginCookie(cookieString: String, sourceDomain: String? = null): Boolean

    fun logout()

    // ── 账户资料 ────────────────────────────────────────────────────────────
    suspend fun getAccountProfile(): AppResult<AccountProfile>
    suspend fun updateAccountProfile(name: String, email: String, csrfToken: String): AppResult<Unit>

    // ── 评论 ────────────────────────────────────────────────────────────────
    suspend fun getComments(videoId: String): AppResult<List<Comment>>
    suspend fun getReplies(commentId: String): AppResult<List<Reply>>

    /** @return Pair(新评论, 新评论总数) */
    suspend fun postComment(
        videoId: String,
        commentText: String,
        commentCount: Int,
        csrfToken: String
    ): AppResult<Pair<Comment, Int>>

    suspend fun toggleCommentLike(
        commentId: String,
        currentLikeStatus: Int,
        likeCount: Int,
        csrfToken: String
    ): AppResult<Unit>

    suspend fun replyComment(commentId: String, replyText: String, csrfToken: String): AppResult<Reply>

    // ── 订阅 ────────────────────────────────────────────────────────────────
    suspend fun toggleSubscribe(
        userId: String,
        artistId: String,
        willSubscribe: Boolean,
        csrfToken: String
    ): AppResult<SubscribeResult>
}
