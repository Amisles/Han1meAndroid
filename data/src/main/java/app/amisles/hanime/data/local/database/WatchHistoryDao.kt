package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE id = :videoId")
    suspend fun getWatchHistoryById(videoId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWatchHistory(history: WatchHistoryEntity)

    /**
     * 仅更新播放进度列。播放中每 5 秒落库一次，整行 REPLACE 会把标题、封面等元数据重复写一遍，
     * 单条 UPDATE 的写入量与快照大小都更低，也不会与其他字段的并发写入互相覆盖。
     */
    @Query(
        "UPDATE watch_history SET playback_position = :position, playback_duration = :duration WHERE id = :videoId"
    )
    suspend fun updatePlaybackProgress(videoId: String, position: Long, duration: Long): Int

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