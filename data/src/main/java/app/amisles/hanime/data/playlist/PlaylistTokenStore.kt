package app.amisles.hanime.data.playlist

import app.amisles.hanime.domain.repository.PlaylistSettings
import app.amisles.hanime.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistTokenStore {
    fun read(): String
    fun write(token: String)
}

@Singleton
class PreferencesPlaylistTokenStore @Inject constructor(
    private val settings: PlaylistSettings,
    private val session: SessionRepository
) : PlaylistTokenStore {

    override fun read(): String = settings.playlistCsrfToken

    override fun write(token: String) {
        if (!session.isActiveLogin) return
        settings.playlistCsrfToken = token
    }
}
