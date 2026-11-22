package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.BackupConflictPolicy
import app.amisles.hanime.domain.model.BackupContents
import app.amisles.hanime.domain.model.BackupImportSummary
import app.amisles.hanime.domain.model.BackupManifest

interface BackupRepository {

    companion object {
        /** 备份包内保存界面配置的条目名；导出与导入两侧共用。 */
        const val ENTRY_UI_CONFIG = "ui_config.txt"
    }

    /** 打包当前数据；界面配置由数据层通过 [app.amisles.hanime.domain.preferences.UiConfigBackup] 读取。 */
    suspend fun exportBackup(): ByteArray

    /** 只解析清单用于展示，不落库。 */
    suspend fun inspectBackup(bytes: ByteArray): BackupManifest

    /**
     * 解析并导入。导入前会在应用外部目录留存一份当前数据快照。
     *
     * 界面配置与设置都在数据库事务内应用；任一步失败都会回滚并恢复导入前的配置快照。
     */
    suspend fun importBackup(
        bytes: ByteArray,
        policy: BackupConflictPolicy
    ): Pair<BackupContents, BackupImportSummary>
}
