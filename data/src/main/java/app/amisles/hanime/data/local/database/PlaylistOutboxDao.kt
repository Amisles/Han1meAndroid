package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.PlaylistOutboxEntity

@Dao
interface PlaylistOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(row: PlaylistOutboxEntity): Long

    @Query(
        "SELECT * FROM playlist_outbox WHERE userId = :userId AND nextAttemptAt <= :now " +
            "ORDER BY createdAt LIMIT :limit"
    )
    suspend fun due(userId: String, now: Long, limit: Int): List<PlaylistOutboxEntity>

    @Query("SELECT COUNT(*) FROM playlist_outbox WHERE userId = :userId")
    suspend fun pendingCount(userId: String): Int

    @Query("DELETE FROM playlist_outbox WHERE id = :id")
    suspend fun removeById(id: Long)

    @Query("UPDATE playlist_outbox SET attempts = :attempts, nextAttemptAt = :nextAttemptAt WHERE id = :id")
    suspend fun postpone(id: Long, attempts: Int, nextAttemptAt: Long)

    @Query("DELETE FROM playlist_outbox WHERE userId = :userId")
    suspend fun clear(userId: String)
}