package app.amisles.hanime.data.backup

import android.content.Context
import app.amisles.hanime.domain.model.BackupContents
import app.amisles.hanime.domain.model.BackupError
import app.amisles.hanime.domain.model.BackupManifest
import app.amisles.hanime.domain.model.BackupSettings
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.model.FavoriteGroup
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.WatchHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class BackupJsonCodec @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun parseManifest(files: Map<String, String>): BackupManifest {
        val raw = files[BackupFormat.ENTRY_MANIFEST] ?: throw BackupError.MissingManifest
        val obj = runCatching { JSONObject(raw) }.getOrElse { throw BackupError.MissingManifest }
        val schema = obj.optInt("schemaVersion", -1)
        val pkg = obj.optString("packageName", "")
        if (pkg != context.packageName) throw BackupError.WrongPackage(pkg)
        if (schema < 1 || schema > BackupFormat.SCHEMA_VERSION) {
            throw BackupError.UnsupportedSchema(schema, BackupFormat.SCHEMA_VERSION)
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

    fun parseContents(files: Map<String, String>, manifest: BackupManifest): BackupContents =
        BackupContents(
            manifest = manifest,
            favorites = parseFavorites(files[BackupFormat.ENTRY_FAVORITES]),
            groups = parseGroups(files[BackupFormat.ENTRY_GROUPS]),
            history = parseHistory(files[BackupFormat.ENTRY_HISTORY]),
            downloads = parseDownloads(files[BackupFormat.ENTRY_DOWNLOADS]),
            searchHistory = parseSearch(files[BackupFormat.ENTRY_SEARCH]),
            settings = files[BackupFormat.ENTRY_SETTINGS]?.let { raw ->
                runCatching { parseSettings(JSONObject(raw)) }.getOrElse { e ->
                    throw BackupError.InvalidEntry(BackupFormat.ENTRY_SETTINGS, e.message.orEmpty())
                }
            },
            uiConfigText = files[BackupFormat.ENTRY_UI_CONFIG]
        )

    fun manifestToJson(m: BackupManifest): String = JSONObject().apply {
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
    }.toString()

    fun favoritesToJson(list: List<FavoriteVideo>): String = JSONArray().apply {
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
    }.toString()

    fun groupsToJson(list: List<FavoriteGroup>): String = JSONArray().apply {
        list.forEach { g ->
            put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("createdAt", g.createdAt)
            })
        }
    }.toString()

    fun historyToJson(list: List<WatchHistory>): String = JSONArray().apply {
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
    }.toString()

    fun downloadsToJson(list: List<DownloadTask>): String = JSONArray().apply {
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
    }.toString()

    fun searchToJson(list: List<Pair<String, Long>>): String = JSONArray().apply {
        list.forEach { entry ->
            put(JSONObject().apply {
                put("query", entry.first)
                put("searchedAt", entry.second)
            })
        }
    }.toString()

    fun settingsToJson(s: BackupSettings): String = JSONObject().apply {
        s.appLanguage?.let { put("appLanguage", it) }
        s.themeMode?.let { put("themeMode", it) }
        s.dynamicColor?.let { put("dynamicColor", it) }
        s.baseUrl?.let { put("baseUrl", it) }
        s.playbackSpeed?.let { put("playbackSpeed", it.toDouble()) }
        s.preferredQuality?.let { put("preferredQuality", it) }
        s.autoPlayNext?.let { put("autoPlayNext", it) }
        s.loopPlayback?.let { put("loopPlayback", it) }
        s.longPressBoostSpeed?.let { put("longPressBoostSpeed", it.toDouble()) }
        s.doubleTapSeek?.let { put("doubleTapSeek", it) }
        s.autoQuality?.let { put("autoQuality", it) }
        s.autoRotateFullscreen?.let { put("autoRotateFullscreen", it) }
        s.maxDownloadConcurrent?.let { put("maxDownloadConcurrent", it) }
        s.videoDetailCacheLimit?.let { put("videoDetailCacheLimit", it) }
        s.downloadRateLimit?.let { put("downloadRateLimit", it) }
        s.subscriptionReminder?.let { put("subscriptionReminder", it) }
        s.downloadStoragePath?.let { put("downloadStoragePath", it) }
        s.searchLayoutMode?.let { put("searchLayoutMode", it) }
        s.playerLayout?.let { put("playerLayout", it) }
    }.toString()

    fun parseSettings(o: JSONObject): BackupSettings = BackupSettings(
        appLanguage = o.stringOrNull("appLanguage"),
        themeMode = o.stringOrNull("themeMode"),
        dynamicColor = o.booleanOrNull("dynamicColor"),
        baseUrl = o.stringOrNull("baseUrl"),
        playbackSpeed = o.doubleOrNull("playbackSpeed")?.toFloat(),
        preferredQuality = o.stringOrNull("preferredQuality"),
        autoPlayNext = o.booleanOrNull("autoPlayNext"),
        loopPlayback = o.booleanOrNull("loopPlayback"),
        longPressBoostSpeed = o.doubleOrNull("longPressBoostSpeed")?.toFloat(),
        doubleTapSeek = o.booleanOrNull("doubleTapSeek"),
        autoQuality = o.booleanOrNull("autoQuality"),
        autoRotateFullscreen = o.booleanOrNull("autoRotateFullscreen"),
        maxDownloadConcurrent = o.intOrNull("maxDownloadConcurrent"),
        videoDetailCacheLimit = o.intOrNull("videoDetailCacheLimit"),
        downloadRateLimit = o.intOrNull("downloadRateLimit"),
        subscriptionReminder = o.booleanOrNull("subscriptionReminder"),
        downloadStoragePath = o.stringOrNull("downloadStoragePath"),
        searchLayoutMode = o.stringOrNull("searchLayoutMode"),
        playerLayout = o.stringOrNull("playerLayout")
    )

    private fun parseFavorites(raw: String?): List<FavoriteVideo> =
        parseArray(BackupFormat.ENTRY_FAVORITES, raw) { o ->
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

    private fun parseGroups(raw: String?): List<FavoriteGroup> =
        parseArray(BackupFormat.ENTRY_GROUPS, raw) { o ->
            FavoriteGroup(
                id = o.getString("id"),
                name = o.optString("name", ""),
                createdAt = o.optLong("createdAt", 0L)
            )
        }

    private fun parseHistory(raw: String?): List<WatchHistory> =
        parseArray(BackupFormat.ENTRY_HISTORY, raw) { o ->
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

    private fun parseDownloads(raw: String?): List<DownloadTask> =
        parseArray(BackupFormat.ENTRY_DOWNLOADS, raw) { o ->
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
        parseArray(BackupFormat.ENTRY_SEARCH, raw) { o ->
            o.optString("query", "") to o.optLong("searchedAt", 0L)
        }.filter { it.first.isNotEmpty() }

    private fun <T> parseArray(entry: String, raw: String?, mapper: (JSONObject) -> T): List<T> {
        if (raw == null) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrElse { e ->
            throw BackupError.InvalidEntry(entry, "不是合法 JSON 数组: ${e.message}")
        }
        val result = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            val item = runCatching { arr.getJSONObject(i) }.getOrElse { e ->
                throw BackupError.InvalidEntry(entry, "第 $i 条不是对象: ${e.message}")
            }
            result += runCatching { mapper(item) }.getOrElse { e ->
                throw BackupError.InvalidEntry(entry, "第 $i 条解析失败: ${e.message}")
            }
        }
        return result
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.booleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null
}
