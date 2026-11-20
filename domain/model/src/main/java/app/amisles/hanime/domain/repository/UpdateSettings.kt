package app.amisles.hanime.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 应用更新设置：跳过版本与启动时自动检查。 */
interface UpdateSettings {
    /** 被用户选择「不再提示」的版本号；空串表示没有跳过任何版本。 */
    val skippedVersion: String
    val skippedVersionFlow: StateFlow<String>
    fun setSkippedVersion(versionName: String)

    val autoCheckEnabled: Boolean
    val autoCheckEnabledFlow: StateFlow<Boolean>
    fun setAutoCheckEnabled(enabled: Boolean)
}
