package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.SessionCookie
import kotlinx.coroutines.flow.StateFlow

/**
 * 登录会话：Cookie 的持有、持久化与清除。
 *
 * 与 [SettingsRepository] 分开的理由是**安全边界**：会话凭据必须与签发它的域名绑定，
 * 注入时只能发回该域名；而设置项（语言、主题）可以随意修改且不影响凭据。
 * 把二者放在同一个 object 上，容易演变成「改了 baseUrl 就顺手把 Cookie 发到新站点」。
 */
interface SessionRepository {
    val isLoggedIn: Boolean
    val loginStateFlow: StateFlow<Boolean>

    /** 登录 Cookie 原文。 */
    val loginCookie: SessionCookie
    val loginCookieFlow: StateFlow<SessionCookie>

    val cloudFlareCookie: SessionCookie

    /** 登录 Cookie 的来源域名（小写）；为空表示未知，此时不会向任何主机注入。 */
    val loginCookieDomain: String

    /** 当前登录用户的数字 ID（评论 / 点赞接口需要）。 */
    val savedUserId: String
    val savedUserIdFlow: StateFlow<String>
    fun saveUserId(id: String)

    /**
     * 保存登录态。
     *
     * @param sourceDomain 实际完成登录的站点 host；会话只会注入回该域名。
     */
    fun saveLogin(cookieString: String, userId: String? = null, sourceDomain: String? = null)

    fun saveCloudFlareCookie(cookieString: String, sourceDomain: String? = null)

    /** 清除全部会话凭据（Cookie、来源域名、用户 ID）。 */
    fun logout()

    /** 从 Cookie 中提取用于展示的账号标识（邮箱前缀或用户 ID）。 */
    fun extractDisplayEmail(): String
}
