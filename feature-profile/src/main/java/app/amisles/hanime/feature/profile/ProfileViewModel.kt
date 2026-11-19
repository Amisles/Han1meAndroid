package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.repository.ReadingSettings
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    settings: ReadingSettings
) : ViewModel() {

    val watchStatsEnabled: StateFlow<Boolean> = settings.watchStatsEnabledFlow

    // 三项统计都从库表实时派生：抽屉内容首次展开后常驻组合，一次性查询会把统计冻在那一刻，
    // 只有重启应用才会变（个人页每次重新进入组合，所以看起来是好的）。
    val watchCount: StateFlow<Int> = library.getWatchHistoryCountFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favoriteCount: StateFlow<Int> = library.getFavoriteCountFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val downloadCount: StateFlow<Int> = downloads.getCompletedDownloadCountFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
                    session.logout()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preferences 未初始化等异常不应让登出流程崩溃
                AppLogger.e("ProfileViewModel", "登出失败: ${e.message}", e)
            }
        }
    }
}
