package app.amisles.hanime.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.AuthorPageData
import app.amisles.hanime.domain.model.AuthorPageDataEvent
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class AuthorViewModel @Inject constructor(
    private val networkService: NetworkService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _authorData = MutableStateFlow<AuthorPageData?>(null)
    val authorData: StateFlow<AuthorPageData?> = _authorData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadJob: Job? = null

    // G15：若 Videos/Playlists 事件先于 Profile 到达，先缓冲，待 Profile 到达时合并，避免被静默丢弃。
    private var pendingVideos: List<HanimeVideo>? = null
    private var pendingPlaylists: List<PlaylistSummary>? = null

    fun loadAuthorPage(authorPageUrl: String) {
        loadJob?.cancel() // 取消上一次未完成的请求（快速重进页面去重）
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _authorData.value = null
            pendingVideos = null
            pendingPlaylists = null
            var firstEventArrived = false
            networkService.fetchAuthorPageStream(authorPageUrl)
                .catch { e ->
                    if (!firstEventArrived) _isLoading.value = false
                    _error.value = e.message ?: context.getString(R.string.common_load_failed)
                }
                .collect { event ->
                    if (!firstEventArrived) {
                        firstEventArrived = true
                        _isLoading.value = false
                    }
                    when (event) {
                        is AuthorPageDataEvent.Profile -> {
                            var data = event.data
                            pendingVideos?.let { data = data.copy(videos = it) }
                            pendingPlaylists?.let { data = data.copy(playlists = it) }
                            pendingVideos = null
                            pendingPlaylists = null
                            _authorData.value = data
                        }
                        is AuthorPageDataEvent.Videos -> {
                            if (_authorData.value != null) {
                                _authorData.value = _authorData.value?.copy(videos = event.videos)
                            } else {
                                pendingVideos = event.videos
                            }
                        }
                        is AuthorPageDataEvent.Playlists -> {
                            if (_authorData.value != null) {
                                _authorData.value = _authorData.value?.copy(playlists = event.playlists)
                            } else {
                                pendingPlaylists = event.playlists
                            }
                        }
                        is AuthorPageDataEvent.Error -> _error.value = event.message
                    }
                }
        }
    }
}
