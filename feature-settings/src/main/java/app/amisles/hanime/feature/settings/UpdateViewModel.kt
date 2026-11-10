package app.amisles.hanime.feature.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.ApkDownloadResult
import app.amisles.hanime.domain.model.ApkFailureReason
import app.amisles.hanime.domain.model.AppUpdate
import app.amisles.hanime.domain.model.UpdateCheckResult
import app.amisles.hanime.domain.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/** 安装包下载状态。 */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: Float) : UpdateDownloadState
    data object Launched : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * 更新页 ViewModel。
 *
 * 检查逻辑全部在 [UpdateRepository] 里，本类负责：
 * - 把结果映射成界面状态；
 * - 承载「自动检查」开关与「不再提示」偏好；
 * - 提供 currentVersion 等展示值；
 * - 下载并安装 APK（downloadAndInstall / installApk）与失败原因映射（failureMessage）。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

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
                _uiState.value = UpdateUiState.Failed(
                    ErrorMapper.message(
                        context,
                        e,
                        context.getString(R.string.common_load_failed)
                    )
                )
            }
        }
    }

    fun downloadAndInstall(update: AppUpdate) {
        if (update.apkUrl.isBlank()) {
            openReleasePage(context, update.htmlUrl)
            return
        }
        if (_downloadState.value is UpdateDownloadState.Downloading) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _downloadState.value = UpdateDownloadState.Downloading(0f)
            val result = updates.downloadApk(update) { progress ->
                _downloadState.value = UpdateDownloadState.Downloading(progress)
            }
            _downloadState.value = when (result) {
                is ApkDownloadResult.Success ->
                    if (installApk(result.filePath)) {
                        UpdateDownloadState.Launched
                    } else {
                        UpdateDownloadState.Failed(
                            context.getString(R.string.update_install_failed)
                        )
                    }

                is ApkDownloadResult.Failure -> UpdateDownloadState.Failed(failureMessage(result))
            }
        }
    }

    fun clearDownloadState() {
        if (_downloadState.value is UpdateDownloadState.Downloading) return
        _downloadState.value = UpdateDownloadState.Idle
    }

    private fun failureMessage(result: ApkDownloadResult.Failure): String = when (result.reason) {
        ApkFailureReason.SIGNATURE ->
            context.getString(R.string.update_download_signature_mismatch)

        ApkFailureReason.STORAGE -> context.getString(R.string.update_download_storage)

        ApkFailureReason.NETWORK, ApkFailureReason.UNKNOWN -> ErrorMapper.message(
            context,
            result.throwable,
            context.getString(R.string.update_download_failed)
        )

        ApkFailureReason.NO_APK, ApkFailureReason.HTTP ->
            context.getString(R.string.update_download_failed)
    }

    private fun installApk(path: String): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            File(path)
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrElse { e ->
        AppLogger.e("UpdateViewModel", "拉起安装程序失败: ${e.message}", e)
        false
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
