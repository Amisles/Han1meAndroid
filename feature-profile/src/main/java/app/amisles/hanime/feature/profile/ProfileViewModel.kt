package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.download.DownloadManagerHolder
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel : ViewModel() {
    private val repository = HanimeRepository.getInstance()

    private val _watchCount = MutableStateFlow(0)
    val watchCount: StateFlow<Int> = _watchCount.asStateFlow()

    private val _favoriteCount = MutableStateFlow(0)
    val favoriteCount: StateFlow<Int> = _favoriteCount.asStateFlow()

    private val _downloadCount = MutableStateFlow(0)
    val downloadCount: StateFlow<Int> = _downloadCount.asStateFlow()

    fun initDatabase(context: Context) {
        repository.initDatabase(context)
    }

    fun loadCounts(context: Context) {
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
                    DownloadManagerHolder.getInstance(context).getCompletedDownloadCount()
                }
                AppLogger.d("ProfileViewModel", "Counts loaded: watch=${_watchCount.value}, fav=${_favoriteCount.value}, dl=${_downloadCount.value}")
            } catch (e: Exception) {
                AppLogger.e("ProfileViewModel", "Error loading counts: ${e.message}", e)
            }
        }
    }
}