package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.BackupConflictPolicy
import app.amisles.hanime.domain.model.BackupContents
import app.amisles.hanime.domain.model.BackupImportSummary
import app.amisles.hanime.domain.model.BackupManifest

interface BackupRepository {

    companion object {
        /** 备份包内保存界面配置（UiConfigStore）的条目名；导出与导入两侧共用。 */
        const val ENTRY_UI_CONFIG = "ui_config.txt"
    }

    /** 打包当前数据；[extraFiles] 用于携带调用方负责的附加内容（如界面配置）。 */
    suspend fun exportBackup(extraFiles: Map<String, String>): ByteArray

    /** 只解析清单用于展示，不落库。 */
    suspend fun inspectBackup(bytes: ByteArray): BackupManifest

    /**
     * 解析并导入。导入前会在应用外部目录留存一份当前数据快照。
     *
     * [applyUiConfig] 在数据库事务内执行：返回 false 会中止导入并回滚数据库，
     * 调用方不得把它当作「稍后再处理」的提示。
     */
    suspend fun importBackup(
        bytes: ByteArray,
        policy: BackupConflictPolicy,
        applyUiConfig: (String) -> Boolean
    ): Pair<BackupContents, BackupImportSummary>
}
