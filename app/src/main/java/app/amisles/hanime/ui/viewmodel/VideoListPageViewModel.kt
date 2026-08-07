package app.amisles.hanime.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.domain.model.HanimeVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class VideoListPageViewModel @Inject constructor(
    private val networkService: NetworkService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _videos = MutableStateFlow<List<HanimeVideo>>(emptyList())
    val videos: StateFlow<List<HanimeVideo>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadVideos(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _videos.value = networkService.fetchVideoListPage(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: context.getString(R.string.common_load_failed)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
