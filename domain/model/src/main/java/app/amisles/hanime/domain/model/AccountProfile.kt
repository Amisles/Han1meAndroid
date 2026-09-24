package app.amisles.hanime.domain.model

/** 账户资料页（/user/{id}/edit）解析结果：用户名、登录电邮与页面 CSRF Token。 */
data class AccountProfile(
    val name: String,
    val email: String,
    val csrfToken: String
)
