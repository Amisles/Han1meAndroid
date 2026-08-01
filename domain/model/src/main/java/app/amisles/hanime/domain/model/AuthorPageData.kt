package app.amisles.hanime.domain.model

data class PlaylistSummary(
    val title: String,
    val thumbnailUrl: String,
    val videoCount: String,
    val author: String,
    val publishTime: String,
    val playlistUrl: String
)

data class PlaylistDetail(
    val title: String,
    val coverUrl: String,
    val author: String,
    val authorAvatarUrl: String,
    val videoCount: Int,
    val viewCount: String,
    val description: String,
    val videos: List<HanimeVideo> = emptyList()
)

data class AuthorPageData(
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val subscriberCount: String,
    val videoCount: String,
    val videos: List<HanimeVideo> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val uploadedPageUrl: String = "",
    val playlistsPageUrl: String = ""
)