package app.amisles.hanime.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.preferences.ThemeMode
import app.amisles.hanime.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import app.amisles.hanime.core.common.util.AppLogger

/**
 * 设置页 UI 状态：聚合所有设置项当前值。
 */
data class SettingsUiState(
    val appLanguage: String = Preferences.LANGUAGE_ZH_CN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val maxDownloadConcurrent: Int = 3,
    val baseUrl: String = Preferences.DEFAULT_BASE_URL,
    val isLoginSupported: Boolean = true,
    val downloadStoragePath: String = Preferences.downloadStoragePath
) {
    val isDefaultBaseUrl: Boolean get() = baseUrl == Preferences.DEFAULT_BASE_URL
}

/**
 * 一次性 UI 事件（Toast / 重建 Activity 等），避免在 Composable 中直接操作 Context。
 */
sealed class SettingsUiEvent {
    data class Toast(val messageResId: Int, val arg: Any? = null) : SettingsUiEvent()
    /** 语言切换后需要 recreate Activity */
    object RecreateActivity : SettingsUiEvent()
}

/**
 * 设置页 ViewModel：封装所有 Preferences 读写，UI 层不再直接操作 Preferences。
 *
 * - 通过 [uiState] 暴露当前设置项聚合状态（单一数据源）
 * - 通过 [events] 暴露一次性 UI 事件（Toast / recreate）
 * - 所有写操作（setLanguage / setThemeMode / setMaxConcurrent / setBaseUrl / restoreDefaultBaseUrl）
 *   均走 VM，UI 只需调用对应方法
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application
) : AndroidViewModel(app) {

    private val _events = MutableSharedFlow<SettingsUiEvent>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        Preferences.appLanguageFlow,
        Preferences.themeModeFlow,
        Preferences.maxDownloadConcurrentFlow,
        Preferences.baseUrlFlow,
        Preferences.loginSupportedFlow
    ) { language, theme, concurrent, url, loginSupported ->
        SettingsUiState(
            appLanguage = language,
            themeMode = theme,
            maxDownloadConcurrent = concurrent,
            baseUrl = url,
            isLoginSupported = loginSupported,
            downloadStoragePath = Preferences.downloadStoragePath
        )
    }.combine(Preferences.downloadStoragePathFlow) { state, storagePath ->
        state.copy(downloadStoragePath = storagePath)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(
            appLanguage = Preferences.appLanguage,
            themeMode = Preferences.themeMode,
            maxDownloadConcurrent = Preferences.maxDownloadConcurrent,
            baseUrl = Preferences.baseUrl,
            isLoginSupported = Preferences.isLoginSupported,
            downloadStoragePath = Preferences.downloadStoragePath
        )
    )

    fun setAppLanguage(code: String) {
        Preferences.setAppLanguage(code)
        viewModelScope.launch { _events.emit(SettingsUiEvent.RecreateActivity) }
    }

    fun setThemeMode(mode: ThemeMode) {
        Preferences.setThemeMode(mode)
    }

    fun setMaxDownloadConcurrent(count: Int) {
        Preferences.setMaxDownloadConcurrent(count)
    }

    fun setBaseUrl(url: String) {
        Preferences.setBaseUrl(url)
    }

    fun restoreDefaultBaseUrl() {
        Preferences.setBaseUrl(Preferences.DEFAULT_BASE_URL)
    }

    /**
     * 设置下载存储路径。空串表示「默认目录」（由 DownloadManager 解析）。
     * 非空路径会做可写校验：不可写则保留原设置并提示，避免写入失败。
     */
    fun setDownloadStoragePath(rawPath: String) {
        val path = rawPath.trim()
        if (path.isBlank()) {
            Preferences.downloadStoragePath = ""
            viewModelScope.launch { _events.emit(SettingsUiEvent.Toast(R.string.settings_download_storage_restored)) }
            return
        }
        val dir = File(path)
        // G7：可写性校验（含 mkdirs）与偏好写入都切到 IO 调度器，避免主线程卡顿
        viewModelScope.launch {
            val writable = withContext(Dispatchers.IO) {
                val ok = runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    dir.exists() && dir.isDirectory && dir.canWrite()
                }.getOrDefault(false)
                if (ok) Preferences.downloadStoragePath = path
                ok
            }
            _events.emit(
                SettingsUiEvent.Toast(
                    if (writable) R.string.settings_download_storage_updated
                    else R.string.settings_download_storage_unwritable
                )
            )
        }
    }

    /**
     * 清除应用缓存。删除在 IO 调度器上执行，完成后通过 [events] 发送成功/失败 Toast
     * （G6：此前在主线程递归删除，且 UI 在点击瞬间无条件提示「已清除」，与实际结果脱节）。
     */
    fun clearAppCache() {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val cacheDir = app.cacheDir
                    if (cacheDir.exists()) {
                        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                }
            }
            result.fold(
                onSuccess = { _events.emit(SettingsUiEvent.Toast(R.string.settings_cache_cleared)) },
                onFailure = { e ->
                    AppLogger.e("SettingsViewModel", "清除缓存失败: ${e.message}", e)
                    _events.emit(SettingsUiEvent.Toast(R.string.settings_cache_clear_failed))
                }
            )
        }
    }
}
