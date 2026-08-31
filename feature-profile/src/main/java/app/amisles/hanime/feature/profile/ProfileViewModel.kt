package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.util.AppLogger
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
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: HanimeRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _watchCount = MutableStateFlow(0)
    val watchCount: StateFlow<Int> = _watchCount.asStateFlow()

    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount.asStateFlow()

    private val _downloadCount = MutableStateFlow(0)
    val downloadCount: StateFlow<Int> = _downloadCount.asStateFlow()

    val displayLetter: StateFlow<String> = combine(
        Preferences.loginCookieFlow,
        Preferences.savedUserIdFlow
    ) { _, _ ->
        val raw = Preferences.extractDisplayEmail().ifBlank { Preferences.savedUserId }
        if (raw.isBlank()) "H" else raw.take(1).uppercase()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "H"
    )

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.logout()
            }
        }
    }

    fun loadCounts() {
        AppLogger.d("ProfileViewModel", "loadCounts called")
        viewModelScope.launch {
            try {
                _watchCount.value = withContext(Dispatchers.IO) {
                    repository.getWatchHistoryCount()
                }
                _favoriteCount.value = withContext(Dispatchers.IO) {
                    repository.getFavoriteCount()
                }
                _downloadCount.value = withContext(Dispatchers.IO) {
                    downloadManager.getCompletedDownloadCount()
                }
                AppLogger.d("ProfileViewModel", "Counts loaded: watch=${_watchCount.value}, fav=${_favoriteCount.value}, dl=${_downloadCount.value}")
            } catch (e: IOException) {
                AppLogger.e("ProfileViewModel", "Error loading counts: ${e.message}", e)
            }
        }
    }
}
