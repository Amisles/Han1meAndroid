package app.amisles.hanime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HomeSection
import app.amisles.hanime.domain.model.HomeDataEvent
import app.amisles.hanime.domain.result.AppResult
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.domain.model.WatchLaterVideo
import app.amisles.hanime.domain.model.WatchStats
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.cache.HomeSnapshotCache
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.data.remote.CloudflareChallengeException
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
    private val statsRepository: app.amisles.hanime.domain.repository.StatsRepository,
    private val settings: SettingsRepository,
    private val homeSnapshotCache: HomeSnapshotCache,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * 「继续观看」条目。
     *
     * 用 stateIn 把仓储 Flow 收敛成 StateFlow：播放进度每 5 秒落库一次，界面只需要最新的列表；
     * WhileSubscribed(5_000) 让页面短暂退到后台时不立即取消上游收集。
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

    val watchLater: StateFlow<List<WatchLaterVideo>> = libraryRepository
        .getWatchLaterFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _weeklyReport = MutableStateFlow<WatchStats?>(null)
    val weeklyReport: StateFlow<WatchStats?> = _weeklyReport.asStateFlow()

    private val _weeklyReportVisible = MutableStateFlow(false)
    val weeklyReportVisible: StateFlow<Boolean> = _weeklyReportVisible.asStateFlow()

    fun dismissWeeklyReport() {
        _weeklyReportVisible.value = false
        viewModelScope.launch(Dispatchers.IO) {
            Preferences.setWeeklyReportDismissedWeek(weekKeyOf())
        }
    }

    private fun weekKeyOf(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-W${cal.get(java.util.Calendar.WEEK_OF_YEAR)}"
    }

    init {
        checkWeeklyReport()
    }

    private fun checkWeeklyReport() {
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) return
        if (Preferences.weeklyReportDismissedWeek() == weekKeyOf()) return
        viewModelScope.launch(Dispatchers.IO) {
            val stats = runCatching { statsRepository.loadWatchStats() }.getOrNull() ?: return@launch
            if (!stats.hasData) return@launch
            _weeklyReport.value = stats
            _weeklyReportVisible.value = true
        }
    }

    /** 点击稍后观看条目 = 开始观看，从队列移除。 */
    fun consumeWatchLater(video: WatchLaterVideo) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.removeWatchLater(video.id)
        }
    }

    fun removeWatchLater(videoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepository.removeWatchLater(videoId)
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

    /** 非 null 表示本次失败由 Cloudflare 人机校验拦截，错误视图据此提供一键验证入口。 */
    private val _challengeHost = MutableStateFlow<String?>(null)
    val challengeHost: StateFlow<String?> = _challengeHost.asStateFlow()

    private var loadJob: Job? = null

    private var loadSiteUrl: String? = null

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
     * - 无数据但有按站点持久化的首页快照 → 注入快照原地上屏并转入刷新分支；
     * - 无数据且无快照 → 首屏加载：清空后由骨架屏接管。
     */
    fun loadHomeData() {
        loadSiteUrl = settings.baseUrl
        val hasContent = _sections.value.isNotEmpty() || _banner.value != null
        if (hasContent) {
            _isRefreshing.value = true
        } else {
            _isLoading.value = true
            _isRefreshing.value = false
            _sections.value = emptyList()
            _banner.value = null
        }
        _error.value = null
        _challengeHost.value = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            var firstEventArrived = false
            var streamFailed = false
            var hasRenderableContent = hasContent
            val siteKey = loadSiteUrl
            if (!hasRenderableContent) {
                val snapshot = siteKey?.let { homeSnapshotCache.read(it) }
                if (snapshot != null && snapshot.sections.isNotEmpty()) {
                    _sections.value = snapshot.sections
                    _banner.value = snapshot.banner
                    _isLoading.value = false
                    _isRefreshing.value = true
                    hasRenderableContent = true
                }
            }
            val accumulated = _sections.value.toMutableList()
            val receivedSections = if (hasRenderableContent) mutableListOf<HomeSection>() else null
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
                    if (e is CloudflareChallengeException) _challengeHost.value = e.host
                    _error.value = ErrorMapper.message(
                        context,
                        e,
                        context.getString(R.string.error_load_home_failed)
                    )
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
                            // 分区内按 videoUrl 去重：HomeScreen 的 LazyColumn key 是「分区标题-下标#videoUrl」，
                            // 同一分区内重复输出同一条视频仍会撞 key，触发「key 已被使用」异常导致崩溃
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
                        is HomeDataEvent.Error -> {
                            val cause = event.exception
                            if (cause is CloudflareChallengeException) {
                                _challengeHost.value = cause.host
                            }
                            _error.value = ErrorMapper.message(
                                context,
                                cause,
                                event.message
                            )
                        }
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
            if (!streamFailed && _sections.value.isNotEmpty()) {
                siteKey?.let { homeSnapshotCache.write(it, _banner.value, _sections.value) }
            }
        }
    }

    /**
     * 换站后重拉：在途请求仍属于旧站点，按刷新分支重拉还会保留旧站点内容，故先丢弃 sections / banner。
     */
    fun refreshIfSiteChanged(siteUrl: String) {
        if (siteUrl == loadSiteUrl) return
        _sections.value = emptyList()
        _banner.value = null
        loadHomeData()
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
