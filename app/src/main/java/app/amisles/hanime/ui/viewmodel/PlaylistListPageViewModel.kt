package app.amisles.hanime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.PlaylistSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlaylistListPageViewModel @Inject constructor(
    private val networkService: NetworkService
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPlaylists(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _playlists.value = networkService.fetchPlaylistListPage(url)
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
