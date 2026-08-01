package app.amisles.hanime.domain.model

data class SearchResult(
    val videos: List<HanimeVideo>,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false
)