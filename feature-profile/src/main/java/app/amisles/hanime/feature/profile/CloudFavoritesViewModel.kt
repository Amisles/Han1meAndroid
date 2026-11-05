package app.amisles.hanime.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CloudFavoritesViewModel @Inject constructor(
    private val content: ContentRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val loggedIn: StateFlow<Boolean> = session.loginStateFlow

    private val _showWatchLater = MutableStateFlow(false)
    val showWatchLater: StateFlow<Boolean> = _showWatchLater.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = _playlists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _watchLater = MutableStateFlow<List<HanimeVideo>>(emptyList())
    val watchLater: StateFlow<List<HanimeVideo>> = _watchLater.asStateFlow()

    private val _watchLaterLoading = MutableStateFlow(false)
    val watchLaterLoading: StateFlow<Boolean> = _watchLaterLoading.asStateFlow()

    private val _watchLaterError = MutableStateFlow<String?>(null)
    val watchLaterError: StateFlow<String?> = _watchLaterError.asStateFlow()

    fun selectSection(watchLater: Boolean) {
        if (_showWatchLater.value == watchLater) return
        _showWatchLater.value = watchLater
        if (watchLater) refreshWatchLater() else refreshPlaylists()
    }

    fun refresh() {
        if (_showWatchLater.value) refreshWatchLater() else refreshPlaylists()
    }

    fun refreshPlaylists() {
        val userId = session.savedUserId
        if (!session.isLoggedIn || userId.isBlank()) {
            clearPlaylists()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val url = settings.baseUrl.trimEnd('/') + "/user/" + userId + "/playlists"
                _playlists.value = content.getPlaylistListPage(url).distinctBy { it.playlistUrl }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = ErrorMapper.message(
                    context,
                    e,
                    context.getString(R.string.common_load_failed)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshWatchLater() {
        val userId = session.savedUserId
        if (!session.isLoggedIn || userId.isBlank()) {
            clearWatchLater()
            return
        }
        viewModelScope.launch {
            _watchLaterLoading.value = true
            _watchLaterError.value = null
            try {
                val url = settings.baseUrl.trimEnd('/') + "/user/" + userId + "/saves"
                _watchLater.value = content.getWatchLaterPage(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _watchLaterError.value = ErrorMapper.message(
                    context,
                    e,
                    context.getString(R.string.common_load_failed)
                )
            } finally {
                _watchLaterLoading.value = false
            }
        }
    }

    private fun clearPlaylists() {
        _playlists.value = emptyList()
        _error.value = null
        _isLoading.value = false
    }

    private fun clearWatchLater() {
        _watchLater.value = emptyList()
        _watchLaterError.value = null
        _watchLaterLoading.value = false
    }
}
