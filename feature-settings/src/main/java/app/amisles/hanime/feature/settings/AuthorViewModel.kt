package app.amisles.hanime.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.AuthorPageData
import app.amisles.hanime.domain.model.AuthorPageDataEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthorViewModel @Inject constructor(
    private val networkService: NetworkService
) : ViewModel() {

    private val _authorData = MutableStateFlow<AuthorPageData?>(null)
    val authorData: StateFlow<AuthorPageData?> = _authorData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadJob: Job? = null

    fun loadAuthorPage(authorPageUrl: String) {
        loadJob?.cancel() // 取消上一次未完成的请求（快速重进页面去重）
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _authorData.value = null
            var firstEventArrived = false
            networkService.fetchAuthorPageStream(authorPageUrl)
                .catch { e ->
                    if (!firstEventArrived) _isLoading.value = false
                    _error.value = e.message ?: "加载失败"
                }
                .collect { event ->
                    if (!firstEventArrived) {
                        firstEventArrived = true
                        _isLoading.value = false
                    }
                    when (event) {
                        is AuthorPageDataEvent.Profile -> _authorData.value = event.data
                        is AuthorPageDataEvent.Videos ->
                            _authorData.value = _authorData.value?.copy(videos = event.videos)
                        is AuthorPageDataEvent.Playlists ->
                            _authorData.value = _authorData.value?.copy(playlists = event.playlists)
                        is AuthorPageDataEvent.Error -> _error.value = event.message
                    }
                }
        }
    }
}
