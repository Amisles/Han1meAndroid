package app.amisles.hanime.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class VideoLocalState(
    val downloaded: Set<String> = emptySet(),
    val favorite: Set<String> = emptySet(),
    val watched: Set<String> = emptySet()
) {
    fun isDownloaded(videoId: String): Boolean = downloaded.contains(videoId)

    fun isFavorite(videoId: String): Boolean = favorite.contains(videoId)

    fun isWatched(videoId: String): Boolean = watched.contains(videoId)
}

val LocalVideoLocalState = staticCompositionLocalOf { VideoLocalState() }
