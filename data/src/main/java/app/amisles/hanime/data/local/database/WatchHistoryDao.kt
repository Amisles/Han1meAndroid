package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.domain.model.WatchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE id = :videoId")
    suspend fun getWatchHistoryById(videoId: String): WatchHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWatchHistory(history: WatchHistory)

    @Query("DELETE FROM watch_history WHERE id = :videoId")
    suspend fun removeWatchHistory(videoId: String)

    // 批量删除走单条 SQL：调用方已保证 ids 非空（空集合会生成非法 SQL，见仓储层守卫）
    @Query("DELETE FROM watch_history WHERE id IN (:videoIds)")
    suspend fun removeWatchHistoriesByIds(videoIds: List<String>)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun getWatchHistoryCount(): Int
}