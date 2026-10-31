package app.amisles.hanime.feature.detail

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistInfo

internal fun nextEpisodeOf(
    playlist: PlaylistInfo?,
    relatedVideos: List<HanimeVideo>,
    currentVideoId: String,
    currentVideoUrl: String
): HanimeVideo? {
    val videos = playlist?.videos.orEmpty()
    if (videos.isNotEmpty()) {
        val index = videos.indexOfFirst { video ->
            (currentVideoUrl.isNotEmpty() && video.videoUrl == currentVideoUrl) ||
                (currentVideoId.isNotEmpty() && video.id == currentVideoId)
        }
        if (index >= 0) {
            return videos.getOrNull(index + 1)?.takeIf { it.videoUrl.isNotBlank() }
        }
    }
    return relatedVideos.firstOrNull { it.videoUrl.isNotBlank() }
}
