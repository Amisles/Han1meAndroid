package app.amisles.hanime.domain.model

/**
 * 登录会话 Cookie 的原文。
 *
 * 刻意用值类包一层而不是裸 String：会话凭据在整个工程里流转，裸 String 很容易在
 * 日志、toString、数据类 copy 中被无意打印出来；包一层后「这是敏感值」在类型上可见。
 *
 * 注意：它**不是**安全存储 —— 加密与域名绑定由实现层负责，本类型只表达语义。
 */
@JvmInline
value class SessionCookie(val raw: String) {
    override fun toString(): String = "SessionCookie(***)"

    val isBlank: Boolean get() = raw.isBlank()
}
