package app.amisles.hanime.domain.model

/**
 * 账户资料页（/user/{id}/edit）解析结果。
 *
 * @param name 用户名称（与官网 `input[name=name]` 的 value 一致）
 * @param email 登录电邮（与官网 `input[name=email]` 的 value 一致）
 * @param csrfToken 页面内 `<meta name="csrf-token">` 的值，更新档案时随表单提交
 */
data class AccountProfile(
    val name: String,
    val email: String,
    val csrfToken: String
)
