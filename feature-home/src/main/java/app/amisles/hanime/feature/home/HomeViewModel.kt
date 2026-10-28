package app.amisles.hanime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.HomeDataEvent
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ContentRepository,
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * 「继续观看」条目。
     *
     * 直接暴露仓储的 Flow：播放进度每 5 秒落库一次，用 StateFlow 中转会多一层
     * 重订阅与去重开销，而界面只需要最新的列表。
     */
    val continueWatching: StateFlow<List<WatchHistory>> = libraryRepository
        .getContinueWatchingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 把条目标记为已看完：进度顶满时长后，95% 完成度过滤会让它离开「继续观看」。
     * 历史记录本身保留。
     */
    fun markContinueItemWatched(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.markWatched(videoId)
        }
    }

    /** 从继续观看移除 = 删除该条历史记录。 */
    fun removeContinueItem(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.removeWatchHistory(videoId)
        }
    }

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
            var streamFailed = false
            val accumulated = _sections.value.toMutableList()
            val receivedSections = if (hasContent) mutableListOf<HomeSection>() else null
            var firstPushDone = false
            var flushJob: Job? = null
            val flushScope: CoroutineScope = this

            // 上一次已提交的快照。分区内容未变时不再重发 —— 合并窗口触发时很可能只有部分
            // 分区到达，若每次都无条件 _sections.value = 新 list，下游 buildPlans 会重算、
            // LazyColumn 会对整页做一次 diff，纯属浪费。
            var lastPushed: List<HomeSection> = _sections.value

            fun pushSections() {
                val next = accumulated.toList()
                if (next == lastPushed) return
                lastPushed = next
                _sections.value = next
            }

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
                            receivedSections?.add(section)
                            mergeSection(accumulated, section)
                            if (!firstPushDone) {
                                // 首个分区立即提交，保住首屏速度
                                firstPushDone = true
                                pushSections()
                            } else if (flushJob?.isActive != true) {
                                // 其余分区在合并窗口内攒批提交，避免逐条更新触发整页重建
                                flushJob = flushScope.launch {
                                    delay(FLUSH_WINDOW_MS)
                                    pushSections()
                                }
                            }
                        }
                        is HomeDataEvent.Error -> _error.value = event.message
                    }
                }
            flushJob?.cancel()
            _isLoading.value = false
            _isRefreshing.value = false
            // 出错时收到的分区只是残缺子集，不能据此剔除既有分区
            if (receivedSections != null && !streamFailed) {
                val dropped = _sections.value.filter { old -> receivedSections.none { sameSection(it, old) } }
                accumulated.removeAll(dropped.toSet())
            }
            pushSections()
        }
    }
}

/** 分区提交合并窗口：窗口内到达的多个分区合并为一次 UI 更新，避免逐条提交触发整页重建 */
private const val FLUSH_WINDOW_MS = 120L

internal fun sameSection(a: HomeSection, b: HomeSection): Boolean =
    a.title == b.title && a.moreUrl == b.moreUrl

/** 同一分区（标题与链接都相同）原位替换；标题相同但链接不同的分区追加，互不覆盖。 */
internal fun mergeSection(target: MutableList<HomeSection>, section: HomeSection) {
    val at = target.indexOfFirst { sameSection(it, section) }
    if (at >= 0) target[at] = section else target.add(section)
}
