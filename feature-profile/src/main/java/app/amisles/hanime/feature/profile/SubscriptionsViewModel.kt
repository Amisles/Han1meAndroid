package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.SubscribedArtist
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val networkService: NetworkService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _artists = MutableStateFlow<List<SubscribedArtist>>(emptyList())
    val artists: StateFlow<List<SubscribedArtist>> = _artists.asStateFlow()

    private val _videos = MutableStateFlow<List<HanimeVideo>>(emptyList())
    val videos: StateFlow<List<HanimeVideo>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 当前筛选的作者名（空字符串 = 全部）
    private val _selectedQuery = MutableStateFlow("")
    val selectedQuery: StateFlow<String> = _selectedQuery.asStateFlow()

    private var loadJob: Job? = null

    fun load(query: String = _selectedQuery.value) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _selectedQuery.value = query
            try {
                val result = networkService.fetchSubscriptionsPage(query)
                _artists.value = result.artists
                _videos.value = result.videos
            } catch (e: Exception) {
                _error.value = e.message ?: context.getString(R.string.common_load_failed)
            } finally {
                // 被取消（loadJob?.cancel）时跳过，避免把新一轮加载的 isLoading 误置回 false
                if (coroutineContext.isActive) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun selectArtist(name: String) {
        if (name == _selectedQuery.value) return
        load(name)
    }
}
