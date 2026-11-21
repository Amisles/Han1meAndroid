package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.PlaylistDetail
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UserPlaylistDetailViewModel @Inject constructor(
    private val content: ContentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _detail = MutableStateFlow<PlaylistDetail?>(null)
    val detail: StateFlow<PlaylistDetail?> = _detail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                when (val result = content.getPlaylistDetail(url)) {
                    is AppResult.Success -> _detail.value = result.data
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
