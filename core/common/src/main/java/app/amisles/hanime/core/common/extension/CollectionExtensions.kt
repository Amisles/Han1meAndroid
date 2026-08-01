package app.amisles.hanime.core.common.extension

/**
 * Collection 扩展函数
 */

/**
 * 安全获取列表元素，避免 IndexOutOfBoundsException
 */
fun <T> List<T>.getOrNull(index: Int): T? {
    return if (index in indices) get(index) else null
}

/**
 * 列表是否不为空
 */
fun <T> List<T>?.isNotNullOrEmpty(): Boolean = !this.isNullOrEmpty()