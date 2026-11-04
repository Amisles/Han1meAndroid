package app.amisles.hanime.feature.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.PlaylistSaveItem
import app.amisles.hanime.core.ui.components.PlaylistSaveUiState
import app.amisles.hanime.domain.model.PlaylistTargets
import app.amisles.hanime.domain.model.VideoSaveOptions
import app.amisles.hanime.domain.repository.PlaylistSyncRepository
import app.amisles.hanime.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistSaveViewModel @Inject constructor(
    private val sync: PlaylistSyncRepository,
    private val session: SessionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistSaveUiState())
    val state: StateFlow<PlaylistSaveUiState> = _state.asStateFlow()

    private val _savedTargets = MutableStateFlow<Set<String>>(emptySet())
    val savedTargets: StateFlow<Set<String>> = _savedTargets.asStateFlow()

    private val _watchLater = MutableStateFlow(false)
    val watchLater: StateFlow<Boolean> = _watchLater.asStateFlow()

    private var videoId: String = ""

    fun bind(id: String) {
        if (id.isBlank() || id == videoId) return
        videoId = id
        refresh()
    }

    fun onWatchPageLoaded(options: VideoSaveOptions) {
        if (options.videoId.isNotBlank() && options.videoId != videoId) {
            videoId = options.videoId
        }
        viewModelScope.launch {
            runCatching { sync.cacheFromWatchPage(options) }
            refresh()
        }
    }

    fun open() {
        if (!session.isLoggedIn) return
        _state.update { it.copy(visible = true, loading = it.items.isEmpty(), error = null) }
        refresh()
    }

    fun dismiss() {
        _state.update { it.copy(visible = false, loading = false) }
    }

    fun toggle(target: String) {
        if (!session.isLoggedIn || videoId.isBlank()) return
        val item = _state.value.items.firstOrNull { it.target == target } ?: return
        val next = !item.selected
        _state.update { state ->
            state.copy(
                items = state.items.map { if (it.target == target) it.copy(selected = next) else it },
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { sync.setSaved(videoId, target, next) }
                .onFailure {
                    _state.update { state ->
                        state.copy(error = context.getString(R.string.playlist_sheet_sync_failed))
                    }
                }
            runCatching { sync.flush() }
            refresh()
        }
    }

    fun toggleWatchLater() {
        if (!session.isLoggedIn || videoId.isBlank()) return
        _watchLater.value = !_watchLater.value
        viewModelScope.launch {
            sync.setWatchLater(videoId, _watchLater.value)
            runCatching { sync.flush() }
            refresh()
        }
    }

    fun create(title: String, description: String) {
        if (videoId.isBlank() || title.isBlank()) return
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            runCatching { sync.createPlaylistAndSave(videoId, title, description) }
                .onFailure {
                    _state.update { state ->
                        state.copy(
                            creating = false,
                            error = context.getString(R.string.playlist_sheet_create_failed)
                        )
                    }
                }
                .onSuccess { _state.update { it.copy(creating = false) } }
            refresh()
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            runCatching { sync.flush() }
            refresh()
        }
    }

    private fun refresh() {
        if (videoId.isBlank() || !session.isLoggedIn) {
            _state.update { it.copy(loading = false, items = emptyList(), pendingCount = 0) }
            _savedTargets.value = emptySet()
            _watchLater.value = false
            return
        }
        viewModelScope.launch {
            val playlists = runCatching { sync.cachedPlaylists() }.getOrDefault(emptyList())
            val saved = runCatching { sync.savedTargets(videoId) }.getOrDefault(emptySet())
            val pending = runCatching { sync.pendingCount() }.getOrDefault(0)
            _savedTargets.value = saved - PlaylistTargets.WATCH_LATER
            _watchLater.value = PlaylistTargets.WATCH_LATER in saved
            _state.update { state ->
                state.copy(
                    loading = false,
                    items = playlists.map {
                        PlaylistSaveItem(
                            target = it.remoteId,
                            title = it.title,
                            selected = it.remoteId in saved
                        )
                    },
                    pendingCount = pending
                )
            }
        }
    }
}
