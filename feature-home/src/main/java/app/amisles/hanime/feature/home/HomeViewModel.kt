package app.amisles.hanime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HanimeRepository
) : ViewModel() {

    private val _videos = MutableStateFlow<List<HanimeVideo>>(emptyList())
    val videos: StateFlow<List<HanimeVideo>> = _videos.asStateFlow()

    private val _sections = MutableStateFlow<List<HomeSection>>(emptyList())
    val sections: StateFlow<List<HomeSection>> = _sections.asStateFlow()

    private val _banner = MutableStateFlow<HanimeBanner?>(null)
    val banner: StateFlow<HanimeBanner?> = _banner.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        AppLogger.d("HomeViewModel", "HomeViewModel created, calling loadHomeData")
        loadHomeData()
    }

    fun loadHomeData() {
        AppLogger.d("HomeViewModel", "loadHomeData called, setting isLoading=true")
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            AppLogger.d("HomeViewModel", "Coroutine started, switching to IO dispatcher")
            val result = withContext(Dispatchers.IO) {
                repository.getHomeData()
            }
            when (result) {
                is AppResult.Success -> {
                    AppLogger.d("HomeViewModel", "Repository returned ${result.data.sections.size} sections, banner: ${result.data.banner != null}")
                    _sections.value = result.data.sections
                    _banner.value = result.data.banner
                    _videos.value = result.data.sections.flatMap { it.videos }
                }
                is AppResult.Error -> {
                    AppLogger.e("HomeViewModel", "Error loading home data: ${result.message}", result.exception)
                    _sections.value = emptyList()
                    _videos.value = emptyList()
                    _banner.value = null
                    _error.value = result.message
                }
                is AppResult.Loading -> {
                    // 首页加载中状态由 _isLoading 控制
                }
            }
            _isLoading.value = false
        }
    }
}
