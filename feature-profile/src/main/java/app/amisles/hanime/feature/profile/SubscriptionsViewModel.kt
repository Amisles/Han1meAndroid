package app.amisles.hanime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.SubscribedArtist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val networkService: NetworkService
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

    fun load(query: String = _selectedQuery.value) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _selectedQuery.value = query
            try {
                val result = networkService.fetchSubscriptionsPage(query)
                _artists.value = result.artists
                _videos.value = result.videos
            } catch (e: IOException) {
                _error.value = e.message ?: "加载失败"
            } catch (e: IllegalStateException) {
                _error.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectArtist(name: String) {
        if (name == _selectedQuery.value) return
        load(name)
    }
}
