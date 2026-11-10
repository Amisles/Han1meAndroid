package app.amisles.hanime.data.repository

import app.amisles.hanime.data.remote.EntryInterceptor
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.data.parser.AccountIdentityParser
import app.amisles.hanime.data.parser.AccountProfileParser
import app.amisles.hanime.data.parser.CommentParser
import app.amisles.hanime.data.parser.LoginParser
import app.amisles.hanime.data.parser.SubscribeParser
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import app.amisles.hanime.domain.model.SubscribeResult
import app.amisles.hanime.domain.model.AccountProfile
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.domain.result.WebLoginVerification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import java.io.IOException
import app.amisles.hanime.domain.repository.AccountRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val networkService: NetworkService,
    private val commentParser: CommentParser,
    private val subscribeParser: SubscribeParser,
    private val accountProfileParser: AccountProfileParser,
    private val accountIdentityParser: AccountIdentityParser
) : AccountRepository {

    override suspend fun getComments(videoId: String, page: Int): AppResult<List<Comment>> {
        return try {
            val json = networkService.fetchComments(videoId, page)
            AppLogger.log("AccountRepositoryImpl", "Comments JSON received, length: ${json.length}")
            AppResult.success(commentParser.parse(json))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in getComments: ${e.message}", e)
            AppResult.error(e.message ?: "加载评论失败", e)
        }
    }


    override suspend fun getReplies(commentId: String): AppResult<List<Reply>> {
        return try {
            val json = networkService.fetchReplies(commentId)
            AppLogger.log("AccountRepositoryImpl", "Replies JSON received, length: ${json.length}")
            AppResult.success(commentParser.parseReplies(json))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in getReplies: ${e.message}", e)
            AppResult.error(e.message ?: "加载回复失败", e)
        }
    }


    /**
     * 发送视频评论。
     *
     * @param commentCount 当前评论数（取自 VideoDetail.commentCount）
     * @param csrfToken 取自 VideoDetail.csrfToken
     * @return 成功时为新评论对象与新评论总数，外层由 AppResult 包装失败分支
     */
    override suspend fun postComment(
        videoId: String,
        commentText: String,
        commentCount: Int,
        csrfToken: String
    ): AppResult<Pair<Comment, Int>> {
        return try {
            val userId = Preferences.savedUserId.ifBlank {
                return AppResult.error("未登录，无法发表评论")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请刷新页面后重试")
            }
            val json = networkService.postComment(
                videoId = videoId,
                commentText = commentText,
                commentCount = commentCount,
                csrfToken = csrfToken,
                userId = userId
            )
            AppLogger.log("AccountRepositoryImpl", "postComment JSON received, length: ${json.length}")
            val parsed = commentParser.parsePostedComment(json)
                ?: return AppResult.error("评论发表成功但解析失败")
            AppResult.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in postComment: ${e.message}", e)
            AppResult.error(e.message ?: "发表评论失败", e)
        }
    }


    /**
     * 切换评论点赞状态（POST /commentLike，凭 like-comment-status 区分点赞与取消）。
     *
     * @param commentId 评论 ID（foreign_id）
     * @param currentLikeStatus 0=未点赞，1=已点赞（作为 like-comment-status 上传）
     * @param likeCount 当前点赞数（作为 comment-likes-count / comment-likes-sum 上传）
     * @param csrfToken 取自 VideoDetail.csrfToken
     */
    override suspend fun toggleCommentLike(
        commentId: String,
        currentLikeStatus: Int,
        likeCount: Int,
        csrfToken: String
    ): AppResult<Unit> {
        return try {
            val userId = Preferences.savedUserId.ifBlank {
                return AppResult.error("请先登录后再点赞评论")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请刷新页面后重试")
            }
            networkService.toggleCommentLike(
                commentId = commentId,
                commentLikeUserId = userId,
                currentLikeStatus = currentLikeStatus,
                likeCount = likeCount,
                csrfToken = csrfToken
            )
            AppResult.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in toggleCommentLike: ${e.message}", e)
            AppResult.error(e.message ?: "评论点赞失败", e)
        }
    }


    /**
     * 发表评论回复（POST /replyComment，凭 reply-comment-id 定位父评论）。
     *
     * @param commentId 被回复的评论 ID（reply-comment-id）
     * @param replyText 回复内容（回复其他回复时已带 "@用户名 " 前缀）
     * @param csrfToken 取自 VideoDetail.csrfToken
     */
    override suspend fun replyComment(
        commentId: String,
        replyText: String,
        csrfToken: String
    ): AppResult<Reply> {
        return try {
            if (Preferences.savedUserId.isBlank()) {
                return AppResult.error("未登录，无法回复评论")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请刷新页面后重试")
            }
            val json = networkService.replyComment(
                commentId = commentId,
                replyText = replyText,
                csrfToken = csrfToken
            )
            AppLogger.log("AccountRepositoryImpl", "replyComment JSON received, length: ${json.length}")
            val parsed = commentParser.parsePostedReply(json)
                ?: return AppResult.error("回复发表成功但解析失败")
            AppResult.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in replyComment: ${e.message}", e)
            AppResult.error(e.message ?: "回复评论失败", e)
        }
    }


    /**
     * 订阅 / 取消订阅作者（POST /subscribe，凭 subscribe-status 区分：""=订阅，"1"=取消）。
     *
     * @param artistId 被订阅作者的数字 ID（取自 VideoDetail.subscribeArtistId）
     * @param willSubscribe true=订阅，false=取消订阅
     * @param csrfToken 取自 VideoDetail.csrfToken
     * @return 订阅结果（新状态 + 回传 CSRF Token）
     */
    override suspend fun toggleSubscribe(
        userId: String,
        artistId: String,
        willSubscribe: Boolean,
        csrfToken: String
    ): AppResult<SubscribeResult> {
        return try {
            if (userId.isBlank()) {
                return AppResult.error("请先登录后再订阅作者")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请刷新页面后重试")
            }
            if (artistId.isBlank()) {
                return AppResult.error("作者 ID 缺失，无法订阅")
            }
            val json = networkService.toggleSubscribe(
                csrfToken = csrfToken,
                userId = userId,
                artistId = artistId,
                willSubscribe = willSubscribe
            )
            AppLogger.log("AccountRepositoryImpl", "toggleSubscribe JSON received, length: ${json.length}")
            val parsed = subscribeParser.parse(json)
                ?: return AppResult.error("订阅操作失败")
            AppResult.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in toggleSubscribe: ${e.message}", e)
            AppResult.error(e.message ?: "订阅操作失败", e)
        }
    }


    /**
     * 取当前登录用户的数字 ID。
     *
     * 登录 Cookie 不含用户 ID，此前只有访问过详情页（页面内渲染了当前用户）才会落盘，
     * 因此「刚登录就打开账户资料」会拿不到 ID。缺失时抓一次首页，从本人的「编辑个人档案」
     * 入口提取 ID 并落盘，之后无需再抓。
     */
    private suspend fun ensureUserId(): String {
        val cached = Preferences.savedUserId
        if (cached.isNotBlank()) return cached
        if (!Preferences.isActiveLogin) return ""
        val html = runCatching { networkService.fetchHomePageWithBaseUrl().html }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return ""
        val userId = accountIdentityParser.parseCurrentUserId(html) ?: return ""
        Preferences.saveUserId(userId)
        return userId
    }


    /**
     * 拉取账户资料（用户名称 + 电邮 + CSRF Token），GET /user/{id}/edit，
     * 由 [AccountProfileParser] 解析表单当前值。成功后顺带缓存昵称，供抽屉等处展示。
     */
    override suspend fun getAccountProfile(): AppResult<AccountProfile> {
        return try {
            val userId = ensureUserId().ifBlank {
                return AppResult.error("无法确认登录用户，请重新登录后再试")
            }
            val result = networkService.fetchAccountEditPage(userId)
            AppLogger.log("AccountRepositoryImpl", "Account edit page HTML length: ${result.html.length}")
            val profile = accountProfileParser.parseEditPage(result.html, result.baseUrl)
                ?: return AppResult.error("登录状态可能已失效，请重新登录后再试")
            Preferences.saveUserName(profile.name)
            AppResult.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in getAccountProfile: ${e.message}", e)
            AppResult.error(e.message ?: "加载账户资料失败", e)
        }
    }


    override suspend fun refreshAccountName(): Boolean =
        getAccountProfile() is AppResult.Success && Preferences.savedUserName.isNotBlank()


    /**
     * 更新账户个人档案（用户名称 + 电邮），POST /user/{id}，凭 type=profile 区分「更改密码」分支。
     *
     * @param csrfToken 账户资料页解析出的 CSRF Token
     */
    override suspend fun updateAccountProfile(
        name: String,
        email: String,
        csrfToken: String
    ): AppResult<Unit> {
        return try {
            val userId = ensureUserId().ifBlank {
                return AppResult.error("无法确认登录用户，请重新登录后再试")
            }
            if (csrfToken.isBlank()) {
                return AppResult.error("CSRF Token 缺失，请返回重试")
            }
            val code = networkService.updateAccountProfile(
                userId = userId,
                csrfToken = csrfToken,
                name = name,
                email = email
            )
            if (code in 300..399) {
                Preferences.saveUserName(name)
                AppResult.success(Unit)
            } else {
                AppResult.error("更新失败（HTTP $code）")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("AccountRepositoryImpl", "Error in updateAccountProfile: ${e.message}", e)
            AppResult.error(e.message ?: "更新账户资料失败", e)
        }
    }


    override suspend fun login(email: String, password: String): AppResult<String> {
        return runCatching {
            val page = try {
                networkService.fetchLoginPageWithBaseUrl()
            } catch (t: IOException) {
                val msg = t.message.orEmpty()
                when {
                    "resolve" in msg || "UnknownHost" in msg || "DNS" in msg ->
                        throw IllegalStateException("无法访问 Hanime1 官网（DNS 解析失败），请检查网络、开启代理/VPN，或改用 WebView 登录方式")
                    t is java.net.SocketTimeoutException || "timeout" in msg ->
                        throw IllegalStateException("连接 Hanime1 官网超时，请检查网络或改用 WebView 登录")
                    "SSL" in msg || "certificate" in msg ->
                        throw IllegalStateException("SSL 证书校验失败，请检查网络代理或改用 WebView 登录")
                    else ->
                        throw IllegalStateException("访问登录页失败：${t.message?.take(80) ?: "未知错误"}，请改用 WebView 登录")
                }
            }
            val csrf = LoginParser.parseCsrfToken(page.html)
                ?: throw IllegalStateException("无法获取 CSRF Token，请切换到 WebView 或手动 Cookie 登录")
            val result = try {
                networkService.postLoginForm(csrf, email, password)
            } catch (t: IOException) {
                val msg = t.message.orEmpty()
                when {
                    "resolve" in msg || "UnknownHost" in msg || "DNS" in msg ->
                        throw IllegalStateException("无法访问 Hanime1 官网（DNS 解析失败），请检查网络、开启代理/VPN，或改用 WebView 登录方式")
                    t is java.net.SocketTimeoutException || "timeout" in msg ->
                        throw IllegalStateException("提交登录超时，请检查网络或改用 WebView 登录")
                    else ->
                        throw IllegalStateException("提交登录失败：${t.message?.take(80) ?: "未知错误"}，请改用 WebView 登录")
                }
            }
            val mergedCookie = result.setCookies
                .map { it.substringBefore(';').trim() }
                .filter { it.contains('=') }
                .joinToString("; ")
            val isRedirect = result.code in 300..399
            val hasSession = mergedCookie.contains("laravel_session", ignoreCase = true) ||
                    mergedCookie.contains("remember_web", ignoreCase = true) ||
                    mergedCookie.contains("session", ignoreCase = true)
            if (result.code == 503) {
                throw IllegalStateException("触发了 Cloudflare 校验，请切换到 WebView 登录")
            }
            if (result.code in 200..299 && result.body != null) {
                val errorMsg = LoginParser.parseLoginFailed(result.body)
                if (errorMsg != null) throw IllegalStateException(errorMsg)
            }
            if (!isRedirect && !hasSession) {
                throw IllegalStateException("登录失败：请检查邮箱或密码，或切换到 WebView 登录")
            }
            val userId = LoginParser.parseUserIdFromSetCookies(result.setCookies)
            // 会话与「实际提交登录的站点」绑定：之后即使用户把 baseUrl 改成镜像站，也不会跨域注入
            Preferences.saveLogin(
                mergedCookie,
                userId,
                // page.baseUrl 即本次实际登录的站点（可能已切换为镜像站），以它作为会话来源
                sourceDomain = runCatching { android.net.Uri.parse(page.baseUrl).host }.getOrNull()
            )
            mergedCookie
        }.fold(
            onSuccess = { AppResult.success(it) },
            onFailure = { e ->
                if (e is CancellationException) throw e
                AppLogger.logError("AccountRepositoryImpl", "Login failed: ${e.message}", e)
                AppResult.error(e.message ?: "登录失败", e)
            }
        )
    }


    /**
     * 用站点页面的「当前用户」标识确认这份会话是否真的登录成功。
     *
     * 判据与 [ensureUserId] 完全一致：登录后的页面头部会渲染本人的「编辑个人档案」入口，
     * 解析不出这个入口说明站点仍把请求当成游客。走到这一步如果判定为游客，
     * 后续所有需要身份的功能都会失败，因此宁可让用户重登，也不能把它存成登录态。
     */
    override suspend fun verifyWebLogin(cookie: String, sourceDomain: String?): WebLoginVerification {
        val domain = sourceDomain?.trim()?.lowercase().orEmpty()
        if (domain.isEmpty() || !isOfficialDomain(domain)) {
            // 只有官方域名签发有效会话；其它站点的 Cookie 无从校验，按未登录处理
            AppLogger.w("AccountRepositoryImpl", "会话校验跳过：来源域名不是官方站点")
            return WebLoginVerification.NotLoggedIn
        }
        val safe = cookie.trim().removePrefix("Cookie:").trim()
        if (safe.isBlank()) return WebLoginVerification.NotLoggedIn
        return try {
            val html = networkService.fetchHomePageWithCookie(safe, domain)
            val userId = accountIdentityParser.parseCurrentUserId(html)
            if (userId != null) {
                AppLogger.log("AccountRepositoryImpl", "会话校验通过: userId=$userId")
                WebLoginVerification.Verified(userId)
            } else {
                AppLogger.w("AccountRepositoryImpl", "会话校验未通过：首页未渲染当前用户，仍是游客会话")
                WebLoginVerification.NotLoggedIn
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 断网、人机校验拦截等情况下无法判断真假：放行，交给后续请求自己暴露问题
            AppLogger.w("AccountRepositoryImpl", "会话校验请求失败: ${e.message}")
            WebLoginVerification.Inconclusive
        }
    }

    private fun isOfficialDomain(host: String): Boolean =
        EntryInterceptor.DEFAULT_OFFICIAL_DOMAINS.any { host == it || host.endsWith(".$it") }

    override suspend fun saveLoginCookie(
        cookieString: String,
        sourceDomain: String?,
        userId: String?
    ): Boolean {
        val safe = cookieString.trim().removePrefix("Cookie:").trim()
        if (safe.isBlank()) return false
        // sourceDomain：WebView 登录时传入实际登录站点的 host；手填 Cookie 时为空（回落到当前 baseUrl）
        Preferences.saveLogin(
            safe,
            userId = userId?.takeIf { it.isNotBlank() },
            sourceDomain = sourceDomain
        )
        return true
    }


    override fun logout() {
        Preferences.logout()
    }

}
