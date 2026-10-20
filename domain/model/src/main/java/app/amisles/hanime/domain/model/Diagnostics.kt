package app.amisles.hanime.domain.model

/** 站点诊断项。 */
enum class DiagnosticType {
    DNS,
    CONNECTIVITY,
    SSL_CERTIFICATE,
    MIRROR_STATUS,
    LOGIN_STATUS
}

/** 单项诊断的状态。 */
enum class DiagnosticStatus {
    /** 检测通过 */
    OK,
    /** 检测失败 */
    FAIL,
    /** 检测中 */
    RUNNING,
    /** 未检测（如未登录时跳过登录检测） */
    SKIPPED
}

/**
 * 一项检测的结果。
 *
 * 位于 domain：诊断页（feature-settings）只负责展示，不应为了渲染几行文字就依赖
 * `:data` 的具体诊断器。
 */
data class DiagnosticResult(
    val type: DiagnosticType,
    val status: DiagnosticStatus,
    val title: String,
    val detail: String,
    /** 请求耗时（毫秒），仅 OK/FAIL 状态有意义 */
    val latencyMs: Long? = null,
    val suggestion: String? = null
)
