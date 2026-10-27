package app.amisles.hanime.feature.comic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.ComicDetail
import app.amisles.hanime.domain.model.ComicProgress
import app.amisles.hanime.domain.repository.ComicRepository
import app.amisles.hanime.domain.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ComicDetailUiState(
    val detail: ComicDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val progress: ComicProgress? = null
)

@HiltViewModel
class ComicDetailViewModel @Inject constructor(
    private val comics: ComicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ComicDetailUiState())
    val state: StateFlow<ComicDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private var loadJob: Job? = null
    private var progressJob: Job? = null

    fun load(comicId: String, force: Boolean = false) {
        if (!force && loadedId == comicId && _state.value.detail != null) return
        loadedId = comicId
        observeProgress(comicId)
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { comics.getComicDetail(comicId) }
                when (result) {
                    is AppResult.Success ->
                        _state.value = _state.value.copy(detail = result.data, error = null)
                    is AppResult.Error -> _state.value = _state.value.copy(error = result.message)
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("ComicDetailViewModel", "加载漫画详情失败: " + e.message, e)
                _state.value = _state.value.copy(
                    error = e.message ?: context.getString(R.string.common_load_failed)
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun retry() {
        val id = loadedId ?: return
        load(id, force = true)
    }

    private fun observeProgress(comicId: String) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            comics.observeComicProgress(comicId).collect { progress ->
                _state.value = _state.value.copy(progress = progress)
            }
        }
    }
}
