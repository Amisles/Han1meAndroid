package app.amisles.hanime.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.preferences.ThemeMode
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

/** 设置页 UI 状态：聚合所有设置项当前值。 */
data class SettingsUiState(
    val appLanguage: String = SettingsRepository.LANGUAGE_ZH_CN,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val maxDownloadConcurrent: Int = DEFAULT_MAX_DOWNLOAD_CONCURRENT,
    val downloadRateLimit: Int = 0,
    val subscriptionReminder: Boolean = false,
    val baseUrl: String = SettingsRepository.DEFAULT_BASE_URL,
    val isLoginSupported: Boolean = true,
    val downloadStoragePath: String = "",
    val longPressBoostSpeed: Float = 2f,
    val doubleTapSeek: Boolean = true,
    val autoQuality: Boolean = true,
    val watchStatsEnabled: Boolean = true
) {
    val isDefaultBaseUrl: Boolean get() = baseUrl == SettingsRepository.DEFAULT_BASE_URL

    companion object {
        /** 与持久化层的默认并发数保持一致。 */
        const val DEFAULT_MAX_DOWNLOAD_CONCURRENT = 3
    }
}

/** 一次性 UI 事件（Toast / 重建 Activity 等），避免在 Composable 中直接操作 Context。 */
sealed class SettingsUiEvent {
    data class Toast(val messageResId: Int, val arg: Any? = null) : SettingsUiEvent()
    /** 语言切换后需要 recreate Activity。 */
    object RecreateActivity : SettingsUiEvent()
}

/**
 * 设置页 ViewModel：封装所有 Preferences 读写，UI 层不再直接操作 Preferences。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val settings: SettingsRepository,
    private val session: SessionRepository
) : AndroidViewModel(app) {

    private val _events = MutableSharedFlow<SettingsUiEvent>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.appLanguageFlow,
        settings.themeModeFlow,
        settings.maxDownloadConcurrentFlow,
        settings.baseUrlFlow,
        settings.loginSupportedFlow
    ) { language, theme, concurrent, url, loginSupported ->
        SettingsUiState(
            appLanguage = language,
            themeMode = theme,
            maxDownloadConcurrent = concurrent,
            baseUrl = url,
            isLoginSupported = loginSupported,
            downloadStoragePath = settings.downloadStoragePathFlow.value
        )
    }.combine(settings.downloadRateLimitFlow) { state, rateLimit ->
        state.copy(downloadRateLimit = rateLimit)
    }.combine(settings.subscriptionReminderFlow) { state, enabled ->
        state.copy(subscriptionReminder = enabled)
    }.combine(settings.watchStatsEnabledFlow) { state, enabled ->
        state.copy(watchStatsEnabled = enabled)
    }.combine(settings.downloadStoragePathFlow) { state, storagePath ->
        state.copy(downloadStoragePath = storagePath)
    }.combine(settings.longPressBoostSpeedFlow) { state, speed ->
        state.copy(longPressBoostSpeed = speed)
    }.combine(settings.doubleTapSeekFlow) { state, enabled ->
        state.copy(doubleTapSeek = enabled)
    }.combine(settings.autoQualityFlow) { state, enabled ->
        state.copy(autoQuality = enabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = initialState()
    )

    /** 用当前持久化值构造初始态，只在 VM 初始化时调用一次。 */
    private fun initialState(): SettingsUiState = SettingsUiState(
        appLanguage = settings.appLanguage,
        themeMode = settings.themeMode,
        maxDownloadConcurrent = settings.maxDownloadConcurrent,
        downloadRateLimit = settings.downloadRateLimit,
        subscriptionReminder = settings.subscriptionReminder,
        watchStatsEnabled = settings.watchStatsEnabled,
        baseUrl = settings.baseUrl,
        isLoginSupported = settings.isLoginSupported,
        downloadStoragePath = settings.downloadStoragePathFlow.value,
        longPressBoostSpeed = settings.longPressBoostSpeed,
        doubleTapSeek = settings.doubleTapSeek,
        autoQuality = settings.autoQuality
    )

    fun setAppLanguage(code: String) {
        settings.setAppLanguage(code)
        viewModelScope.launch { _events.emit(SettingsUiEvent.RecreateActivity) }
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.setThemeMode(mode)
    }

    fun setMaxDownloadConcurrent(count: Int) {
        settings.setMaxDownloadConcurrent(count)
    }

    fun setDownloadRateLimit(megabytesPerSecond: Int) {
        settings.setDownloadRateLimit(megabytesPerSecond)
    }

    fun setSubscriptionReminder(enabled: Boolean) {
        settings.setSubscriptionReminder(enabled)
    }

    fun setWatchStatsEnabled(enabled: Boolean) {
        settings.setWatchStatsEnabled(enabled)
    }

    fun setLongPressBoostSpeed(speed: Float) {
        settings.setLongPressBoostSpeed(speed)
    }

    fun setDoubleTapSeek(enabled: Boolean) {
        settings.setDoubleTapSeek(enabled)
    }

    fun setAutoQuality(enabled: Boolean) {
        settings.setAutoQuality(enabled)
    }

    /** 设置站点基址；返回 true = 已生效，false = 输入非法（非 https 域名）且原设置不变。 */
    fun setBaseUrl(url: String): Boolean = settings.setBaseUrl(url)

    fun restoreDefaultBaseUrl() {
        settings.setBaseUrl(SettingsRepository.DEFAULT_BASE_URL)
    }

    /** 设置下载存储路径；空串表示「默认目录」，非空路径会做可写校验，不可写则保留原设置并提示。 */
    fun setDownloadStoragePath(rawPath: String) {
        val path = rawPath.trim()
        if (path.isBlank()) {
            settings.downloadStoragePath = ""
            viewModelScope.launch { _events.emit(SettingsUiEvent.Toast(R.string.settings_download_storage_restored)) }
            return
        }
        val dir = File(path)
        // 可写性校验（含 mkdirs）与偏好写入都切到 IO 调度器，避免主线程卡顿
        viewModelScope.launch {
            val writable = withContext(Dispatchers.IO) {
                val ok = runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    dir.exists() && dir.isDirectory && dir.canWrite()
                }.getOrDefault(false)
                if (ok) settings.downloadStoragePath = path
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

    /** 清除应用缓存；删除在 IO 调度器上执行，完成后通过 [events] 发送成功/失败 Toast。 */
    fun clearAppCache() {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val cacheDir = app.cacheDir
                    if (!cacheDir.exists()) 0
                    // 统计删除失败的条目（被占用的文件会删除失败），据此提示用户真实结果
                    else cacheDir.listFiles()?.count { !it.deleteRecursively() } ?: 0
                }
            }
            result.fold(
                onSuccess = { failed ->
                    if (failed > 0) {
                        AppLogger.e("SettingsViewModel", "清除缓存未完全成功: $failed 项未能删除")
                        _events.emit(SettingsUiEvent.Toast(R.string.settings_cache_clear_failed))
                    } else {
                        _events.emit(SettingsUiEvent.Toast(R.string.settings_cache_cleared))
                    }
                },
                onFailure = { e ->
                    AppLogger.e("SettingsViewModel", "清除缓存失败: ${e.message}", e)
                    _events.emit(SettingsUiEvent.Toast(R.string.settings_cache_clear_failed))
                }
            )
        }
    }
}
