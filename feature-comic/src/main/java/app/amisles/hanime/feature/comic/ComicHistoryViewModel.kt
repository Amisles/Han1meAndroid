package app.amisles.hanime.feature.comic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.ComicProgress
import app.amisles.hanime.domain.repository.ComicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ComicHistoryViewModel @Inject constructor(
    repository: ComicRepository
) : ViewModel() {

    val items: StateFlow<List<ComicProgress>?> = repository.observeComicProgressList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
