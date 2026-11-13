package app.amisles.hanime.core.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

@Immutable
data class VideoLocalState(
    val downloaded: Set<String> = emptySet(),
    val favorite: Set<String> = emptySet(),
    val watched: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap()
) {
    fun isDownloaded(videoId: String): Boolean = downloaded.contains(videoId)

    fun isFavorite(videoId: String): Boolean = favorite.contains(videoId)

    fun isWatched(videoId: String): Boolean = watched.contains(videoId)

    fun progressOf(videoId: String): Float? = progress[videoId]
}

val LocalVideoLocalState = compositionLocalOf { VideoLocalState() }
