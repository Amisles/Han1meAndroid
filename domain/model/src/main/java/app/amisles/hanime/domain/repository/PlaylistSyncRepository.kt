package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.CloudPlaylist
import app.amisles.hanime.domain.model.VideoSaveOptions

interface PlaylistSyncRepository {

    suspend fun cacheFromWatchPage(options: VideoSaveOptions)

    suspend fun savedTargets(videoId: String): Set<String>

    suspend fun cachedPlaylists(): List<CloudPlaylist>

    suspend fun setSaved(videoId: String, target: String, saved: Boolean)

    suspend fun setWatchLater(videoId: String, saved: Boolean)

    suspend fun createPlaylistAndSave(videoId: String, title: String, description: String): CloudPlaylist

    suspend fun flush(): Int

    suspend fun pendingCount(): Int

    suspend fun shouldOfferMigration(): Boolean

    suspend fun markMigrationHandled()

    suspend fun migrateFavorites(videoIds: List<String>, target: String): Int
}