package app.amisles.hanime.domain.model

sealed interface AuthorPageDataEvent {
    data class Profile(val data: AuthorPageData) : AuthorPageDataEvent
    data class Videos(val videos: List<HanimeVideo>) : AuthorPageDataEvent
    data class Playlists(val playlists: List<PlaylistSummary>) : AuthorPageDataEvent
    data class Error(val message: String) : AuthorPageDataEvent
}
