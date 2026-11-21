package app.amisles.hanime.data.backup

import android.content.Context
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.BackupConflictPolicy
import app.amisles.hanime.domain.model.BackupContents
import app.amisles.hanime.domain.model.BackupError
import app.amisles.hanime.domain.model.BackupImportSummary
import app.amisles.hanime.domain.model.BackupManifest
import app.amisles.hanime.domain.repository.BackupRepository
import app.amisles.hanime.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BackupDatabaseStore,
    private val settingsMapper: BackupSettingsMapper,
    private val downloads: DownloadRepository,
    private val codec: BackupJsonCodec
) : BackupRepository {

    override suspend fun exportBackup(extraFiles: Map<String, String>): ByteArray =
        withContext(Dispatchers.IO) {
            val snapshot = store.read()
            val manifest = BackupManifest(
                schemaVersion = BackupFormat.SCHEMA_VERSION,
                packageName = context.packageName,
                appVersionName = appVersionName(),
                exportedAt = System.currentTimeMillis(),
                favoriteCount = snapshot.favorites.size,
                groupCount = snapshot.groups.size,
                historyCount = snapshot.history.size,
                downloadCount = snapshot.downloads.size,
                searchHistoryCount = snapshot.searches.size,
                hasSettings = true,
                hasUiConfig = extraFiles.containsKey(BackupFormat.ENTRY_UI_CONFIG)
            )

            val files = linkedMapOf(
                BackupFormat.ENTRY_MANIFEST to codec.manifestToJson(manifest),
                BackupFormat.ENTRY_FAVORITES to codec.favoritesToJson(snapshot.favorites),
                BackupFormat.ENTRY_GROUPS to codec.groupsToJson(snapshot.groups),
                BackupFormat.ENTRY_HISTORY to codec.historyToJson(snapshot.history),
                BackupFormat.ENTRY_DOWNLOADS to codec.downloadsToJson(snapshot.downloads),
                BackupFormat.ENTRY_SEARCH to codec.searchToJson(snapshot.searches),
                BackupFormat.ENTRY_SETTINGS to codec.settingsToJson(settingsMapper.read())
            )
            files.putAll(extraFiles)
            // 校验值覆盖除自身以外的全部条目，写入后再落盘
            files[BackupChecksum.ENTRY_CHECKSUM] =
                BackupChecksum.format(BackupChecksum.compute(files))
            BackupArchiveCodec.zip(files)
        }

    override suspend fun inspectBackup(bytes: ByteArray): BackupManifest =
        withContext(Dispatchers.IO) {
            codec.parseManifest(BackupArchiveCodec.unzip(bytes))
        }

    override suspend fun importBackup(
        bytes: ByteArray,
        policy: BackupConflictPolicy,
        applyUiConfig: (String) -> Boolean
    ): Pair<BackupContents, BackupImportSummary> = withContext(Dispatchers.IO) {
        val files = BackupArchiveCodec.unzip(bytes)
        val manifest = codec.parseManifest(files)
        // 先验完整性再动数据库：半截文件解出的 JSON 可能恰好能被解析成残缺数据
        val payload = files.filterKeys { it != BackupChecksum.ENTRY_CHECKSUM }
        if (!BackupChecksum.matches(payload, files[BackupChecksum.ENTRY_CHECKSUM])) {
            throw BackupError.ChecksumMismatch
        }
        // 全部条目解析成功后才允许进入写入阶段：任何一条损坏都整包拒绝，不做静默裁剪。
        val contents = codec.parseContents(files, manifest)
        validateContents(files, manifest, contents)
        downloads.suspendForBackupImport()
        val autoBackupName = writeAutoBackup(policy)
        val previousSettings = settingsMapper.read()

        var restoredIdRange: DownloadIdRange? = null
        try {
            restoredIdRange = store.restore(
                contents = contents,
                policy = policy,
                applySettings = { settingsMapper.apply(it) },
                applyUiConfig = applyUiConfig
            )
        } catch (e: Exception) {
            // 设置在数据库之外，事务回滚不会自动恢复：按导入前快照还原。
            runCatching { settingsMapper.apply(previousSettings) }
            throw e
        } finally {
            downloads.reloadTasksFromDatabase()
        }

        // 导入的下载任务保留了源设备的主键 id，而运行中的 DownloadManager 只在进程启动时
        // 同步过一次自增计数器；不同步会让之后新建的任务撞上导入行的 id，
        // upsert(REPLACE) 会静默覆盖掉刚导入的记录。导入完成后立即向两侧扩张计数器。
        restoredIdRange?.let {
            downloads.adoptDownloadIdFloor(it.min)
            downloads.adoptDownloadIdCeiling(it.max)
        }

        val summary = BackupImportSummary(
            favorites = contents.favorites.size,
            groups = contents.groups.size,
            history = contents.history.size,
            downloads = contents.downloads.size,
            searchHistory = contents.searchHistory.size,
            settingsApplied = contents.settings != null,
            uiConfigApplied = contents.uiConfigText != null,
            autoBackupName = autoBackupName
        )
        AppLogger.log("BackupManager", "导入完成: $summary")
        contents to summary
    }

    /** 清单声明的条目数必须与解析结果一致，避免缺失/损坏条目被当成「空」静默带入。 */
    private fun validateContents(
        files: Map<String, String>,
        manifest: BackupManifest,
        contents: BackupContents
    ) {
        fun requireCount(entry: String, declared: Int, actual: Int) {
            // 旧备份可能没有计数字段（0 表示未知）；只有明确声明了数量才做一致性校验。
            if (declared > 0 && declared != actual) {
                throw BackupError.InvalidEntry(entry, "清单声明 $declared 条，实际解析 $actual 条")
            }
        }
        requireCount(BackupFormat.ENTRY_FAVORITES, manifest.favoriteCount, contents.favorites.size)
        requireCount(BackupFormat.ENTRY_GROUPS, manifest.groupCount, contents.groups.size)
        requireCount(BackupFormat.ENTRY_HISTORY, manifest.historyCount, contents.history.size)
        requireCount(BackupFormat.ENTRY_DOWNLOADS, manifest.downloadCount, contents.downloads.size)
        requireCount(BackupFormat.ENTRY_SEARCH, manifest.searchHistoryCount, contents.searchHistory.size)
        if (manifest.hasSettings && files[BackupFormat.ENTRY_SETTINGS] == null) {
            throw BackupError.InvalidEntry(BackupFormat.ENTRY_SETTINGS, "清单声明包含设置，但条目缺失")
        }
        if (manifest.hasUiConfig && files[BackupFormat.ENTRY_UI_CONFIG] == null) {
            throw BackupError.InvalidEntry(BackupFormat.ENTRY_UI_CONFIG, "清单声明包含界面配置，但条目缺失")
        }
    }

    private suspend fun writeAutoBackup(policy: BackupConflictPolicy): String? = runCatching {
        val dir = File(context.getExternalFilesDir(null), AUTO_BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()
        val stamp = System.currentTimeMillis()
        val name = "auto-" + stamp + ".zip"
        File(dir, name).writeBytes(exportBackup(emptyMap()))
        pruneAutoBackups(dir)
        name
    }.getOrElse { e ->
        AppLogger.logError("BackupManager", "自动备份失败: " + e.message, e)
        null
    }

    /** 自动快照按时间保留最近 [MAX_AUTO_BACKUPS] 份，避免反复导入无限累积占满外部存储。 */
    private fun pruneAutoBackups(dir: File) {
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.startsWith("auto-") && f.name.endsWith(".zip") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_AUTO_BACKUPS)
                ?.forEach { runCatching { it.delete() } }
        }
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    companion object {
        const val SCHEMA_VERSION = BackupFormat.SCHEMA_VERSION
        const val ENTRY_MANIFEST = BackupFormat.ENTRY_MANIFEST
        const val ENTRY_FAVORITES = BackupFormat.ENTRY_FAVORITES
        const val ENTRY_GROUPS = BackupFormat.ENTRY_GROUPS
        const val ENTRY_HISTORY = BackupFormat.ENTRY_HISTORY
        const val ENTRY_DOWNLOADS = BackupFormat.ENTRY_DOWNLOADS
        const val ENTRY_SEARCH = BackupFormat.ENTRY_SEARCH
        const val ENTRY_SETTINGS = BackupFormat.ENTRY_SETTINGS
        const val ENTRY_UI_CONFIG = BackupFormat.ENTRY_UI_CONFIG

        private const val AUTO_BACKUP_DIR = "backups"
        private const val MAX_AUTO_BACKUPS = 3
    }
}
