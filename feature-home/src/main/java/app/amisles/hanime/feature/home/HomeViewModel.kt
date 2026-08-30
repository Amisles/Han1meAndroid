package app.amisles.hanime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.HomeDataEvent
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HanimeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _sections = MutableStateFlow<List<HomeSection>>(emptyList())
    val sections: StateFlow<List<HomeSection>> = _sections.asStateFlow()

    private val _banner = MutableStateFlow<HanimeBanner?>(null)
    val banner: StateFlow<HanimeBanner?> = _banner.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadJob: Job? = null

    init {
        AppLogger.d("HomeViewModel", "HomeViewModel created, calling loadHomeData")
        loadHomeData()
    }

    fun loadHomeData() {
        _isLoading.value = true
        _error.value = null
        _sections.value = emptyList()
        _banner.value = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            var firstEventArrived = false
            repository.getHomeDataStream()
                .catch { e ->
                    if (!firstEventArrived) _isLoading.value = false
                    _error.value = e.message ?: context.getString(R.string.error_load_home_failed)
                }
                .collect { event ->
                    if (!firstEventArrived) {
                        firstEventArrived = true
                        _isLoading.value = false
                    }
                    when (event) {
                        is HomeDataEvent.Banner -> _banner.value = event.banner
                        is HomeDataEvent.Section -> {
                            _sections.value = _sections.value + event.section
                        }
                        is HomeDataEvent.Error -> _error.value = event.message
                    }
                }
        }
    }
}
