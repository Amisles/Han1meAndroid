package app.amisles.hanime.data.local.database

import app.amisles.hanime.domain.model.FavoriteVideo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoriteVideo>

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE id = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    @Insert
    suspend fun addFavorite(favoriteVideo: FavoriteVideo)

    @Delete
    suspend fun removeFavorite(favoriteVideo: FavoriteVideo)

    @Query("DELETE FROM favorites WHERE id = :videoId")
    suspend fun removeFavoriteById(videoId: String)

    @Query("SELECT * FROM favorites WHERE id = :videoId")
    suspend fun getFavoriteById(videoId: String): FavoriteVideo?

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoriteCount(): Int
}