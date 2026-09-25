package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.FavoriteVideoEntity

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    suspend fun getAllFavorites(): List<FavoriteVideoEntity>

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE id = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    // @Insert 统一为 REPLACE 策略：Room 默认 ABORT 会使重复收藏抛 SQLiteConstraintException，
    // 而仓储层仅记录异常，导致界面乐观置位「已收藏」但数据库未写入。
    // 与 WatchHistoryDao / SearchHistoryDao / DownloadDao 保持一致。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favoriteVideo: FavoriteVideoEntity)

    @Query("DELETE FROM favorites WHERE id = :videoId")
    suspend fun removeFavoriteById(videoId: String)

    // 批量删除走单条 SQL：调用方已保证 ids 非空（空集合会生成非法 SQL，见仓储层守卫）
    @Query("DELETE FROM favorites WHERE id IN (:videoIds)")
    suspend fun removeFavoritesByIds(videoIds: List<String>)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoriteCount(): Int
}
