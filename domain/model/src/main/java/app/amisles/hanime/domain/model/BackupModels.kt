package app.amisles.hanime.domain.model

/** 备份包内容清单，写入 zip 内的 manifest.json。 */
data class BackupManifest(
    val schemaVersion: Int,
    val packageName: String,
    val appVersionName: String,
    val exportedAt: Long,
    val favoriteCount: Int,
    val groupCount: Int,
    val historyCount: Int,
    val downloadCount: Int,
    val searchHistoryCount: Int,
    val hasSettings: Boolean,
    val hasUiConfig: Boolean
)

/** 导入冲突策略。MERGE 保留已有条目并覆盖同主键项，OVERWRITE 先清空再写入。 */
enum class BackupConflictPolicy {
    MERGE,
    OVERWRITE
}

/** 可备份的用户设置。刻意不含登录 Cookie 与会话域名，避免备份文件泄露凭据。 */
data class BackupSettings(
    val appLanguage: String? = null,
    val themeMode: String? = null,
    val baseUrl: String? = null,
    val playbackSpeed: Float? = null,
    val preferredQuality: String? = null,
    val autoPlayNext: Boolean? = null,
    val loopPlayback: Boolean? = null,
    val longPressBoostSpeed: Float? = null,
    val doubleTapSeek: Boolean? = null,
    val autoQuality: Boolean? = null,
    val maxDownloadConcurrent: Int? = null,
    val downloadRateLimit: Int? = null,
    val subscriptionReminder: Boolean? = null,
    val downloadStoragePath: String? = null,
    val searchLayoutMode: String? = null
)

/** 解析后的备份内容。 */
data class BackupContents(
    val manifest: BackupManifest,
    val favorites: List<FavoriteVideo>,
    val groups: List<FavoriteGroup>,
    val history: List<WatchHistory>,
    val downloads: List<DownloadTask>,
    val searchHistory: List<Pair<String, Long>>,
    val settings: BackupSettings?,
    /** 界面定制配置原文，由调用方按 UiConfigCodec 解析。 */
    val uiConfigText: String?
)

/** 导入结果，用于界面反馈。 */
data class BackupImportSummary(
    val favorites: Int,
    val groups: Int,
    val history: Int,
    val downloads: Int,
    val searchHistory: Int,
    val settingsApplied: Boolean,
    val uiConfigApplied: Boolean,
    val autoBackupName: String?
)

/** 备份包校验失败的分类，供界面给出可操作的提示。 */
sealed class BackupError : Exception() {
    data object NotASupportedArchive : BackupError()
    data object MissingManifest : BackupError()
    data class UnsupportedSchema(val found: Int, val supported: Int) : BackupError()
    data class WrongPackage(val found: String) : BackupError()
}
