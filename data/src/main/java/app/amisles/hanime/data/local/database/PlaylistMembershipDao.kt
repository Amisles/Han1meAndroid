package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.amisles.hanime.data.local.entity.PlaylistMembershipEntity

@Dao
interface PlaylistMembershipDao {

    @Query("SELECT * FROM playlist_membership WHERE userId = :userId AND videoId = :videoId")
    suspend fun load(userId: String, videoId: String): List<PlaylistMembershipEntity>

    @Upsert
    suspend fun upsert(row: PlaylistMembershipEntity)

    @Query(
        "UPDATE playlist_membership SET saved = :saved, dirty = 0, updatedAt = :updatedAt " +
            "WHERE userId = :userId AND videoId = :videoId AND target = :target"
    )
    suspend fun markSynced(userId: String, videoId: String, target: String, saved: Boolean, updatedAt: Long)

    @Query("DELETE FROM playlist_membership WHERE userId = :userId")
    suspend fun clear(userId: String)
}