package app.amisles.hanime.data.playlist

import app.amisles.hanime.domain.model.CloudPlaylist
import app.amisles.hanime.domain.model.CreatedPlaylist
import app.amisles.hanime.domain.model.PlaylistSaveOption
import app.amisles.hanime.domain.model.PlaylistSaveResult
import app.amisles.hanime.domain.model.PlaylistTargets
import app.amisles.hanime.domain.model.VideoSaveOptions
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSyncEngineTest {

    private val store = FakeStore()
    private val remote = FakeRemote()
    private val tokens = FakeTokenStore()
    private var now = 1_000L
    private val engine = PlaylistSyncEngine(store, remote, PlaylistClock { now }, tokens)

    private val options = VideoSaveOptions(
        videoId = "409004",
        csrfToken = "page_token",
        options = listOf(
            PlaylistSaveOption(PlaylistTargets.WATCH_LATER, "稍后观看", true, true),
            PlaylistSaveOption("1055606", "test", false, true),
            PlaylistSaveOption("1055655", "test2", false, false)
        )
    )

    @Test
    fun `cacheOptions stores playlists and memberships and remembers token`() = kotlinx.coroutines.runBlocking {
        engine.cacheOptions("u1", options)

        assertEquals(listOf("test", "test2"), store.cachedPlaylists("u1").map { it.title })
        val memberships = store.memberships("u1", "409004").associateBy { it.target }
        assertEquals(3, memberships.size)
        assertTrue(memberships.getValue(PlaylistTargets.WATCH_LATER).saved)
        assertTrue(memberships.getValue("1055606").saved)
        assertFalse(memberships.getValue("1055655").saved)
        assertFalse(memberships.getValue("1055606").dirty)
        assertTrue(engine.hasToken)
    }

    @Test
    fun `cacheOptions keeps locally pending membership`() = kotlinx.coroutines.runBlocking {
        engine.cacheOptions("u1", options)
        engine.setSaved("u1", "409004", "1055606", false)

        engine.cacheOptions("u1", options)

        val membership = store.memberships("u1", "409004").first { it.target == "1055606" }
        assertFalse(membership.saved)
        assertTrue(membership.dirty)
    }

    @Test
    fun `setSaved coalesces repeated intents for one target`() = kotlinx.coroutines.runBlocking {
        engine.setSaved("u1", "409004", "1055606", true)
        engine.setSaved("u1", "409004", "1055606", false)

        assertEquals(1, engine.pendingCount("u1"))
        assertFalse(store.outbox.first().entry.desired)
    }

    @Test
    fun `flush pushes pending entries and marks them synced`() = kotlinx.coroutines.runBlocking {
        engine.cacheOptions("u1", options)
        engine.setSaved("u1", "409004", "1055606", false)
        engine.setSaved("u1", "409004", PlaylistTargets.WATCH_LATER, false)

        val sent = engine.flush("u1")

        assertEquals(2, sent)
        assertEquals(0, engine.pendingCount("u1"))
        assertEquals(listOf("1055606", PlaylistTargets.WATCH_LATER), remote.calls.map { it.second })
        val memberships = store.memberships("u1", "409004").associateBy { it.target }
        assertFalse(memberships.getValue("1055606").saved)
        assertFalse(memberships.getValue("1055606").dirty)
    }

    @Test
    fun `syncFromWatchPage flushes pending intents after refreshing the snapshot`() = kotlinx.coroutines.runBlocking {
        engine.cacheOptions("u1", options)
        engine.setSaved("u1", "409004", "1055606", false)

        engine.syncFromWatchPage("u1", options)

        assertEquals(0, engine.pendingCount("u1"))
        assertEquals(listOf("1055606"), remote.calls.map { it.second })
        assertFalse(remote.calls.single().third)
        assertFalse(store.memberships("u1", "409004").first { it.target == "1055606" }.saved)
    }

    @Test
    fun `flush does nothing without a token`() = kotlinx.coroutines.runBlocking {
        engine.setSaved("u1", "409004", "1055606", true)

        assertEquals(0, engine.flush("u1"))
        assertEquals(1, engine.pendingCount("u1"))
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun `flush keeps entries and backs off when the site rejects`() = kotlinx.coroutines.runBlocking {
        engine.cacheOptions("u1", options)
        engine.setSaved("u1", "409004", "1055606", true)
        remote.fail = true

        val sent = engine.flush("u1")

        assertEquals(0, sent)
        assertEquals(1, engine.pendingCount("u1"))
        assertEquals(1, store.outbox.first().entry.attempts)
        assertEquals(1, remote.calls.size)
    }

    @Test
    fun `createAndSave registers the new playlist and marks the video saved`() = kotlinx.coroutines.runBlocking {
        engine.cacheOptions("u1", options)

        val created = engine.createAndSave("u1", "409004", "test2", "1223231313")

        assertEquals(CloudPlaylist("1055655", "test2"), created)
        assertTrue(store.cachedPlaylists("u1").any { it.remoteId == "1055655" })
        val membership = store.memberships("u1", "409004").first { it.target == "1055655" }
        assertTrue(membership.saved)
        assertFalse(membership.dirty)

        engine.setSaved("u1", "409004", "1055606", true)
        engine.flush("u1")
        assertEquals("server_token", remote.lastToken)
    }

    @Test(expected = IllegalStateException::class)
    fun `createAndSave requires a csrf token`() {
        kotlinx.coroutines.runBlocking { engine.createAndSave("u1", "409004", "test2", "") }
    }

    @Test
    fun `csrf token is persisted and restored by a fresh engine`() {
        engine.rememberToken("persisted_token")

        val restored = PlaylistSyncEngine(store, remote, PlaylistClock { now }, tokens)

        assertTrue(restored.hasToken)
        assertEquals("persisted_token", tokens.token)
    }

    @Test
    fun `retry delay grows and is capped`() {
        assertEquals(1_000L, PlaylistSyncEngine.retryDelayMillis(1))
        assertEquals(2_000L, PlaylistSyncEngine.retryDelayMillis(2))
        assertEquals(5 * 60_000L, PlaylistSyncEngine.retryDelayMillis(20))
    }

    private class FakeStore : PlaylistLocalStore {

        data class Row(val entry: PlaylistOutboxEntry, val userId: String)

        private val playlists = mutableMapOf<Pair<String, String>, CloudPlaylist>()
        private val memberships = mutableMapOf<Triple<String, String, String>, PlaylistMembership>()
        val outbox = mutableListOf<Row>()
        private var nextId = 1L

        override suspend fun cachedPlaylists(userId: String): List<CloudPlaylist> =
            playlists.filterKeys { it.first == userId }.values.toList()

        override suspend fun upsertPlaylists(userId: String, playlists: List<CloudPlaylist>, updatedAt: Long) {
            playlists.forEach { this.playlists[userId to it.remoteId] = it }
        }

        override suspend fun memberships(userId: String, videoId: String): List<PlaylistMembership> =
            memberships.filterKeys { it.first == userId && it.second == videoId }.values.toList()

        override suspend fun putMembership(
            userId: String,
            videoId: String,
            target: String,
            saved: Boolean,
            dirty: Boolean,
            updatedAt: Long
        ) {
            memberships[Triple(userId, videoId, target)] = PlaylistMembership(videoId, target, saved, dirty)
        }

        override suspend fun markSynced(
            userId: String,
            videoId: String,
            target: String,
            saved: Boolean,
            updatedAt: Long
        ) {
            val key = Triple(userId, videoId, target)
            memberships[key]?.let { memberships[key] = it.copy(saved = saved, dirty = false) }
        }

        override suspend fun enqueue(
            userId: String,
            videoId: String,
            target: String,
            desired: Boolean,
            createdAt: Long
        ) {
            val index = outbox.indexOfFirst {
                it.userId == userId && it.entry.videoId == videoId && it.entry.target == target
            }
            if (index >= 0) {
                outbox[index] = outbox[index].copy(entry = outbox[index].entry.copy(desired = desired, attempts = 0))
                return
            }
            outbox += Row(PlaylistOutboxEntry(nextId++, videoId, target, desired, 0), userId)
        }

        override suspend fun dueEntries(userId: String, now: Long, limit: Int): List<PlaylistOutboxEntry> =
            outbox.filter { it.userId == userId }.map { it.entry }.take(limit)

        override suspend fun complete(entryId: Long) {
            outbox.removeAll { it.entry.id == entryId }
        }

        override suspend fun postpone(entryId: Long, attempts: Int, nextAttemptAt: Long) {
            val index = outbox.indexOfFirst { it.entry.id == entryId }
            if (index >= 0) outbox[index] = outbox[index].copy(entry = outbox[index].entry.copy(attempts = attempts))
        }

        override suspend fun pendingCount(userId: String): Int = outbox.count { it.userId == userId }

        override suspend fun clear(userId: String) {
            playlists.keys.removeAll { it.first == userId }
            memberships.keys.removeAll { it.first == userId }
            outbox.removeAll { it.userId == userId }
        }
    }

    private class FakeTokenStore : PlaylistTokenStore {
        var token: String = ""

        override fun read(): String = token

        override fun write(token: String) {
            this.token = token
        }
    }

    private class FakeRemote : PlaylistRemote {

        val calls = mutableListOf<Triple<String, String, Boolean>>()
        var fail = false
        var token = "server_token"
        var lastToken: String = ""

        override suspend fun setSaved(
            videoId: String,
            target: String,
            saved: Boolean,
            csrfToken: String
        ): PlaylistSaveResult {
            lastToken = csrfToken
            calls += Triple(videoId, target, saved)
            if (fail) throw IOException("save failed")
            return PlaylistSaveResult(saved = saved, csrfToken = token)
        }

        override suspend fun createPlaylist(
            videoId: String,
            title: String,
            description: String,
            csrfToken: String
        ): CreatedPlaylist {
            lastToken = csrfToken
            return CreatedPlaylist(remoteId = "1055655", title = title, csrfToken = token)
        }
    }
}
