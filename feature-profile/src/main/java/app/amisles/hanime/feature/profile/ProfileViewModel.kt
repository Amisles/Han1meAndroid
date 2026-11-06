package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val downloads: DownloadRepository,
    settings: SettingsRepository
) : ViewModel() {

    val watchStatsEnabled: StateFlow<Boolean> = settings.watchStatsEnabledFlow

    private val _watchCount = MutableStateFlow(0)
    val watchCount: StateFlow<Int> = _watchCount.asStateFlow()

    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount.asStateFlow()

    private val _downloadCount = MutableStateFlow(0)
    val downloadCount: StateFlow<Int> = _downloadCount.asStateFlow()

    /** 登录用户的昵称；未登录或尚未取到时为空串，由界面回退到通用文案。 */
    val displayName: StateFlow<String> = combine(
        session.activeLoginStateFlow,
        session.savedUserNameFlow
    ) { loggedIn, name ->
        if (loggedIn) name else ""
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = session.savedUserName
    )

    val displayLetter: StateFlow<String> = combine(
        session.loginCookieFlow,
        session.savedUserIdFlow,
        session.savedUserNameFlow
    ) { cookie, userId, name ->
        val raw = name.ifBlank { session.extractDisplayEmail().ifBlank { userId } }
        if (raw.isBlank()) "H" else raw.take(1).uppercase()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "H"
    )

    /** 昵称本地还没有时才向站点取一次，避免每次打开抽屉都发请求。 */
    private var accountNameRequested = false

    init {
        // 换账号后昵称已随登出清空，需要允许重新取一次
        viewModelScope.launch {
            session.loginStateFlow.collect { loggedIn -> if (!loggedIn) accountNameRequested = false }
        }
    }

    fun refreshAccountName() {
        if (!session.isActiveLogin || session.savedUserName.isNotBlank() || accountNameRequested) return
        accountNameRequested = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { account.refreshAccountName() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ProfileViewModel", "刷新昵称失败: ${e.message}", e)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    account.logout()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preferences 未初始化等异常不应让登出流程崩溃
                AppLogger.e("ProfileViewModel", "登出失败: ${e.message}", e)
            }
        }
    }

    fun loadCounts() {
        AppLogger.d("ProfileViewModel", "loadCounts called")
        viewModelScope.launch {
            try {
                _watchCount.value = withContext(Dispatchers.IO) {
                    library.getWatchHistoryCount()
                }
                _favoriteCount.value = withContext(Dispatchers.IO) {
                    library.getFavoriteCount()
                }
                _downloadCount.value = withContext(Dispatchers.IO) {
                    downloads.getCompletedDownloadCount()
                }
                AppLogger.d("ProfileViewModel", "Counts loaded: watch=${_watchCount.value}, fav=${_favoriteCount.value}, dl=${_downloadCount.value}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("ProfileViewModel", "Error loading counts: ${e.message}", e)
            }
        }
    }
}
