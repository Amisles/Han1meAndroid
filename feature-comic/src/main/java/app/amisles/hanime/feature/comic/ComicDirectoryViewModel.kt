package app.amisles.hanime.feature.comic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.ComicDirectoryEntry
import app.amisles.hanime.domain.model.ComicDirectoryKind
import app.amisles.hanime.domain.model.ComicDirectoryPhase
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

@HiltViewModel
class ComicDirectoryViewModel @Inject constructor(
    private val comics: ComicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _entries = MutableStateFlow<List<ComicDirectoryEntry>>(emptyList())
    val entries: StateFlow<List<ComicDirectoryEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _phase = MutableStateFlow<ComicDirectoryPhase?>(null)
    val phase: StateFlow<ComicDirectoryPhase?> = _phase.asStateFlow()

    private var loadJob: Job? = null
    private var loadedKind: ComicDirectoryKind? = null

    fun load(kind: ComicDirectoryKind, refresh: Boolean = false) {
        if (_isLoading.value || (loadedKind == kind && !refresh)) return
        loadedKind = kind
        loadJob?.cancel()
        _isLoading.value = true
        _error.value = null
        _phase.value = ComicDirectoryPhase.FETCHING
        loadJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    comics.getComicDirectory(kind, refresh) { phase -> _phase.value = phase }
                }
                when (result) {
                    is AppResult.Success -> {
                        _entries.value = result.data
                        _error.value = null
                    }
                    is AppResult.Error -> _error.value = ErrorMapper.message(
                        context,
                        result.exception,
                        context.getString(R.string.common_load_failed)
                    )
                    is AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.logError("ComicDirectoryViewModel", "加载漫画目录失败: " + e.message, e)
                _error.value = ErrorMapper.message(context, e, context.getString(R.string.common_load_failed))
            } finally {
                _isLoading.value = false
                _phase.value = null
            }
        }
    }

    fun retry() {
        val kind = loadedKind ?: return
        load(kind, refresh = true)
    }
}
