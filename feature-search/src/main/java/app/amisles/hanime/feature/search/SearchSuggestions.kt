package app.amisles.hanime.feature.search

/** 建议里的一条标签：[value] 是官网原值（拼 `tags[]` 用），[label] 是当前语言的展示名。 */
data class SuggestionTag(val value: String, val label: String)

/**
 * 搜索建议的匹配与排序。
 *
 * 全部在本地完成：历史来自 Room 的搜索记录，标签来自内置目录，输入过程中不发任何请求。
 * 命中位置优先于出现顺序 —— 前缀命中排在包含命中之前，同档内保持数据源原有顺序
 * （历史是按最近使用倒序、标签目录按站点给定的热门程度），因此 [sortedBy] 必须保持稳定。
 */
object SearchSuggestions {

    const val MAX_HISTORY = 5
    const val MAX_TAGS = 6

    /** 0 = 前缀命中，1 = 包含命中，-1 = 未命中。 */
    private fun matchRank(query: String, text: String): Int = when {
        text.startsWith(query, ignoreCase = true) -> 0
        text.contains(query, ignoreCase = true) -> 1
        else -> -1
    }

    /** 与输入相关且不等于输入本身的历史关键词；大小写不同的重复项只保留最近的一条。 */
    fun history(query: String, history: List<String>): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return history.asSequence()
            .filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) }
            .map { it to matchRank(q, it) }
            .filter { it.second >= 0 }
            .sortedBy { it.second }
            .map { it.first }
            .distinctBy { it.lowercase() }
            .take(MAX_HISTORY)
            .toList()
    }

    /** 与输入相关的标签；展示名与原值任一命中即算命中，取更好的那个档位。 */
    fun tags(query: String, tags: List<SuggestionTag>): List<SuggestionTag> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return tags.asSequence()
            .map { it to tagRank(q, it) }
            .filter { it.second >= 0 }
            .sortedBy { it.second }
            .map { it.first }
            .distinctBy { it.value }
            .take(MAX_TAGS)
            .toList()
    }

    private fun tagRank(query: String, tag: SuggestionTag): Int {
        val byLabel = matchRank(query, tag.label)
        val byValue = matchRank(query, tag.value)
        return when {
            byLabel < 0 && byValue < 0 -> -1
            byLabel < 0 -> byValue
            byValue < 0 -> byLabel
            else -> minOf(byLabel, byValue)
        }
    }
}
