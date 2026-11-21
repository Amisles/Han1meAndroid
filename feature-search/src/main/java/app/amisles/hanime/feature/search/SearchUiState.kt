package app.amisles.hanime.feature.search

import app.amisles.hanime.domain.model.HanimeVideo

data class SearchUiState(
    val query: String = "",
    val genre: String? = null,
    val sort: String? = null,
    val tags: List<String> = emptyList(),
    val broad: Boolean = false,
    val date: String = "",
    val duration: String = "",
    val videos: List<HanimeVideo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val error: String? = null,
    val challengeHost: String? = null,
    val loadMoreError: Boolean = false,
    val searchGeneration: Int = 0,
    val searchHistory: List<String>? = null
)
