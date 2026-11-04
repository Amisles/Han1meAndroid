package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.amisles.hanime.data.local.entity.CloudPlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudPlaylistDao {

    @Query("SELECT * FROM cloud_playlist WHERE userId = :userId ORDER BY title COLLATE NOCASE")
    fun observe(userId: String): Flow<List<CloudPlaylistEntity>>

    @Query("SELECT * FROM cloud_playlist WHERE userId = :userId")
    suspend fun load(userId: String): List<CloudPlaylistEntity>

    @Upsert
    suspend fun upsertAll(items: List<CloudPlaylistEntity>)

    @Query("DELETE FROM cloud_playlist WHERE userId = :userId")
    suspend fun clear(userId: String)
}