package app.amisles.hanime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.ui.components.VideoLocalState
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class VideoLocalStateViewModel @Inject constructor(
    library: LibraryRepository,
    downloads: DownloadRepository
) : ViewModel() {

    val state: StateFlow<VideoLocalState> = combine(
        downloads.tasks
            .map { tasks ->
                tasks.asSequence()
                    .filter { it.status == DownloadStatus.COMPLETED && it.videoId.isNotBlank() }
                    .map { it.videoId }
                    .toSet()
            }
            .distinctUntilChanged(),
        library.getFavoriteIdsFlow().map { it.toSet() }.distinctUntilChanged(),
        library.getWatchedIdsFlow().map { it.toSet() }.distinctUntilChanged()
    ) { downloaded, favorite, watched ->
        VideoLocalState(downloaded = downloaded, favorite = favorite, watched = watched)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoLocalState())
}
