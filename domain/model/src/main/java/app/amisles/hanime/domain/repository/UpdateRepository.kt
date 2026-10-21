package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.UpdateCheckResult
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用更新检查。
 *
 * 与 [ContentRepository] / [AccountRepository] 分开：更新检查访问的是 GitHub 而不是站点，
 * 不携带任何会话凭据，也不受用户切换镜像站的影响 —— 生命周期与故障域都与内容接口不同。
 */
interface UpdateRepository {

    /** 当前安装版本号（`versionName`），如 `1.6.0`。 */
    val currentVersion: String

    /**
     * 最近一次检查得到的「有可用更新」结果。
     *
     * 启动时的一次静默检查与更新页里的手动检查都会写入这里；界面订阅它以决定是否弹提示。
     * 为 null 表示当前没有需要提示的更新（已是最新、检查失败、或用户已选择跳过）。
     */
    val availableUpdate: StateFlow<app.amisles.hanime.domain.model.AppUpdate?>

    /**
     * 检查是否有新版本。
     *
     * @param includePrerelease 是否把预发布版也算作更新；默认 false。
     * @param force 为 true 时跳过节流（用户手动点「检查更新」）；默认 false，
     *   此时若距上次检查不足 [CHECK_INTERVAL_MS] 且已有结果，直接复用上次结果，
     *   避免每次进入应用都打一次网络。
     */
    suspend fun checkForUpdate(
        includePrerelease: Boolean = false,
        force: Boolean = false
    ): UpdateCheckResult

    /**
     * 把某个版本记为「不再提示」。
     *
     * 记录的是版本号本身而不是时间戳：用户跳过了 1.6.0，之后 1.7.0 发布时仍会重新提示。
     */
    fun skipVersion(versionName: String)

    /** 清除「不再提示」记录，使被跳过的版本重新参与提示。 */
    fun clearSkippedVersion()

    /** 被用户跳过的版本号；空串表示没有跳过任何版本。 */
    val skippedVersion: String
    val skippedVersionFlow: StateFlow<String>

    /** 是否允许在启动时自动检查更新（用户可在更新页关闭）。 */
    val autoCheckEnabled: Boolean
    val autoCheckEnabledFlow: StateFlow<Boolean>
    fun setAutoCheckEnabled(enabled: Boolean)

    companion object {
        /** 启动期静默检查的最小间隔；避免短时间内反复进出应用时重复请求 GitHub。 */
        const val CHECK_INTERVAL_MS: Long = 6L * 60 * 60 * 1000
    }
}
