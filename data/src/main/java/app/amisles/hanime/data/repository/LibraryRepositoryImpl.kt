package app.amisles.hanime.data.repository

import android.database.SQLException
import app.amisles.hanime.data.local.database.FavoriteDao
import app.amisles.hanime.data.local.database.FavoriteGroupDao
import app.amisles.hanime.data.local.entity.toDomain
import app.amisles.hanime.data.local.entity.toEntity
import app.amisles.hanime.data.local.database.SearchHistoryDao
import app.amisles.hanime.data.local.entity.SearchHistoryEntity
import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.local.database.WatchLaterDao
import app.amisles.hanime.data.local.entity.toDomain as toWatchLaterDomain
import app.amisles.hanime.data.local.entity.toEntity as toWatchLaterEntity
import app.amisles.hanime.domain.model.WatchLaterVideo
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.FavoriteGroup
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.amisles.hanime.domain.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val favoriteGroupDao: FavoriteGroupDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val watchLaterDao: WatchLaterDao
) : LibraryRepository {

    override fun getFavoriteIdsFlow(): Flow<List<String>> = favoriteDao.getFavoriteIds()

    override fun getWatchedIdsFlow(): Flow<List<String>> = watchHistoryDao.getWatchedIds()

    override suspend fun addFavorite(video: FavoriteVideo): Boolean {
        return try {
            favoriteDao.addFavorite(video.toEntity())
            AppLogger.log("LibraryRepositoryImpl", "Favorite added successfully")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error adding favorite: ${e.message}", e)
            false
        }
    }


    override suspend fun removeFavorite(videoId: String): Boolean {
        return try {
            favoriteDao.removeFavoriteById(videoId)
            AppLogger.log("LibraryRepositoryImpl", "Favorite removed successfully")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error removing favorite: ${e.message}", e)
            false
        }
    }


    /**
     * 批量删除收藏。ids 为空时直接返回：Room 的 `IN (:ids)` 在空集合下会生成非法 SQL。
     */
    override suspend fun removeFavorites(videoIds: List<String>): Boolean {
        // 空集合是合法的无操作，按成功处理（Room 的 IN (:ids) 在空集合下会生成非法 SQL）
        if (videoIds.isEmpty()) return true
        return try {
            favoriteDao.removeFavoritesByIds(videoIds)
            AppLogger.log("LibraryRepositoryImpl", "Favorites removed: ${videoIds.size}")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error removing favorites: ${e.message}", e)
            false
        }
    }


    override suspend fun isFavorite(videoId: String): Boolean {
        return try {
            favoriteDao.isFavorite(videoId)
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error checking favorite: ${e.message}", e)
            false
        }
    }


    override suspend fun getAllFavorites(): List<FavoriteVideo> {
        return try {
            val favorites = favoriteDao.getAllFavorites().map { it.toDomain() }
            AppLogger.log("LibraryRepositoryImpl", "Got ${favorites.size} favorites")
            favorites
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error getting favorites: ${e.message}", e)
            emptyList()
        }
    }


    override suspend fun getFavoriteCount(): Int {
        return try {
            favoriteDao.getFavoriteCount()
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error getting favorite count: ${e.message}", e)
            0
        }
    }


    override fun getAllFavoriteGroupsFlow(): Flow<List<FavoriteGroup>> =
        favoriteGroupDao.getAllGroups().map { list -> list.map { it.toDomain() } }


    override suspend fun upsertFavoriteGroup(group: FavoriteGroup): Boolean {
        return try {
            favoriteGroupDao.upsertGroup(group.toEntity())
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error saving favorite group: ${e.message}", e)
            false
        }
    }


    /** 删除分组并把其下收藏移回「未分组」，避免条目失去归属后不可见。 */
    override suspend fun deleteFavoriteGroup(groupId: String): Boolean {
        return try {
            favoriteGroupDao.detachFavoritesOfGroup(groupId)
            favoriteGroupDao.deleteGroup(groupId)
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error deleting favorite group: ${e.message}", e)
            false
        }
    }


    override suspend fun moveFavoriteToGroup(videoId: String, groupId: String): Boolean {
        return try {
            favoriteDao.setFavoriteGroup(videoId, groupId)
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error moving favorite: ${e.message}", e)
            false
        }
    }


    override suspend fun moveFavoritesToGroup(videoIds: List<String>, groupId: String): Boolean {
        if (videoIds.isEmpty()) return true
        return try {
            favoriteDao.setFavoriteGroupForIds(videoIds, groupId) == videoIds.size
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error moving favorites: ${e.message}", e)
            false
        }
    }


    override suspend fun setFavoriteTags(videoId: String, tags: List<String>): Boolean {
        return try {
            val normalized = tags
                .map { it.trim().replace(',', ' ') }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_TAGS_PER_FAVORITE)
            favoriteDao.setFavoriteTags(videoId, normalized.joinToString(","))
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error saving favorite tags: ${e.message}", e)
            false
        }
    }


    override suspend fun addWatchHistory(history: WatchHistory): Boolean {
        return try {
            watchHistoryDao.addWatchHistory(history.toEntity())
            AppLogger.log("LibraryRepositoryImpl", "Watch history added successfully")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error adding watch history: ${e.message}", e)
            false
        }
    }


    /**
     * 只更新已有历史记录的播放进度；返回是否命中了记录（未命中时由调用方插入完整记录）。
     * 播放过程中高频调用，避免读-改-写的整行 REPLACE。
     */
    override suspend fun updateWatchProgress(videoId: String, position: Long, duration: Long): Boolean {
        return try {
            watchHistoryDao.updatePlaybackProgress(videoId, position, duration) > 0
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error updating watch progress: " + e.message, e)
            false
        }
    }


    override suspend fun markWatched(videoId: String): Boolean =
        try {
            watchHistoryDao.markWatched(videoId, System.currentTimeMillis()) > 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.logError("LibraryRepositoryImpl", "markWatched: ${e.message}", e)
            false
        }


    override suspend fun getWatchHistory(videoId: String): WatchHistory? {
        return try {
            watchHistoryDao.getWatchHistoryById(videoId)?.toDomain()
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error getting watch history: ${e.message}", e)
            null
        }
    }


    override fun getContinueWatchingFlow(): Flow<List<WatchHistory>> {
        return watchHistoryDao.getContinueWatching().map { list -> list.map { it.toDomain() } }
    }


    override fun getAllWatchHistoryFlow(): Flow<List<WatchHistory>> {
        return watchHistoryDao.getAllWatchHistory().map { list -> list.map { it.toDomain() } }
    }


    override suspend fun removeWatchHistory(videoId: String): Boolean {
        return try {
            watchHistoryDao.removeWatchHistory(videoId)
            AppLogger.log("LibraryRepositoryImpl", "Watch history removed successfully")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error removing watch history: ${e.message}", e)
            false
        }
    }


    /**
     * 批量删除观看历史。ids 为空时直接返回：Room 的 `IN (:ids)` 在空集合下会生成非法 SQL。
     */
    override suspend fun removeWatchHistories(videoIds: List<String>): Boolean {
        // 空集合是合法的无操作，按成功处理
        if (videoIds.isEmpty()) return true
        return try {
            watchHistoryDao.removeWatchHistoriesByIds(videoIds)
            AppLogger.log("LibraryRepositoryImpl", "Watch histories removed: ${videoIds.size}")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error removing watch histories: ${e.message}", e)
            false
        }
    }


    override suspend fun clearWatchHistory(): Boolean {
        return try {
            watchHistoryDao.clearWatchHistory()
            AppLogger.log("LibraryRepositoryImpl", "Watch history cleared successfully")
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error clearing watch history: ${e.message}", e)
            false
        }
    }


    override suspend fun getWatchHistoryCount(): Int {
        return try {
            watchHistoryDao.getWatchHistoryCount()
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error getting watch history count: ${e.message}", e)
            0
        }
    }

    /** 搜索历史关键词（按时间倒序，最多 20 条）。只暴露字符串，避免持久化实体泄漏到上层。 */
    override fun getSearchHistory(): Flow<List<String>> {
        return searchHistoryDao.getAllHistory().map { list -> list.map { it.query } }
    }


    override suspend fun addSearchHistory(query: String): Boolean {
        if (query.isBlank()) return true
        return try {
            searchHistoryDao.addSearch(
                SearchHistoryEntity(
                    query = query.trim(),
                    searchedAt = System.currentTimeMillis()
                )
            )
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error adding search history: ${e.message}", e)
            false
        }
    }


    override suspend fun removeSearchHistory(query: String): Boolean {
        return try {
            searchHistoryDao.removeSearch(query)
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error removing search history: ${e.message}", e)
            false
        }
    }


    override suspend fun clearSearchHistory(): Boolean {
        return try {
            searchHistoryDao.clearAllSearch()
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error clearing search history: ${e.message}", e)
            false
        }
    }

    // ── 稍后观看 ────────────────────────────────────────────────────────────

    override fun getWatchLaterFlow(): Flow<List<WatchLaterVideo>> =
        watchLaterDao.getAllFlow().map { list -> list.map { it.toWatchLaterDomain() } }

    override suspend fun getWatchLaterIds(): List<String> =
        try {
            watchLaterDao.getAllOnce().map { it.id }
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error getting watch later ids: ${e.message}", e)
            emptyList()
        }

    override suspend fun addWatchLater(video: WatchLaterVideo): Boolean {
        return try {
            watchLaterDao.add(video.toWatchLaterEntity())
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error adding watch later: ${e.message}", e)
            false
        }
    }

    override suspend fun removeWatchLater(videoId: String): Boolean {
        return try {
            watchLaterDao.remove(videoId)
            true
        } catch (e: SQLException) {
            AppLogger.logError("LibraryRepositoryImpl", "Error removing watch later: ${e.message}", e)
            false
        }
    }

    // ── 作者页 / 列表页 / 订阅内容 ──────────────────────────────────────────
    // 原先这些能力由上层直接注入 NetworkService 获得；改为在仓储层转发，
    // 让 feature 模块只依赖 domain 接口。


    private companion object {
        /** 单个收藏最多保留的标签数：超出后输入体验与展示都会失控。 */
        const val MAX_TAGS_PER_FAVORITE = 10
    }
}
