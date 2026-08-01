package app.amisles.hanime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.core.common.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    private val repository = HanimeRepository.getInstance()

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
        android.util.Log.i("HomeViewModel", "🚀 HomeViewModel初始化，开始加载首页数据")
        AppLogger.d("HomeViewModel", "HomeViewModel created, calling loadHomeData")
        loadHomeData()
    }

    fun loadHomeData() {
        android.util.Log.i("HomeViewModel", "📌 loadHomeData called")
        AppLogger.d("HomeViewModel", "loadHomeData called, setting isLoading=true")
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            android.util.Log.i("HomeViewModel", "🔄 Coroutine started, switching to IO dispatcher")
            AppLogger.d("HomeViewModel", "Coroutine started, switching to IO dispatcher")
            try {
                val result = withContext(Dispatchers.IO) {
                    android.util.Log.i("HomeViewModel", "⏳ 正在调用 repository.getHomeData()")
                    repository.getHomeData()
                }
                android.util.Log.i("HomeViewModel", "✅ Repository returned ${result.sections.size} sections, banner: ${result.banner != null}")
                AppLogger.d("HomeViewModel", "Repository returned ${result.sections.size} sections, banner: ${result.banner != null}")
                _sections.value = result.sections
                _banner.value = result.banner
                _videos.value = result.sections.flatMap { it.videos }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "❌ Error loading home data: ${e.message}", e)
                AppLogger.e("HomeViewModel", "Error loading home data: ${e.message}", e)
                _sections.value = emptyList()
                _videos.value = emptyList()
                _banner.value = null
                _error.value = e.message ?: "加载失败，请检查网络后重试"
            } finally {
                android.util.Log.i("HomeViewModel", "🏁 Setting isLoading=false")
                AppLogger.d("HomeViewModel", "Setting isLoading=false")
                _isLoading.value = false
            }
        }
    }
}