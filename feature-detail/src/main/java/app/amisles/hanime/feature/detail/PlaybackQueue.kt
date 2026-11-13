package app.amisles.hanime.feature.detail

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistInfo

internal enum class NextEpisodeSource { PLAYLIST, RELATED }

internal data class NextEpisode(
    val video: HanimeVideo,
    val source: NextEpisodeSource
)

internal fun nextEpisodeOf(
    playlist: PlaylistInfo?,
    relatedVideos: List<HanimeVideo>,
    currentVideoId: String,
    currentVideoUrl: String,
    allowRelated: Boolean
): NextEpisode? {
    val videos = playlist?.videos.orEmpty()
    if (videos.isNotEmpty()) {
        val index = videos.indexOfFirst { video ->
            (currentVideoUrl.isNotEmpty() && video.videoUrl == currentVideoUrl) ||
                (currentVideoId.isNotEmpty() && video.id == currentVideoId)
        }
        if (index >= 0) {
            videos.getOrNull(index + 1)
                ?.takeIf { it.videoUrl.isNotBlank() }
                ?.let { return NextEpisode(it, NextEpisodeSource.PLAYLIST) }
        }
    }
    if (!allowRelated) return null
    val related = relatedVideos.firstOrNull { it.videoUrl.isNotBlank() } ?: return null
    return NextEpisode(related, NextEpisodeSource.RELATED)
}
