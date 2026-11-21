package app.amisles.hanime.feature.home.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.model.HanimeVideo
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
class VideoListPageViewModel @Inject constructor(
    private val content: ContentRepository,
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
                when (val result = content.getVideoListPage(url)) {
                    is AppResult.Success -> _videos.value = result.data.distinctBy { it.id }
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
