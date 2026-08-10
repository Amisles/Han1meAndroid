package app.amisles.hanime.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 UI 状态：聚合所有设置项当前值。
 */
data class SettingsUiState(
    val appLanguage: String = Preferences.LANGUAGE_ZH_CN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val maxDownloadConcurrent: Int = 3,
    val baseUrl: String = Preferences.DEFAULT_BASE_URL,
    val isLoginSupported: Boolean = true
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
            isLoginSupported = loginSupported
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(
            appLanguage = Preferences.appLanguage,
            themeMode = Preferences.themeMode,
            maxDownloadConcurrent = Preferences.maxDownloadConcurrent,
            baseUrl = Preferences.baseUrl,
            isLoginSupported = Preferences.isLoginSupported
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
     * 清除应用缓存。返回缓存目录路径用于 UI 显示，实际删除在 VM 中完成。
     */
    fun clearAppCache() {
        viewModelScope.launch {
            try {
                val cacheDir = app.cacheDir
                if (cacheDir.exists()) {
                    cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
