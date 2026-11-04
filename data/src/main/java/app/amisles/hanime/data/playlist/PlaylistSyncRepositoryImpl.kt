package app.amisles.hanime.data.playlist

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.CloudPlaylist
import app.amisles.hanime.domain.model.PlaylistTargets
import app.amisles.hanime.domain.model.VideoSaveOptions
import app.amisles.hanime.domain.repository.PlaylistSyncRepository
import app.amisles.hanime.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistSyncRepositoryImpl @Inject constructor(
    private val engine: PlaylistSyncEngine,
    private val store: PlaylistLocalStore,
    private val session: SessionRepository
) : PlaylistSyncRepository {

    override suspend fun cacheFromWatchPage(options: VideoSaveOptions) {
        engine.syncFromWatchPage(activeUserId(), options)
    }

    override suspend fun savedTargets(videoId: String): Set<String> {
        val userId = activeUserId()
        if (userId.isBlank() || videoId.isBlank()) return emptySet()
        return store.memberships(userId, videoId).filter { it.saved }.map { it.target }.toSet()
    }

    override suspend fun cachedPlaylists(): List<CloudPlaylist> {
        val userId = activeUserId()
        if (userId.isBlank()) return emptyList()
        return store.cachedPlaylists(userId)
    }

    override suspend fun setSaved(videoId: String, target: String, saved: Boolean) {
        val userId = activeUserId()
        if (userId.isBlank()) return
        engine.setSaved(userId, videoId, target, saved)
    }

    override suspend fun setWatchLater(videoId: String, saved: Boolean) {
        setSaved(videoId, PlaylistTargets.WATCH_LATER, saved)
    }

    override suspend fun createPlaylistAndSave(
        videoId: String,
        title: String,
        description: String
    ): CloudPlaylist {
        val userId = activeUserId()
        check(userId.isNotBlank()) { "未登录" }
        return engine.createAndSave(userId, videoId, title, description)
    }

    override suspend fun flush(): Int = engine.flush(activeUserId())

    override suspend fun pendingCount(): Int = engine.pendingCount(activeUserId())

    override suspend fun shouldOfferMigration(): Boolean =
        session.isLoggedIn && !Preferences.playlistMigrationDone

    override suspend fun markMigrationHandled() {
        Preferences.playlistMigrationDone = true
    }

    override suspend fun migrateFavorites(videoIds: List<String>, target: String): Int {
        val userId = activeUserId()
        if (userId.isBlank() || target.isBlank() || videoIds.isEmpty()) return 0
        videoIds.forEach { engine.setSaved(userId, it, target, true) }
        Preferences.playlistMigrationDone = true
        engine.flush(userId)
        return videoIds.size
    }

    private var lastUserId: String = ""

    private suspend fun activeUserId(): String {
        val current = if (session.isLoggedIn) session.savedUserId else ""
        if (current == lastUserId) return current
        val previous = lastUserId
        lastUserId = current
        if (previous.isNotBlank()) runCatching { store.clear(previous) }
        if (current.isNotBlank()) runCatching { engine.flush(current) }
        return current
    }
}