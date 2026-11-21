package app.amisles.hanime.feature.home.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.model.PlaylistDetail
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val content: ContentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _playlistDetail = MutableStateFlow<PlaylistDetail?>(null)
    val playlistDetail: StateFlow<PlaylistDetail?> = _playlistDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadPlaylistDetail(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = content.getPlaylistDetail(url)) {
                    is AppResult.Success -> _playlistDetail.value =
                        result.data.copy(videos = result.data.videos.distinctBy { it.id })
                    is AppResult.Error -> _error.value = ErrorMapper.message(
                        context,
                        result.exception,
                        result.code,
                        context.getString(R.string.common_load_failed)
                    )
                    is AppResult.Loading -> Unit
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
