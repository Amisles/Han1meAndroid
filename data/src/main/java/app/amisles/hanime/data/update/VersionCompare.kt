package app.amisles.hanime.data.update

/**
 * 语义化版本比较（宽松版，只覆盖本项目实际会遇到的形态）。
 *
 * 之所以不引第三方库：发布标签的形态由我们自己控制，常见的只有
 * `1.6.0` / `v1.6.0` / `1.7` 这几种；专用库会为了它的完整语义（预发布标签排序、
 * build metadata）引入额外依赖与不可控行为，而这里只需要「谁更新」。
 *
 * 支持：
 * - 可选前缀 `v` / `V`
 * - 2 段或 3 段数字（`1.7` 等价于 `1.7.0`）
 * - 忽略 `+` 之后的 build metadata 与 `-` 之后的预发布标签（由调用方单独判定预发布）
 *
 * 非法输入返回 null，由调用方决定如何处理（通常视为「无法比较」，跳过该发行版）。
 */
internal object VersionCompare {

    /** 解析为各段数字；无法解析时返回 null。 */
    fun parse(raw: String): List<Int>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // 去掉 v 前缀
        val withoutPrefix = trimmed.removePrefix("v").removePrefix("V")
        // 去掉 build metadata（1.2.3+build.5）与预发布标签（1.2.3-beta.1）
        val core = withoutPrefix.takeWhile { it != '+' && it != '-' }
        if (core.isEmpty()) return null
        val parts = core.split('.')
        if (parts.isEmpty()) return null
        val numbers = ArrayList<Int>(parts.size)
        for (part in parts) {
            // 空段（如 "1..2"）视为非法，避免被宽松地当成 0
            if (part.isEmpty()) return null
            // 只接受纯数字；带字母（如 "1.6.0a"）视为无法比较
            if (!part.all { it in '0'..'9' }) return null
            val value = part.toIntOrNull() ?: return null
            numbers.add(value)
        }
        return numbers
    }

    /**
     * 比较两个版本。
     *
     * @return 正数表示 [a] 比 [b] 新，负数表示旧，0 表示相同。
     *   任一无法解析时返回 null —— 调用方不应把「无法比较」当成「相同」。
     */
    fun compare(a: String, b: String): Int? {
        val pa = parse(a) ?: return null
        val pb = parse(b) ?: return null
        // 段数不同时按 0 补齐：1.7 == 1.7.0
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    /** [candidate] 是否比 [current] 更新。无法比较时返回 false（宁可不提示，也不误报）。 */
    fun isNewer(candidate: String, current: String): Boolean =
        (compare(candidate, current) ?: 0) > 0
}
