package app.amisles.hanime.domain.model

object PlaylistTargets {
    const val WATCH_LATER = "save"

    fun isWatchLater(target: String): Boolean = target == WATCH_LATER
}

data class PlaylistSaveOption(
    val target: String,
    val title: String,
    val isPrivate: Boolean,
    val selected: Boolean
)

data class VideoSaveOptions(
    val videoId: String = "",
    val csrfToken: String = "",
    val options: List<PlaylistSaveOption> = emptyList()
) {
    val savedTargets: Set<String>
        get() = options.filter { it.selected }.map { it.target }.toSet()

    val favoriteOptions: List<PlaylistSaveOption>
        get() = options.filterNot { PlaylistTargets.isWatchLater(it.target) }

    val watchLaterSaved: Boolean
        get() = options.firstOrNull { PlaylistTargets.isWatchLater(it.target) }?.selected == true
}

data class CloudPlaylist(
    val remoteId: String,
    val title: String
)

data class PlaylistSaveResult(
    val saved: Boolean,
    val csrfToken: String
)

data class CreatedPlaylist(
    val remoteId: String,
    val title: String,
    val csrfToken: String
)