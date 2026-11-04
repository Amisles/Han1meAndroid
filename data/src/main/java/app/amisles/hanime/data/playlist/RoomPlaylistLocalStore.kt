package app.amisles.hanime.data.playlist

import app.amisles.hanime.data.local.database.CloudPlaylistDao
import app.amisles.hanime.data.local.database.PlaylistMembershipDao
import app.amisles.hanime.data.local.database.PlaylistOutboxDao
import app.amisles.hanime.data.local.entity.CloudPlaylistEntity
import app.amisles.hanime.data.local.entity.PlaylistMembershipEntity
import app.amisles.hanime.data.local.entity.PlaylistOutboxEntity
import app.amisles.hanime.domain.model.CloudPlaylist
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPlaylistLocalStore @Inject constructor(
    private val playlistDao: CloudPlaylistDao,
    private val membershipDao: PlaylistMembershipDao,
    private val outboxDao: PlaylistOutboxDao
) : PlaylistLocalStore {

    override suspend fun cachedPlaylists(userId: String): List<CloudPlaylist> =
        playlistDao.load(userId).map { CloudPlaylist(remoteId = it.remoteId, title = it.title) }

    override suspend fun upsertPlaylists(userId: String, playlists: List<CloudPlaylist>, updatedAt: Long) {
        if (playlists.isEmpty()) return
        playlistDao.upsertAll(
            playlists.map {
                CloudPlaylistEntity(
                    remoteId = it.remoteId,
                    title = it.title,
                    userId = userId,
                    updatedAt = updatedAt
                )
            }
        )
    }

    override suspend fun memberships(userId: String, videoId: String): List<PlaylistMembership> =
        membershipDao.load(userId, videoId).map {
            PlaylistMembership(
                videoId = it.videoId,
                target = it.target,
                saved = it.saved,
                dirty = it.dirty
            )
        }

    override suspend fun putMembership(
        userId: String,
        videoId: String,
        target: String,
        saved: Boolean,
        dirty: Boolean,
        updatedAt: Long
    ) {
        membershipDao.upsert(
            PlaylistMembershipEntity(
                userId = userId,
                videoId = videoId,
                target = target,
                saved = saved,
                updatedAt = updatedAt,
                dirty = dirty
            )
        )
    }

    override suspend fun markSynced(
        userId: String,
        videoId: String,
        target: String,
        saved: Boolean,
        updatedAt: Long
    ) {
        membershipDao.markSynced(userId, videoId, target, saved, updatedAt)
    }

    override suspend fun enqueue(
        userId: String,
        videoId: String,
        target: String,
        desired: Boolean,
        createdAt: Long
    ) {
        outboxDao.enqueue(
            PlaylistOutboxEntity(
                userId = userId,
                videoId = videoId,
                target = target,
                desired = desired,
                createdAt = createdAt
            )
        )
    }

    override suspend fun dueEntries(userId: String, now: Long, limit: Int): List<PlaylistOutboxEntry> =
        outboxDao.due(userId, now, limit).map {
            PlaylistOutboxEntry(
                id = it.id,
                videoId = it.videoId,
                target = it.target,
                desired = it.desired,
                attempts = it.attempts
            )
        }

    override suspend fun complete(entryId: Long) {
        outboxDao.removeById(entryId)
    }

    override suspend fun postpone(entryId: Long, attempts: Int, nextAttemptAt: Long) {
        outboxDao.postpone(entryId, attempts, nextAttemptAt)
    }

    override suspend fun pendingCount(userId: String): Int = outboxDao.pendingCount(userId)

    override suspend fun clear(userId: String) {
        playlistDao.clear(userId)
        membershipDao.clear(userId)
        outboxDao.clear(userId)
    }
}