package app.amisles.hanime.data.playlist

import app.amisles.hanime.domain.model.CreatedPlaylist
import app.amisles.hanime.domain.model.PlaylistSaveResult

interface PlaylistRemote {

    suspend fun setSaved(videoId: String, target: String, saved: Boolean, csrfToken: String): PlaylistSaveResult

    suspend fun createPlaylist(
        videoId: String,
        title: String,
        description: String,
        csrfToken: String
    ): CreatedPlaylist
}