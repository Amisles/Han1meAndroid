package app.amisles.hanime.data.playlist

import app.amisles.hanime.domain.model.CloudPlaylist
import app.amisles.hanime.domain.model.VideoSaveOptions
import javax.inject.Inject
import javax.inject.Singleton

fun interface PlaylistClock {
    fun now(): Long
}

@Singleton
class PlaylistSyncEngine @Inject constructor(
    private val store: PlaylistLocalStore,
    private val remote: PlaylistRemote,
    private val clock: PlaylistClock
) {

    private var csrfToken: String = ""

    val hasToken: Boolean get() = csrfToken.isNotBlank()

    fun rememberToken(token: String) {
        if (token.isNotBlank()) csrfToken = token
    }

    suspend fun cacheOptions(userId: String, options: VideoSaveOptions) {
        rememberToken(options.csrfToken)
        if (userId.isBlank() || options.videoId.isBlank() || options.options.isEmpty()) return
        store.upsertPlaylists(
            userId = userId,
            playlists = options.favoriteOptions.map { CloudPlaylist(it.target, it.title) },
            updatedAt = clock.now()
        )
        val known = store.memberships(userId, options.videoId).associateBy { it.target }
        options.options.forEach { option ->
            if (known[option.target]?.dirty == true) return@forEach
            store.putMembership(
                userId = userId,
                videoId = options.videoId,
                target = option.target,
                saved = option.selected,
                dirty = false,
                updatedAt = clock.now()
            )
        }
    }

    suspend fun syncFromWatchPage(userId: String, options: VideoSaveOptions) {
        cacheOptions(userId, options)
        if (userId.isNotBlank()) flush(userId)
    }

    suspend fun setSaved(userId: String, videoId: String, target: String, saved: Boolean) {
        if (userId.isBlank() || videoId.isBlank() || target.isBlank()) return
        val now = clock.now()
        store.putMembership(userId, videoId, target, saved, dirty = true, updatedAt = now)
        store.enqueue(userId, videoId, target, saved, createdAt = now)
    }

    suspend fun createAndSave(
        userId: String,
        videoId: String,
        title: String,
        description: String
    ): CloudPlaylist {
        val token = requiredToken()
        val created = remote.createPlaylist(videoId, title, description, token)
        rememberToken(created.csrfToken)
        val playlist = CloudPlaylist(remoteId = created.remoteId, title = created.title)
        store.upsertPlaylists(userId, listOf(playlist), clock.now())
        store.putMembership(
            userId = userId,
            videoId = videoId,
            target = created.remoteId,
            saved = true,
            dirty = false,
            updatedAt = clock.now()
        )
        return playlist
    }

    suspend fun flush(userId: String, limit: Int = DEFAULT_BATCH): Int {
        if (userId.isBlank()) return 0
        var sent = 0
        while (true) {
            val token = csrfToken.takeIf { it.isNotBlank() } ?: return sent
            val due = store.dueEntries(userId, clock.now(), limit)
            if (due.isEmpty()) return sent
            var progressed = false
            for (entry in due) {
                runCatching { remote.setSaved(entry.videoId, entry.target, entry.desired, token) }
                    .onSuccess { result ->
                        rememberToken(result.csrfToken)
                        store.markSynced(userId, entry.videoId, entry.target, result.saved, clock.now())
                        store.complete(entry.id)
                        sent++
                        progressed = true
                    }
                    .onFailure {
                        val attempts = entry.attempts + 1
                        store.postpone(entry.id, attempts, clock.now() + retryDelayMillis(attempts))
                    }
            }
            if (!progressed) return sent
        }
    }

    suspend fun pendingCount(userId: String): Int =
        if (userId.isBlank()) 0 else store.pendingCount(userId)

    private fun requiredToken(): String =
        csrfToken.takeIf { it.isNotBlank() } ?: throw IllegalStateException("缺少 CSRF Token")

    companion object {
        private const val DEFAULT_BATCH = 16
        private const val MAX_RETRY_DELAY_MILLIS = 5 * 60_000L

        fun retryDelayMillis(attempts: Int): Long {
            val shift = (attempts - 1).coerceIn(0, 10)
            return (1_000L shl shift).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        }
    }
}