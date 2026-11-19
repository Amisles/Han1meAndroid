package app.amisles.hanime.domain.result

/**
 * 数据层唯一允许的"面向用户失败"表达：只声明失败类别，文案由界面按当前语言渲染。
 *
 * 不要在这里塞中文句子 —— 那会让英/繁/日 用户看到中文，也让提示无法被测试。
 */
class AppException(
    val code: DomainError,
    override val cause: Throwable? = null
) : Exception(code.name, cause)
