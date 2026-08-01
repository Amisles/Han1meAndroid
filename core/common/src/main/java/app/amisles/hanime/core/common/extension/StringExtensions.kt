package app.amisles.hanime.core.common.extension

/**
 * String 扩展函数
 */

/**
 * 如果字符串为空白则返回 null
 */
fun String?.ifBlankOrNull(): String? = if (this.isNullOrBlank()) null else this

/**
 * 安全截取字符串
 */
fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength) + "..."
}

/**
 * 移除 HTML 标签
 */
fun String.removeHtmlTags(): String {
    return replace(Regex("<[^>]*>"), "")
}