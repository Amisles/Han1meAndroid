package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.FavoriteGroup
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.WatchHistory
import kotlinx.coroutines.flow.Flow

/**
 * 本地库仓储：收藏、观看历史、搜索历史。
 *
 * 与 [ContentRepository] 分开：这些操作只读写本机 Room，与站点是否可达无关，
 * 也不应因为站点改版而改变签名。界面在离线状态下同样需要它们。
 */
interface LibraryRepository {
    // ── 收藏 ────────────────────────────────────────────────────────────────
    suspend fun addFavorite(video: FavoriteVideo)
    suspend fun removeFavorite(videoId: String)
    suspend fun removeFavorites(videoIds: List<String>)
    suspend fun isFavorite(videoId: String): Boolean
    suspend fun getAllFavorites(): List<FavoriteVideo>
    suspend fun getFavoriteCount(): Int

    // ── 收藏分组 ────────────────────────────────────────────────────────────
    fun getAllFavoriteGroupsFlow(): Flow<List<FavoriteGroup>>
    suspend fun upsertFavoriteGroup(group: FavoriteGroup)
    suspend fun deleteFavoriteGroup(groupId: String)
    suspend fun moveFavoriteToGroup(videoId: String, groupId: String)
    suspend fun setFavoriteTags(videoId: String, tags: List<String>)

    // ── 观看历史 ────────────────────────────────────────────────────────────
    suspend fun addWatchHistory(history: WatchHistory)

    /**
     * 只更新已有记录的播放进度，返回是否命中。
     *
     * 播放中每 5 秒调用一次，故刻意与 [addWatchHistory] 分开：整行 REPLACE 会把标题、
     * 封面等字段重复写一遍，写放大且会覆盖并发的元数据更新。
     */
    suspend fun updateWatchProgress(videoId: String, position: Long, duration: Long): Boolean

    suspend fun getWatchHistory(videoId: String): WatchHistory?
    /**
     * 「继续观看」候选：有播放进度、且尚未看完的条目，按最近观看倒序。
     *
     * 过滤放在 SQL 层而非 UI：历史表会随使用无限增长，全量取出再过滤会让首页首帧
     * 随历史条数线性变慢。
     */
    fun getContinueWatchingFlow(): Flow<List<WatchHistory>>

    fun getAllWatchHistoryFlow(): Flow<List<WatchHistory>>
    suspend fun removeWatchHistory(videoId: String)
    suspend fun removeWatchHistories(videoIds: List<String>)
    suspend fun clearWatchHistory()
    suspend fun getWatchHistoryCount(): Int

    // ── 搜索历史 ────────────────────────────────────────────────────────────
    fun getSearchHistory(): Flow<List<String>>
    suspend fun addSearchHistory(query: String)
    suspend fun removeSearchHistory(query: String)
    suspend fun clearSearchHistory()
}
