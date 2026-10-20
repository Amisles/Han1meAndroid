package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.LibraryRepository
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
    private val downloads: DownloadRepository
) : ViewModel() {

    private val _watchCount = MutableStateFlow(0)
    val watchCount: StateFlow<Int> = _watchCount.asStateFlow()

    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount.asStateFlow()

    private val _downloadCount = MutableStateFlow(0)
    val downloadCount: StateFlow<Int> = _downloadCount.asStateFlow()

    val displayLetter: StateFlow<String> = combine(
        session.loginCookieFlow,
        session.savedUserIdFlow
    ) { cookie, userId ->
        val raw = session.extractDisplayEmail().ifBlank { userId }
        if (raw.isBlank()) "H" else raw.take(1).uppercase()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "H"
    )

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
