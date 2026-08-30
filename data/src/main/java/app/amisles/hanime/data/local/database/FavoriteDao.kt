package app.amisles.hanime.data.local.database

import app.amisles.hanime.domain.model.FavoriteVideo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoriteVideo>

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE id = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    // B4：原 @Insert 未指定冲突策略，Room 默认 ABORT——重复收藏会抛 SQLiteConstraintException，
    // 而 HanimeRepository.addFavorite 只捕获并记录，导致界面乐观置位「已收藏」但数据库并未写入。
    // 统一为 REPLACE，与 WatchHistoryDao / SearchHistoryDao / DownloadDao 保持一致。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
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