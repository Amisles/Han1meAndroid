package app.amisles.hanime.data.playlist

import app.amisles.hanime.data.preferences.Preferences
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistTokenStore {
    fun read(): String
    fun write(token: String)
}

@Singleton
class PreferencesPlaylistTokenStore @Inject constructor() : PlaylistTokenStore {

    override fun read(): String = Preferences.playlistCsrfToken

    override fun write(token: String) {
        Preferences.playlistCsrfToken = token
    }
}
