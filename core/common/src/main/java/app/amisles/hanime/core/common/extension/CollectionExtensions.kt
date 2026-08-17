package app.amisles.hanime.core.common.extension

/**
 * 安全获取列表元素，避免 IndexOutOfBoundsException
 */
fun <T> List<T>.getOrNull(index: Int): T? {
    return if (index in indices) get(index) else null
}

fun <T> List<T>?.isNotNullOrEmpty(): Boolean = !this.isNullOrEmpty()