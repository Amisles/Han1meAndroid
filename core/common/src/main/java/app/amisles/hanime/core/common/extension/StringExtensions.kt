package app.amisles.hanime.core.common.extension

fun String?.ifBlankOrNull(): String? = if (this.isNullOrBlank()) null else this

fun String.truncate(maxLength: Int): String {
    return if (length <= maxLength) this else take(maxLength) + "..."
}

fun String.removeHtmlTags(): String {
    return replace(Regex("<[^>]*>"), "")
}