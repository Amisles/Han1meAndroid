package app.amisles.hanime.data.playlist

import app.amisles.hanime.data.parser.PlaylistSaveResponseParser
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.CreatedPlaylist
import app.amisles.hanime.domain.model.PlaylistSaveResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkPlaylistRemote @Inject constructor(
    private val networkService: NetworkService,
    private val responseParser: PlaylistSaveResponseParser
) : PlaylistRemote {

    override suspend fun setSaved(
        videoId: String,
        target: String,
        saved: Boolean,
        csrfToken: String
    ): PlaylistSaveResult = responseParser.parseSave(
        networkService.saveToPlaylist(
            videoId = videoId,
            target = target,
            saved = saved,
            csrfToken = csrfToken
        )
    )

    override suspend fun createPlaylist(
        videoId: String,
        title: String,
        description: String,
        csrfToken: String
    ): CreatedPlaylist = responseParser.parseCreatedPlaylist(
        networkService.createPlaylist(
            videoId = videoId,
            title = title,
            description = description,
            csrfToken = csrfToken
        )
    )
}