package app.amisles.hanime.data.backup

import androidx.room.withTransaction
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.local.database.FavoriteDao
import app.amisles.hanime.data.local.database.FavoriteDatabase
import app.amisles.hanime.data.local.database.FavoriteGroupDao
import app.amisles.hanime.data.local.database.SearchHistoryDao
import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.local.entity.SearchHistoryEntity
import app.amisles.hanime.data.local.entity.toDomain
import app.amisles.hanime.data.local.entity.toEntity
import app.amisles.hanime.data.local.entity.toTask
import app.amisles.hanime.domain.model.BackupConflictPolicy
import app.amisles.hanime.domain.model.BackupContents
import app.amisles.hanime.domain.model.BackupError
import app.amisles.hanime.domain.model.BackupSettings
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.model.FavoriteGroup
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.WatchHistory
import javax.inject.Inject
import javax.inject.Singleton

internal data class BackupDatabaseSnapshot(
    val favorites: List<FavoriteVideo>,
    val groups: List<FavoriteGroup>,
    val history: List<WatchHistory>,
    val downloads: List<DownloadTask>,
    val searches: List<Pair<String, Long>>
)

internal data class DownloadIdRange(val min: Int, val max: Int)

@Singleton
class BackupDatabaseStore @Inject constructor(
    private val db: FavoriteDatabase,
    private val favoriteDao: FavoriteDao,
    private val favoriteGroupDao: FavoriteGroupDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val downloadDao: DownloadDao,
    private val searchHistoryDao: SearchHistoryDao
) {
    internal suspend fun read(): BackupDatabaseSnapshot = BackupDatabaseSnapshot(
        favorites = favoriteDao.getAllFavorites().map { it.toDomain() },
        groups = favoriteGroupDao.getAllGroupsOnce().map { it.toDomain() },
        history = watchHistoryDao.getAllOnce().map { it.toDomain() },
        downloads = downloadDao.getAllDownloadsOnce().map { it.toTask() },
        searches = searchHistoryDao.getAllOnce().map { it.query to it.searchedAt }
    )

    internal suspend fun restore(
        contents: BackupContents,
        policy: BackupConflictPolicy,
        applySettings: (BackupSettings) -> Unit,
        applyUiConfig: (String) -> Boolean
    ): DownloadIdRange? = db.withTransaction {
        if (policy == BackupConflictPolicy.OVERWRITE) {
            favoriteDao.clearAll()
            favoriteGroupDao.clearAll()
            watchHistoryDao.clearWatchHistory()
            downloadDao.clearAll()
            searchHistoryDao.clearAllSearch()
        }

        val existingGroups = favoriteGroupDao.getAllGroupsOnce().mapTo(mutableSetOf()) { it.id }
        contents.groups.filter { it.id !in existingGroups }.forEach {
            favoriteGroupDao.upsertGroup(it.toEntity())
        }
        contents.favorites.forEach { favoriteDao.addFavorite(it.toEntity()) }
        contents.history.forEach { watchHistoryDao.addWatchHistory(it.toEntity()) }
        if (contents.downloads.isNotEmpty()) {
            downloadDao.upsertAll(contents.downloads.map { it.toEntity() })
        }
        contents.searchHistory.forEach { entry ->
            searchHistoryDao.addSearch(
                SearchHistoryEntity(query = entry.first, searchedAt = entry.second)
            )
        }
        // 设置与界面配置在事务内应用：任一失败都抛异常，让数据库回滚，避免「数据换了、配置没换」。
        contents.settings?.let(applySettings)
        contents.uiConfigText?.let { text ->
            if (!applyUiConfig(text)) throw BackupError.UiConfigRejected
        }

        if (contents.downloads.isEmpty()) {
            null
        } else {
            val max = downloadDao.getMaxId()
            val min = downloadDao.getMinId()
            if (max != null && min != null) DownloadIdRange(min, max) else null
        }
    }
}
