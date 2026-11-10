package app.amisles.hanime.domain.result

/**
 * 一份「即将保存为登录态」的会话 Cookie 的校验结果。
 *
 * 站点对游客同样下发 laravel_session，仅凭「Cookie 里有 session 字段」无法区分游客与登录用户；
 * 早期版本正是据此判定成功，结果把游客会话存成了登录态（界面显示登录却处处报错）。
 * 因此判定必须回落到服务端：拿这份 Cookie 请求一次页面，能解析出当前用户才算真登录。
 */
sealed interface WebLoginVerification {

    /** 已确认登录：站点认出了具体用户。 */
    data class Verified(val userId: String) : WebLoginVerification

    /** 站点明确表示这份 Cookie 仍是游客会话（没有当前用户），不应保存为登录态。 */
    data object NotLoggedIn : WebLoginVerification

    /** 校验请求本身没走通（断网、人机校验拦截等），无法判断真假；此时只能放行并让后续请求自行失败。 */
    data object Inconclusive : WebLoginVerification
}
