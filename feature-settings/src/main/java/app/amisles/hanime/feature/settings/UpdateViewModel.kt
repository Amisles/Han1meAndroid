package app.amisles.hanime.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.AppUpdate
import app.amisles.hanime.domain.model.UpdateCheckResult
import app.amisles.hanime.domain.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 更新页的检查状态机。 */
sealed interface UpdateUiState {
    /** 尚未检查（进入页面时的初始态）。 */
    data object Idle : UpdateUiState

    /** 检查中。 */
    data object Checking : UpdateUiState

    /** 已是最新版本。 */
    data class UpToDate(val currentVersion: String) : UpdateUiState

    /** 发现新版本。 */
    data class Available(val update: AppUpdate, val currentVersion: String) : UpdateUiState

    /** 检查失败；[message] 可直接展示。 */
    data class Failed(val message: String) : UpdateUiState

    /**
     * 有新版本，但用户此前选择了「不再提示」。
     *
     * 必须与 [UpToDate] 区分开：跳过 1.7.0 的用户其实是「有更新但主动忽略」，
     * 若显示「已是最新版本」，就等于告诉用户一个假事实 —— 他会以为真的没有新版本。
     */
    data class Skipped(
        val update: AppUpdate,
        val currentVersion: String
    ) : UpdateUiState
}

/**
 * 更新页 ViewModel。
 *
 * 检查逻辑全部在 [UpdateRepository] 里，本类只负责：
 * - 把结果映射成界面状态；
 * - 承载「自动检查」开关与「不再提示」偏好；
 * - 提供安装包大小等展示用的派生值。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    val currentVersion: String = updates.currentVersion

    val autoCheckEnabled: StateFlow<Boolean> = updates.autoCheckEnabledFlow

    val skippedVersion: StateFlow<String> = updates.skippedVersionFlow

    init {
        // 进入页面即自动检查一次（force = false，仍受 6 小时节流约束）：
        // 用户点进「检查更新」就是想看到当前状态，没必要让他再点一次按钮。
        check(force = false)
    }

    /** @param force true 表示用户主动点击，跳过节流并明确反馈结果。 */
    fun check(force: Boolean) {
        if (_uiState.value is UpdateUiState.Checking) return
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            try {
                _uiState.value = when (val result = updates.checkForUpdate(force = force)) {
                    is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(result.currentVersion)
                    is UpdateCheckResult.Available ->
                        // 仓储已按跳过规则决定是否发布到 availableUpdate，但页面需要保留
                        // 「有这个版本、只是你选择了忽略」的信息，故此处不复用那份状态。
                        if (result.update.versionName == updates.skippedVersion) {
                            UpdateUiState.Skipped(result.update, result.currentVersion)
                        } else {
                            UpdateUiState.Available(result.update, result.currentVersion)
                        }
                    is UpdateCheckResult.Failure -> UpdateUiState.Failed(result.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 仓储已把预期异常收敛为 Failure，这里兜住真正的意外，避免页面卡在 Checking
                AppLogger.e("UpdateViewModel", "检查更新异常: ${e.message}", e)
                _uiState.value = UpdateUiState.Failed(e.message ?: "检查更新失败")
            }
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        updates.setAutoCheckEnabled(enabled)
    }

    /** 「不再提示此版本」：记录版本号，并把界面切到「已忽略」态（不是「已是最新」）。 */
    fun skipCurrentVersion() {
        val state = _uiState.value
        if (state !is UpdateUiState.Available) return
        updates.skipVersion(state.update.versionName)
        _uiState.value = UpdateUiState.Skipped(state.update, state.currentVersion)
    }

    /** 清除「不再提示」记录，使被跳过的版本重新参与提示。 */
    fun clearSkippedVersion() {
        updates.clearSkippedVersion()
        check(force = true)
    }
}
