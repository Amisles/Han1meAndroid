package app.amisles.hanime.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import app.amisles.hanime.domain.model.extractVideoId
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.repository.PlayerSettings
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.TranslationRepository
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.StartDownloadResult
import app.amisles.hanime.domain.model.TranslationModelState
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoDetailEvent
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.extension.redactUrlForLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    // 刻意注入五个窄接口而非一个"全能仓储"：本 ViewModel 同时用到内容、本地库、账号、会话与下载能力，
    // 通过参数类型即可看出它依赖哪些领域，也便于测试时只 fake 相关部分。
    private val content: ContentRepository,
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val downloads: DownloadRepository,
    private val translation: TranslationRepository,
    private val playerSettings: PlayerSettings,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 递增即要求播放器换用新直链：换字幕语种后直链变了，进度必须留在原处。 */
    private val _sourceReloadToken = MutableStateFlow(0)
    val sourceReloadToken: StateFlow<Int> = _sourceReloadToken.asStateFlow()

    private val _subtitleSwitching = MutableStateFlow(false)
    val subtitleSwitching: StateFlow<Boolean> = _subtitleSwitching.asStateFlow()

    /**
     * 一次性提示（字符串资源 id）。
     *
     * 目前只有本地库写失败会走这里：写失败时星标会被随后的复核复位，若不给提示，用户看到的是
     * 「点了没反应、星标自己弹回」，无从判断是网络问题还是存储问题。
     */
    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private val downloadActions = DetailDownloadActions(content, downloads, viewModelScope)
    private val libraryActions = DetailLibraryActions(library, viewModelScope) {
        _messages.trySend(R.string.local_save_failed)
    }
    private val subscriptionActions = DetailSubscriptionActions(account, viewModelScope)

    val savedPosition: StateFlow<Long> = libraryActions.savedPosition
    val downloadQualities: StateFlow<List<DownloadQuality>> = downloadActions.qualities
    val isLoadingQualities: StateFlow<Boolean> = downloadActions.isLoading
    val downloadStartResults: Flow<StartDownloadResult> = downloadActions.results
    val isFavorite: StateFlow<Boolean> = libraryActions.isFavorite
    val isWatchLater: StateFlow<Boolean> = libraryActions.isWatchLater
    val isSubscribed: StateFlow<Boolean> = subscriptionActions.isSubscribed
    val isSubscribing: StateFlow<Boolean> = subscriptionActions.isSubscribing
    val subscribeError: StateFlow<SubscribeError?> = subscriptionActions.error

    val realtimeTranslationAvailable: StateFlow<Boolean> = translation.modelStateFlow
        .map { it is TranslationModelState.Ready }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = translation.modelStateFlow.value is TranslationModelState.Ready
        )

    private var currentVideoId: String = ""
    private var currentVideoUrl: String = ""

    // 供 UI 层获取当前视频 ID
    val videoId: String get() = currentVideoId

    // 进行中的详情加载任务；快速重进页面时取消旧请求，避免重复/竞态
    private var detailLoadJob: Job? = null

    /**
     * 当前视频 ID 的可观察镜像，供评论域同步。
     *
     * UI 层订阅它来调用 CommentViewModel.selectVideo()：视频域与评论域是两个独立 VM，
     * 之间的同步走这条单向通道，而不是让 DetailViewModel 持有 CommentViewModel
     * （Hilt 不允许 @HiltViewModel 互为依赖，且会形成循环依赖）。
     *
     * **必须是 StateFlow 而不是 SharedFlow**：原先用
     * `MutableSharedFlow(extraBufferCapacity = 1)` 发送，但 replay = 0 —— 缓冲只对「已订阅」
     * 的收集者生效。而 UI 侧的 LaunchedEffect 是首次启动收集，等它挂起到 ACTIVE 时
     * loadVideoDetail() 早已同步发完事件，首次进入详情页的事件必然丢失：
     * 评论域 currentVideoId 保持空串，loadComments() 首行直接 return，评论永远加载不出来。
     * StateFlow 自带当前值，晚订阅也能立刻拿到，从根上消除这个时序依赖。
     */
    private val videoSwitch = VideoSwitchChannel()
    val videoSwitched: StateFlow<String> = videoSwitch.videoId

    fun loadVideoDetail(videoUrl: String) {
        AppLogger.d("DetailViewModel", "loadVideoDetail called, url: ${videoUrl.redactUrlForLog()}")
        _error.value = null

        currentVideoId = extractVideoId(videoUrl)
        currentVideoUrl = videoUrl
        videoSwitch.switchTo(currentVideoId)

        // 切换视频时清空本 VM 的画质、加载态与稍后观看态，使新视频可被重新加载。
        // 订阅态不在这里复位，由 MainInfo 事件按新视频的 subscribeStatus 重新推导。
        // 评论域的 16 项复位由 CommentViewModel.selectVideo 收拢，本类不再逐个赋值 ——
        // 原先要写 20+ 行，漏一个就会出现「新视频显示旧评论」。
        // 评论域复位由 UI 层订阅 [videoSwitched] 后调用 CommentViewModel.selectVideo() 完成；
        // 视频域 VM 不持有评论域 VM（Hilt 不允许 @HiltViewModel 互为依赖）。

        // 清空下载画质，避免弹窗短暂展示上一部视频的画质列表
        downloadActions.reset()
        libraryActions.reset()
        subscriptionActions.reset(null)

        // 取消上一次未完成的详情请求（快速重进页面时去重）
        detailLoadJob?.cancel()
        detailLoadJob = viewModelScope.launch {
            content.getVideoDetailStream(videoUrl)
                .catch { e ->
                    _error.value = ErrorMapper.message(
                        context,
                        e,
                        context.getString(R.string.error_load_detail_failed)
                    )
                }
                .collect { event ->
                    when (event) {
                        is VideoDetailEvent.MainInfo -> {
                            val detail = event.detail
                            AppLogger.d(
                                "DetailViewModel",
                                "Got video detail main info: ${detail.title}, sources: ${detail.videoSources.size}"
                            )
                            _videoDetail.value = detail

                            // 登录用户 ID：以订阅表单的 subscribe-user-id 优先，缺失时回退 currentUserId
                            val resolvedUserId = detail.subscribeUserId.ifBlank { detail.currentUserId }
                            if (resolvedUserId.isNotBlank()) {
                                session.saveUserId(resolvedUserId)
                            }
                            if (currentVideoId.isNotEmpty()) {
                                libraryActions.loadForVideo(currentVideoId, currentVideoUrl, detail)
                            }
                            subscriptionActions.reset(detail)
                        }
                        is VideoDetailEvent.RelatedVideos -> {
                            _videoDetail.value = _videoDetail.value?.copy(relatedVideos = event.videos)
                        }
                        is VideoDetailEvent.Playlist -> {
                            _videoDetail.value = _videoDetail.value?.copy(playlist = event.playlist)
                        }
                        is VideoDetailEvent.Error -> {
                            AppLogger.e("DetailViewModel", "Error loading video detail stream: ${event.message}")
                            _error.value = ErrorMapper.message(
                                context,
                                event.exception,
                                context.getString(R.string.error_load_detail_failed)
                            )
                        }
                    }
                }
        }
    }

    /** 判断异步响应是否仍属于当前视频：详情页被「快速重进另一条视频」复用，需在结果落地前校验，避免旧数据写进新视频状态。 */
    private fun isSameVideoAsRequest(requestVideoId: String): Boolean =
        requestVideoId == currentVideoId

    fun loadDownloadQualities() {
        val requestVideoId = currentVideoId
        val subtitleLang = _videoDetail.value?.subtitleLang.orEmpty()
        downloadActions.load(requestVideoId, subtitleLang) { isSameVideoAsRequest(requestVideoId) }
    }

    /**
     * 切换字幕语种：只换解析出来的视频直链，详情页的标题、简介、标签仍是默认语种渲染的那份。
     *
     * 站点把简繁字幕做成两份文件，换语种必须回站点重抓一次；抓不到就回滚偏好，
     * 否则下次进来会按一个从未拿到直链的语种再抓一次。
     */
    fun switchSubtitleLang(lang: String) {
        val detail = _videoDetail.value ?: return
        if (detail.subtitleLang.isEmpty() || lang == detail.subtitleLang || _subtitleSwitching.value) return
        val requestVideoId = currentVideoId
        val previous = playerSettings.subtitleLang
        playerSettings.setSubtitleLang(lang)
        _subtitleSwitching.value = true
        viewModelScope.launch {
            try {
                val fresh = content.getVideoDetail(currentVideoUrl).getOrNull()
                val current = _videoDetail.value
                if (!isSameVideoAsRequest(requestVideoId)) return@launch
                if (fresh == null || current == null || fresh.subtitleLang != lang) {
                    playerSettings.setSubtitleLang(previous)
                    _messages.trySend(R.string.error_load_detail_failed)
                    return@launch
                }
                _videoDetail.value = current.copy(
                    videoSources = fresh.videoSources,
                    defaultSourceUrl = fresh.defaultSourceUrl,
                    subtitleLang = fresh.subtitleLang
                )
                _sourceReloadToken.value++
            } finally {
                _subtitleSwitching.value = false
            }
        }
    }

    fun startDownload(quality: DownloadQuality) {
        downloadActions.start(_videoDetail.value, currentVideoId, quality)
    }

    fun toggleWatchLater() {
        libraryActions.toggleWatchLater(currentVideoId, currentVideoUrl, _videoDetail.value)
    }

    fun toggleFavorite() {
        libraryActions.toggleFavorite(currentVideoId, currentVideoUrl, _videoDetail.value)
    }

    /**
     * 持久化当前播放进度（毫秒）。
     *
     * 播放中每 5 秒调用一次：若该视频已有历史记录，只 UPDATE 三个进度列；尚未建立历史时才
     * 插入完整记录。原实现每次都先 SELECT 再整行 REPLACE，两次事务全表重写，写放大且会与其他
     * 字段的并发更新互相覆盖。
     */
    fun savePlaybackProgress(position: Long, duration: Long) {
        val id = currentVideoId
        if (id.isEmpty()) return
        val detail = _videoDetail.value ?: return
        libraryActions.saveProgress(id, currentVideoUrl, detail, position, duration, uncancellable = false)
    }

    /**
     * 离场时的最终进度保存，由播放器组合销毁（onDispose）调用。
     *
     * 与 [savePlaybackProgress] 的区别：此时页面组合正在销毁，且连播切集会用 popUpTo
     * 立即弹出旧导航条目，viewModelScope 可能在转场结束前就被取消 —— 落库段必须挂
     * NonCancellable 才能保证写完。不能把保存发在组合自身的作用域里：那会随组合
     * 一起被取消，任何等帧/挂起都到不了落库那一步。
     */
    fun saveFinalPlaybackProgress(position: Long, duration: Long) {
        val id = currentVideoId
        if (id.isEmpty()) return
        val detail = _videoDetail.value ?: return
        libraryActions.saveProgress(id, currentVideoUrl, detail, position, duration, uncancellable = true)
    }

    /** 读取某视频的已保存播放进度（毫秒），用于进入时续播。 */
    suspend fun getSavedPlaybackPosition(videoId: String): Long =
        libraryActions.savedPosition(videoId)
    private var refreshingSources = false

    fun refreshVideoSources() {
        if (currentVideoUrl.isBlank() || refreshingSources) return
        refreshingSources = true
        viewModelScope.launch {
            try {
                val fresh = content.getVideoDetail(currentVideoUrl).getOrNull() ?: return@launch
                val current = _videoDetail.value ?: return@launch
                _videoDetail.value = current.copy(
                    videoSources = fresh.videoSources,
                    defaultSourceUrl = fresh.defaultSourceUrl,
                    subtitleLang = fresh.subtitleLang
                )
            } finally {
                refreshingSources = false
            }
        }
    }

    /**
     * 订阅 / 取消订阅作者。调用官网 /subscribe 接口，凭 subscribe-status 区分。
     * 采用乐观更新：先本地切换，接口成功用服务端返回的新状态 / CSRF Token 修正，失败回滚。
     */
    fun toggleSubscribe() {
        val requestVideoId = currentVideoId
        subscriptionActions.toggle(
            detail = _videoDetail.value,
            requestVideoId = requestVideoId,
            isCurrent = { isSameVideoAsRequest(requestVideoId) },
            onCsrfToken = { token ->
                _videoDetail.value = _videoDetail.value?.copy(csrfToken = token)
            }
        )
    }

    /**
     * 评论数变化回写：评论发表成功后由 CommentViewModel 回调，
     * 保持 VideoDetail.commentCount 与列表头部的新评论一致。
     *
     * 评论域与视频域是两个独立 VM，此回调是二者唯一的反向通道（UI 层传入）。
     */
    fun onCommentCountUpdated(newCount: Int) {
        _videoDetail.value = _videoDetail.value?.copy(commentCount = newCount)
    }

    /** 清除订阅操作的错误状态。 */
    fun clearSubscribeError() {
        subscriptionActions.clearError()
    }
}

/** 订阅作者操作的错误类型，用于 UI 侧按类型选择本地化文案。 */
enum class SubscribeError {
    NOT_LOGGED_IN,   // 未登录
    CSRF_MISSING,    // CSRF Token 缺失
    ARTIST_ID_MISSING, // 作者 ID 缺失
    FAILED           // 其他失败（网络/解析等）
}
