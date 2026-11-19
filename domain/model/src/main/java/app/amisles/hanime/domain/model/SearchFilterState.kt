package app.amisles.hanime.domain.model

/** 搜索页的筛选条件快照，用于跨会话恢复。 */
data class SearchFilterState(
    val genre: String? = null,
    val sort: String? = null,
    val tags: List<String> = emptyList(),
    val broad: Boolean = false,
    val date: String = "",
    val duration: String = ""
)
