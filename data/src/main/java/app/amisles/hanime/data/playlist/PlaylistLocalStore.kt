package app.amisles.hanime.data.playlist

import app.amisles.hanime.domain.model.CloudPlaylist

data class PlaylistMembership(
    val videoId: String,
    val target: String,
    val saved: Boolean,
    val dirty: Boolean
)

data class PlaylistOutboxEntry(
    val id: Long,
    val videoId: String,
    val target: String,
    val desired: Boolean,
    val attempts: Int
)

interface PlaylistLocalStore {

    suspend fun cachedPlaylists(userId: String): List<CloudPlaylist>

    suspend fun upsertPlaylists(userId: String, playlists: List<CloudPlaylist>, updatedAt: Long)

    suspend fun memberships(userId: String, videoId: String): List<PlaylistMembership>

    suspend fun putMembership(
        userId: String,
        videoId: String,
        target: String,
        saved: Boolean,
        dirty: Boolean,
        updatedAt: Long
    )

    suspend fun markSynced(userId: String, videoId: String, target: String, saved: Boolean, updatedAt: Long)

    suspend fun enqueue(userId: String, videoId: String, target: String, desired: Boolean, createdAt: Long)

    suspend fun dueEntries(userId: String, now: Long, limit: Int): List<PlaylistOutboxEntry>

    suspend fun complete(entryId: Long)

    suspend fun postpone(entryId: Long, attempts: Int, nextAttemptAt: Long)

    suspend fun pendingCount(userId: String): Int

    suspend fun clear(userId: String)
}