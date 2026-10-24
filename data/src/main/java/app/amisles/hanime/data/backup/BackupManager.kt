package app.amisles.hanime.data.backup

import android.content.Context
import androidx.room.withTransaction
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.local.database.FavoriteDao
import app.amisles.hanime.data.local.database.FavoriteDatabase
import app.amisles.hanime.data.local.database.FavoriteGroupDao
import app.amisles.hanime.data.local.database.SearchHistoryDao
import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.local.entity.DownloadEntity
import app.amisles.hanime.data.local.entity.SearchHistoryEntity
import app.amisles.hanime.data.local.entity.toDomain
import app.amisles.hanime.data.local.entity.toEntity
import app.amisles.hanime.domain.model.BackupConflictPolicy
import app.amisles.hanime.domain.model.BackupContents
import app.amisles.hanime.domain.model.BackupError
import app.amisles.hanime.domain.model.BackupImportSummary
import app.amisles.hanime.domain.model.BackupManifest
import app.amisles.hanime.domain.model.BackupSettings
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.model.FavoriteGroup
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.domain.repository.BackupRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FavoriteDatabase,
    private val favoriteDao: FavoriteDao,
    private val favoriteGroupDao: FavoriteGroupDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val downloadDao: DownloadDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val settings: SettingsRepository
) : BackupRepository {

    override suspend fun exportBackup(extraFiles: Map<String, String>): ByteArray =
        withContext(Dispatchers.IO) {
            val favorites = favoriteDao.getAllFavorites().map { it.toDomain() }
            val groups = favoriteGroupDao.getAllGroupsOnce().map { it.toDomain() }
            val history = watchHistoryDao.getAllOnce().map { it.toDomain() }
            val downloads = downloadDao.getAllDownloadsOnce().map { it.toTask() }
            val searches = searchHistoryDao.getAllOnce()

            val manifest = BackupManifest(
                schemaVersion = SCHEMA_VERSION,
                packageName = context.packageName,
                appVersionName = appVersionName(),
                exportedAt = System.currentTimeMillis(),
                favoriteCount = favorites.size,
                groupCount = groups.size,
                historyCount = history.size,
                downloadCount = downloads.size,
                searchHistoryCount = searches.size,
                hasSettings = true,
                hasUiConfig = extraFiles.containsKey(ENTRY_UI_CONFIG)
            )

            val files = linkedMapOf(
                ENTRY_MANIFEST to manifestToJson(manifest).toString(),
                ENTRY_FAVORITES to favoritesToJson(favorites).toString(),
                ENTRY_GROUPS to groupsToJson(groups).toString(),
                ENTRY_HISTORY to historyToJson(history).toString(),
                ENTRY_DOWNLOADS to downloadsToJson(downloads).toString(),
                ENTRY_SEARCH to searchToJson(searches.map { it.query to it.searchedAt }).toString(),
                ENTRY_SETTINGS to settingsToJson(readSettings()).toString()
            )
            files.putAll(extraFiles)
            zip(files)
        }

    override suspend fun inspectBackup(bytes: ByteArray): BackupManifest =
        withContext(Dispatchers.IO) { parseManifest(unzip(bytes)) }

    override suspend fun importBackup(
        bytes: ByteArray,
        policy: BackupConflictPolicy
    ): Pair<BackupContents, BackupImportSummary> = withContext(Dispatchers.IO) {
        val files = unzip(bytes)
        val manifest = parseManifest(files)
        val contents = parseContents(files, manifest)
        val autoBackupName = writeAutoBackup(policy)

        db.withTransaction {
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
                searchHistoryDao.addSearch(SearchHistoryEntity(query = entry.first, searchedAt = entry.second))
            }
        }

        val settingsApplied = contents.settings?.let { applySettings(it); true } ?: false

        val summary = BackupImportSummary(
            favorites = contents.favorites.size,
            groups = contents.groups.size,
            history = contents.history.size,
            downloads = contents.downloads.size,
            searchHistory = contents.searchHistory.size,
            settingsApplied = settingsApplied,
            uiConfigApplied = contents.uiConfigText != null,
            autoBackupName = autoBackupName
        )
        AppLogger.log("BackupManager", "导入完成: " + summary)
        contents to summary
    }

    private suspend fun writeAutoBackup(policy: BackupConflictPolicy): String? = runCatching {
        val dir = File(context.getExternalFilesDir(null), AUTO_BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        val stamp = System.currentTimeMillis()
        val name = "auto-" + stamp + ".zip"
        File(dir, name).writeBytes(exportBackup(emptyMap()))
        name
    }.getOrElse { e ->
        AppLogger.logError("BackupManager", "自动备份失败: " + e.message, e)
        null
    }

    private fun parseContents(files: Map<String, String>, manifest: BackupManifest) = BackupContents(
        manifest = manifest,
        favorites = parseFavorites(files[ENTRY_FAVORITES]),
        groups = parseGroups(files[ENTRY_GROUPS]),
        history = parseHistory(files[ENTRY_HISTORY]),
        downloads = parseDownloads(files[ENTRY_DOWNLOADS]),
        searchHistory = parseSearch(files[ENTRY_SEARCH]),
        settings = files[ENTRY_SETTINGS]?.let { runCatching { parseSettings(JSONObject(it)) }.getOrNull() },
        uiConfigText = files[ENTRY_UI_CONFIG]
    )

    private fun parseManifest(files: Map<String, String>): BackupManifest {
        val raw = files[ENTRY_MANIFEST] ?: throw BackupError.MissingManifest
        val obj = runCatching { JSONObject(raw) }.getOrElse { throw BackupError.MissingManifest }
        val schema = obj.optInt("schemaVersion", -1)
        val pkg = obj.optString("packageName", "")
        if (pkg != context.packageName) throw BackupError.WrongPackage(pkg)
        if (schema < 1 || schema > SCHEMA_VERSION) {
            throw BackupError.UnsupportedSchema(schema, SCHEMA_VERSION)
        }
        return BackupManifest(
            schemaVersion = schema,
            packageName = pkg,
            appVersionName = obj.optString("appVersionName", ""),
            exportedAt = obj.optLong("exportedAt", 0L),
            favoriteCount = obj.optInt("favoriteCount", 0),
            groupCount = obj.optInt("groupCount", 0),
            historyCount = obj.optInt("historyCount", 0),
            downloadCount = obj.optInt("downloadCount", 0),
            searchHistoryCount = obj.optInt("searchHistoryCount", 0),
            hasSettings = obj.optBoolean("hasSettings", false),
            hasUiConfig = obj.optBoolean("hasUiConfig", false)
        )
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private fun readSettings() = BackupSettings(
        appLanguage = settings.appLanguage,
        themeMode = settings.themeMode.name,
        baseUrl = settings.baseUrl,
        playbackSpeed = settings.playbackSpeed,
        preferredQuality = settings.preferredQuality,
        autoPlayNext = settings.autoPlayNext,
        loopPlayback = settings.loopPlayback,
        longPressBoostSpeed = settings.longPressBoostSpeed,
        doubleTapSeek = settings.doubleTapSeek,
        autoQuality = settings.autoQuality,
        maxDownloadConcurrent = settings.maxDownloadConcurrent,
        downloadRateLimit = settings.downloadRateLimit,
        subscriptionReminder = settings.subscriptionReminder,
        downloadStoragePath = settings.downloadStoragePathFlow.value,
        searchLayoutMode = settings.searchLayoutMode?.name
    )

    private fun applySettings(s: BackupSettings) {
        s.appLanguage?.let { settings.setAppLanguage(it) }
        s.themeMode?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }?.let { settings.setThemeMode(it) }
        }
        s.baseUrl?.let { settings.setBaseUrl(it) }
        s.playbackSpeed?.let { settings.setPlaybackSpeed(it) }
        s.preferredQuality?.let { settings.setPreferredQuality(it) }
        s.autoPlayNext?.let { settings.setAutoPlayNext(it) }
        s.loopPlayback?.let { settings.setLoopPlayback(it) }
        s.longPressBoostSpeed?.let { settings.setLongPressBoostSpeed(it) }
        s.doubleTapSeek?.let { settings.setDoubleTapSeek(it) }
        s.autoQuality?.let { settings.setAutoQuality(it) }
        s.maxDownloadConcurrent?.let { settings.setMaxDownloadConcurrent(it) }
        s.downloadRateLimit?.let { settings.setDownloadRateLimit(it) }
        s.subscriptionReminder?.let { settings.setSubscriptionReminder(it) }
        s.downloadStoragePath?.let { settings.downloadStoragePath = it }
        s.searchLayoutMode?.let { name ->
            SearchLayoutMode.entries.firstOrNull { it.name == name }
                ?.let { settings.setSearchLayoutMode(it) }
        }
    }

    private fun zip(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            files.forEach { (name, text) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var total = 0L
        val chunk = ByteArray(8 * 1024)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val buf = ByteArrayOutputStream()
                while (true) {
                    val n = zis.read(chunk)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_TOTAL_BYTES || buf.size() + n > MAX_ENTRY_BYTES) {
                        throw BackupError.NotASupportedArchive
                    }
                    buf.write(chunk, 0, n)
                }
                result[entry.name] = buf.toString("UTF-8")
                zis.closeEntry()
            }
        }
        return result
    }

    private fun manifestToJson(m: BackupManifest) = JSONObject().apply {
        put("schemaVersion", m.schemaVersion)
        put("packageName", m.packageName)
        put("appVersionName", m.appVersionName)
        put("exportedAt", m.exportedAt)
        put("favoriteCount", m.favoriteCount)
        put("groupCount", m.groupCount)
        put("historyCount", m.historyCount)
        put("downloadCount", m.downloadCount)
        put("searchHistoryCount", m.searchHistoryCount)
        put("hasSettings", m.hasSettings)
        put("hasUiConfig", m.hasUiConfig)
    }

    private fun favoritesToJson(list: List<FavoriteVideo>) = JSONArray().apply {
        list.forEach { v ->
            put(JSONObject().apply {
                put("id", v.id)
                put("title", v.title)
                put("thumbnailUrl", v.thumbnailUrl)
                put("videoUrl", v.videoUrl)
                put("author", v.author)
                put("duration", v.duration)
                put("likeRate", v.likeRate)
                put("viewCount", v.viewCount)
                put("createdAt", v.createdAt)
                put("groupId", v.groupId)
                put("tags", JSONArray(v.tags))
            })
        }
    }

    private fun groupsToJson(list: List<FavoriteGroup>) = JSONArray().apply {
        list.forEach { g ->
            put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("createdAt", g.createdAt)
            })
        }
    }

    private fun historyToJson(list: List<WatchHistory>) = JSONArray().apply {
        list.forEach { h ->
            put(JSONObject().apply {
                put("id", h.id)
                put("title", h.title)
                put("thumbnailUrl", h.thumbnailUrl)
                put("videoUrl", h.videoUrl)
                put("author", h.author)
                put("duration", h.duration)
                put("watchedAt", h.watchedAt)
                put("playbackPosition", h.playbackPosition)
                put("playbackDuration", h.playbackDuration)
            })
        }
    }

    private fun downloadsToJson(list: List<DownloadTask>) = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().apply {
                put("id", d.id)
                put("title", d.title)
                put("quality", d.quality)
                put("url", d.url)
                put("totalBytes", d.totalBytes)
                put("downloadedBytes", d.downloadedBytes)
                put("status", d.status.name)
                put("filePath", d.filePath)
                put("thumbnailUrl", d.thumbnailUrl)
                put("videoId", d.videoId)
                put("errorMessage", d.errorMessage)
            })
        }
    }

    private fun searchToJson(list: List<Pair<String, Long>>) = JSONArray().apply {
        list.forEach { entry ->
            put(JSONObject().apply {
                put("query", entry.first)
                put("searchedAt", entry.second)
            })
        }
    }

    private fun settingsToJson(s: BackupSettings) = JSONObject().apply {
        s.appLanguage?.let { put("appLanguage", it) }
        s.themeMode?.let { put("themeMode", it) }
        s.baseUrl?.let { put("baseUrl", it) }
        s.playbackSpeed?.let { put("playbackSpeed", it.toDouble()) }
        s.preferredQuality?.let { put("preferredQuality", it) }
        s.autoPlayNext?.let { put("autoPlayNext", it) }
        s.loopPlayback?.let { put("loopPlayback", it) }
        s.longPressBoostSpeed?.let { put("longPressBoostSpeed", it.toDouble()) }
        s.doubleTapSeek?.let { put("doubleTapSeek", it) }
        s.autoQuality?.let { put("autoQuality", it) }
        s.maxDownloadConcurrent?.let { put("maxDownloadConcurrent", it) }
        s.downloadRateLimit?.let { put("downloadRateLimit", it) }
        s.subscriptionReminder?.let { put("subscriptionReminder", it) }
        s.downloadStoragePath?.let { put("downloadStoragePath", it) }
        s.searchLayoutMode?.let { put("searchLayoutMode", it) }
    }

    private fun parseSettings(o: JSONObject) = BackupSettings(
        appLanguage = o.stringOrNull("appLanguage"),
        themeMode = o.stringOrNull("themeMode"),
        baseUrl = o.stringOrNull("baseUrl"),
        playbackSpeed = o.doubleOrNull("playbackSpeed")?.toFloat(),
        preferredQuality = o.stringOrNull("preferredQuality"),
        autoPlayNext = o.booleanOrNull("autoPlayNext"),
        loopPlayback = o.booleanOrNull("loopPlayback"),
        longPressBoostSpeed = o.doubleOrNull("longPressBoostSpeed")?.toFloat(),
        doubleTapSeek = o.booleanOrNull("doubleTapSeek"),
        autoQuality = o.booleanOrNull("autoQuality"),
        maxDownloadConcurrent = o.intOrNull("maxDownloadConcurrent"),
        downloadRateLimit = o.intOrNull("downloadRateLimit"),
        subscriptionReminder = o.booleanOrNull("subscriptionReminder"),
        downloadStoragePath = o.stringOrNull("downloadStoragePath"),
        searchLayoutMode = o.stringOrNull("searchLayoutMode")
    )

    private fun parseFavorites(raw: String?): List<FavoriteVideo> = parseArray(raw) { o ->
        FavoriteVideo(
            id = o.getString("id"),
            title = o.optString("title", ""),
            thumbnailUrl = o.optString("thumbnailUrl", ""),
            videoUrl = o.optString("videoUrl", ""),
            author = o.optString("author", ""),
            duration = o.optString("duration", ""),
            likeRate = o.optString("likeRate", ""),
            viewCount = o.optString("viewCount", ""),
            createdAt = o.optLong("createdAt", 0L),
            groupId = o.optString("groupId", ""),
            tags = o.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotEmpty() }
            } ?: emptyList()
        )
    }

    private fun parseGroups(raw: String?): List<FavoriteGroup> = parseArray(raw) { o ->
        FavoriteGroup(
            id = o.getString("id"),
            name = o.optString("name", ""),
            createdAt = o.optLong("createdAt", 0L)
        )
    }

    private fun parseHistory(raw: String?): List<WatchHistory> = parseArray(raw) { o ->
        WatchHistory(
            id = o.getString("id"),
            title = o.optString("title", ""),
            thumbnailUrl = o.optString("thumbnailUrl", ""),
            videoUrl = o.optString("videoUrl", ""),
            author = o.optString("author", ""),
            duration = o.optString("duration", ""),
            watchedAt = o.optLong("watchedAt", 0L),
            playbackPosition = o.optLong("playbackPosition", 0L),
            playbackDuration = o.optLong("playbackDuration", 0L)
        )
    }

    private fun parseDownloads(raw: String?): List<DownloadTask> = parseArray(raw) { o ->
        DownloadTask(
            id = o.getInt("id"),
            title = o.optString("title", ""),
            quality = o.optString("quality", ""),
            url = o.optString("url", ""),
            totalBytes = o.optLong("totalBytes", 0L),
            downloadedBytes = o.optLong("downloadedBytes", 0L),
            status = runCatching { DownloadStatus.valueOf(o.optString("status", "PENDING")) }
                .getOrDefault(DownloadStatus.PENDING),
            filePath = o.optString("filePath", ""),
            thumbnailUrl = o.optString("thumbnailUrl", ""),
            videoId = o.optString("videoId", ""),
            errorMessage = o.optString("errorMessage", "")
        )
    }

    private fun parseSearch(raw: String?): List<Pair<String, Long>> =
        parseArray(raw) { o -> o.optString("query", "") to o.optLong("searchedAt", 0L) }
            .filter { it.first.isNotEmpty() }

    private fun <T> parseArray(raw: String?, mapper: (JSONObject) -> T): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { mapper(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.booleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun DownloadEntity.toTask() = DownloadTask(
        id = id,
        title = title,
        quality = quality,
        url = url,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.PENDING),
        filePath = filePath,
        thumbnailUrl = thumbnailUrl,
        videoId = videoId,
        errorMessage = errorMessage
    )

    private fun DownloadTask.toEntity() = DownloadEntity(
        id = id,
        title = title,
        quality = quality,
        url = url,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status.name,
        filePath = filePath,
        thumbnailUrl = thumbnailUrl,
        videoId = videoId,
        errorMessage = errorMessage
    )

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 48L * 1024 * 1024
        private const val AUTO_BACKUP_DIR = "backups"

        const val ENTRY_MANIFEST = "manifest.json"
        const val ENTRY_FAVORITES = "favorites.json"
        const val ENTRY_GROUPS = "favorite_groups.json"
        const val ENTRY_HISTORY = "watch_history.json"
        const val ENTRY_DOWNLOADS = "downloads.json"
        const val ENTRY_SEARCH = "search_history.json"
        const val ENTRY_SETTINGS = "settings.json"
        const val ENTRY_UI_CONFIG = "ui_config.txt"
    }
}
