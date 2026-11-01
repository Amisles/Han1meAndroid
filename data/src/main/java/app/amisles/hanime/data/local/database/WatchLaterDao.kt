package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<WatchLaterEntity>>

    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    suspend fun getAllOnce(): List<WatchLaterEntity>

    @Query("SELECT EXISTS(SELECT * FROM watch_later WHERE id = :videoId)")
    suspend fun exists(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE id = :videoId")
    suspend fun remove(videoId: String)

    @Query("SELECT COUNT(*) FROM watch_later")
    suspend fun count(): Int
}
