package app.amisles.hanime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.HomeDataEvent
import app.amisles.hanime.data.repository.HanimeRepository
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

    /** 首屏加载（当前无任何数据，界面用骨架屏占位）。 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 下拉刷新（已有数据，界面原地更新，**不清空、不切骨架屏**）。
     *
     * 与 [isLoading] 分开是刻意的：此前刷新复用 isLoading 并先清空数据，界面会经历
     * 「内容消失 → 骨架屏 → 内容重建」两次跳变，重建还会让已解码的缩略图缓存全部失效。
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadJob: Job? = null

    // 首页数据在 VM 创建时即加载（不放到 UI 的 LaunchedEffect）：首页是启动页，越早请求首屏越快
    init {
        loadHomeData()
    }

    /**
     * 加载首页数据。
     *
     * 「首屏加载」与「下拉刷新」的分支由**当前是否已有数据**自行判定，不由调用方传入：
     * 下拉手势与错误页「重试」共用同一个回调，靠入参区分会漏判（重试时若走刷新分支，
     * 界面会因 sections 为空且无骨架屏而白屏）。
     * - 已有数据 → 刷新：保留 sections / banner 原地更新，只由下拉指示器表达进度；
     * - 无数据 → 首屏加载：清空后由骨架屏接管。
     */
    fun loadHomeData() {
        val hasContent = _sections.value.isNotEmpty() || _banner.value != null
        if (hasContent) {
            _isRefreshing.value = true
        } else {
            _isLoading.value = true
            _sections.value = emptyList()
            _banner.value = null
        }
        _error.value = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            var firstEventArrived = false
            // 刷新时记录本次流实际返回的分区标题：流正常结束后据此剔除已下线的旧分区，
            val receivedTitles = if (hasContent) mutableSetOf<String>() else null
            var streamFailed = false
            repository.getHomeDataStream()
                .catch { e ->
                    streamFailed = true
                    _error.value = e.message ?: context.getString(R.string.error_load_home_failed)
                }
                .collect { event ->
                    if (!firstEventArrived) {
                        firstEventArrived = true
                        _isLoading.value = false
                        _isRefreshing.value = false
                    }
                    when (event) {
                        is HomeDataEvent.Banner -> _banner.value = event.banner
                        is HomeDataEvent.Section -> {
                            // 分区内按 videoUrl 去重：HomeScreen 以 videoUrl 作为 LazyColumn 的 key，
                            // 站点重复输出同一条视频会触发「key 已被使用」异常导致崩溃
                            val section = event.section.copy(
                                videos = event.section.videos.distinctBy { it.videoUrl }
                            )
                            receivedTitles?.add(section.title)
                            // 按标题去重：流重放或站点重复输出同一分区时不会出现重复区块
                            _sections.value = _sections.value
                                .filterNot { it.title == section.title } + section
                        }
                        is HomeDataEvent.Error -> _error.value = event.message
                    }
                }
            _isLoading.value = false
            _isRefreshing.value = false
            // 出错时收到的分区只是残缺子集，不能据此剔除既有分区
            if (receivedTitles != null && !streamFailed) {
                _sections.value = _sections.value.filter { it.title in receivedTitles }
            }
        }
    }
}
