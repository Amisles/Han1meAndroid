package app.amisles.hanime.core.common.extension

fun String?.ifBlankOrNull(): String? = if (this.isNullOrBlank()) null else this

fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength) + "..."
}

fun String.removeHtmlTags(): String {
    return replace(Regex("<[^>]*>"), "")
}

/** 邮箱脱敏 */
fun String.maskEmail(): String {
    val at = indexOf('@')
    if (at <= 0) return "***"
    return "${substring(0, 1)}***${substring(at)}"
}

/** 密钥 / 令牌脱敏 */
fun String.maskSecret(keep: Int = 4): String {
    if (isEmpty()) return "<empty>"
    return if (length <= keep) "***" else take(keep) + "***"
}

/**
 * 日志脱敏：截断 URL 的 query / fragment 中的敏感值，保留 `scheme://host/path` 并在原分隔符后
 * 追加 `<redacted>` 标记；无 query / fragment 时原样返回。
 */
fun String.redactUrlForLog(): String {
    val cut = indexOfFirst { it == '?' || it == '#' }
    if (cut < 0) return this
    return take(cut) + if (this[cut] == '?') "?<redacted>" else "#<redacted>"
}