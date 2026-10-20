package app.amisles.hanime.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.extractVideoId
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoDetailEvent
import app.amisles.hanime.domain.model.WatchHistory
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.extension.redactUrlForLog
import app.amisles.hanime.domain.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    // 刻意注入三个窄接口而非一个"全能仓储"：本 ViewModel 同时用到内容、本地库与账号能力，
    // 通过参数类型即可看出它依赖哪些领域，也便于测试时只 fake 相关部分。
    private val content: ContentRepository,
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val session: SessionRepository,
    private val downloads: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail.asStateFlow()

    // 进入播放器时用于续播的已保存进度（毫秒）
    private val _savedPosition = MutableStateFlow(0L)
    val savedPosition: StateFlow<Long> = _savedPosition.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _downloadQualities = MutableStateFlow<List<DownloadQuality>>(emptyList())
    val downloadQualities: StateFlow<List<DownloadQuality>> = _downloadQualities.asStateFlow()

    private val _isLoadingQualities = MutableStateFlow(false)
    val isLoadingQualities: StateFlow<Boolean> = _isLoadingQualities.asStateFlow()

    private val _downloadStartResults = Channel<Int>(Channel.BUFFERED)
    val downloadStartResults: Flow<Int> = _downloadStartResults.receiveAsFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // 订阅作者相关状态
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _isSubscribing = MutableStateFlow(false)
    val isSubscribing: StateFlow<Boolean> = _isSubscribing.asStateFlow()

    // 订阅错误（未登录 / CSRF 缺失 / 作者 ID 缺失 / 网络失败等），以枚举区分便于 UI 本地化
    private val _subscribeError = MutableStateFlow<SubscribeError?>(null)
    val subscribeError: StateFlow<SubscribeError?> = _subscribeError.asStateFlow()

    private var currentVideoId: String = ""
    private var currentVideoUrl: String = ""

    // 供 UI 层获取当前视频 ID
    val videoId: String get() = currentVideoId

    // 进行中的详情加载任务；快速重进页面时取消旧请求，避免重复/竞态
    private var detailLoadJob: Job? = null

    /**
     * 视频已切换的通知，值为新 videoId。
     *
     * UI 层订阅它来调用 CommentViewModel.selectVideo()：视频域与评论域是两个独立 VM，
     * 之间的同步走这个单向事件，而不是让 DetailViewModel 持有 CommentViewModel
     * （Hilt 不允许 @HiltViewModel 互为依赖，且会形成循环依赖）。
     */
    private val _videoSwitched = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val videoSwitched: SharedFlow<String> = _videoSwitched.asSharedFlow()

    fun loadVideoDetail(videoUrl: String) {
        AppLogger.d("DetailViewModel", "loadVideoDetail called, url: ${videoUrl.redactUrlForLog()}")
        _isLoading.value = true
        _error.value = null

        currentVideoId = extractVideoId(videoUrl)
        currentVideoUrl = videoUrl
        _videoSwitched.tryEmit(currentVideoId)

        // 切换视频时重置评论域（评论/回复/点赞/展开态）与订阅态，使新视频可被重新加载。
        // 评论域的 16 项复位由 CommentViewModel.selectVideo 收拢，本类不再逐个赋值 ——
        // 原先要写 20+ 行，漏一个就会出现「新视频显示旧评论」。
        // 评论域复位由 UI 层订阅 [videoSwitched] 后调用 CommentViewModel.selectVideo() 完成；
        // 视频域 VM 不持有评论域 VM（Hilt 不允许 @HiltViewModel 互为依赖）。

        // 清空下载画质，避免弹窗短暂展示上一部视频的画质列表
        _downloadQualities.value = emptyList()
        _isLoadingQualities.value = false

        // 取消上一次未完成的详情请求（快速重进页面时去重）
        detailLoadJob?.cancel()
        detailLoadJob = viewModelScope.launch {
            var mainArrived = false
            content.getVideoDetailStream(videoUrl)
                .catch { e ->
                    // 仅当主信息尚未到达时才关闭骨架屏，避免尾部异常把已渲染内容误判为失败
                    if (!mainArrived) _isLoading.value = false
                    _error.value = e.message ?: context.getString(R.string.error_load_detail_failed)
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
                            // 主信息到齐即可关闭骨架屏，播放器区先渲染；相关视频/播放列表随后渐进补全
                            _isLoading.value = false
                            mainArrived = true

                            // 登录用户 ID：以订阅表单的 subscribe-user-id 优先，缺失时回退 currentUserId
                            val resolvedUserId = detail.subscribeUserId.ifBlank { detail.currentUserId }
                            if (resolvedUserId.isNotBlank()) {
                                session.saveUserId(resolvedUserId)
                            }
                            if (currentVideoId.isNotEmpty()) {
                                // 先读取已有历史，保留播放进度，避免每次进入覆盖为 0
                                val existing = withContext(Dispatchers.IO) {
                                    library.getWatchHistory(currentVideoId)
                                }
                                val history = WatchHistory(
                                    id = currentVideoId,
                                    title = detail.title,
                                    thumbnailUrl = detail.posterUrl,
                                    videoUrl = currentVideoUrl,
                                    author = detail.author,
                                    duration = "",
                                    watchedAt = System.currentTimeMillis(),
                                    playbackPosition = existing?.playbackPosition ?: 0L,
                                    playbackDuration = existing?.playbackDuration ?: 0L
                                )
                                withContext(Dispatchers.IO) {
                                    library.addWatchHistory(history)
                                }
                                _savedPosition.value = existing?.playbackPosition ?: 0L
                            }

                            val isFav = withContext(Dispatchers.IO) {
                                library.isFavorite(currentVideoId)
                            }
                            _isFavorite.value = isFav

                            // 已解析出订阅状态（"1" 表示已订阅），还原高亮
                            _isSubscribed.value = detail.subscribeStatus == "1"
                        }
                        is VideoDetailEvent.RelatedVideos -> {
                            _videoDetail.value = _videoDetail.value?.copy(relatedVideos = event.videos)
                        }
                        is VideoDetailEvent.Playlist -> {
                            _videoDetail.value = _videoDetail.value?.copy(playlist = event.playlist)
                        }
                        is VideoDetailEvent.Error -> {
                            AppLogger.e("DetailViewModel", "Error loading video detail stream: ${event.message}")
                            _error.value = event.message
                            _isLoading.value = false
                        }
                    }
                }
        }
    }

    /** 判断异步响应是否仍属于当前视频：详情页被「快速重进另一条视频」复用，需在结果落地前校验，避免旧数据写进新视频状态。 */
    private fun isSameVideoAsRequest(requestVideoId: String): Boolean =
        requestVideoId == currentVideoId

    fun loadDownloadQualities() {
        if (currentVideoId.isEmpty()) return
        val requestVideoId = currentVideoId
        AppLogger.d("DetailViewModel", "loadDownloadQualities called, videoId: $requestVideoId")
        _isLoadingQualities.value = true

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    content.getDownloadQualities(requestVideoId)
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    // 已切到其他视频：丢弃结果，相关状态已由 loadVideoDetail 重置
                    AppLogger.d("DetailViewModel", "Discard stale qualities response: $requestVideoId")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        AppLogger.d("DetailViewModel", "Got ${result.data.size} download qualities")
                        _downloadQualities.value = result.data
                    }
                    is AppResult.Error -> {
                        AppLogger.e("DetailViewModel", "Error loading download qualities: ${result.message}", result.exception)
                        _downloadQualities.value = emptyList()
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 异常若逃逸出 viewModelScope 会直接崩溃，且会跳过加载态复位
                AppLogger.e("DetailViewModel", "Error loading download qualities: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) _downloadQualities.value = emptyList()
            } finally {
                // 已切到其他视频时相关状态由 loadVideoDetail 统一复位，此处不得触碰
                if (isSameVideoAsRequest(requestVideoId)) _isLoadingQualities.value = false
            }
        }
    }

    /**
     * 发起下载。
     *
     * **不再同步返回结果码**：`DownloadManager.startDownload` 内含目录解析、`canonicalPath`
     * 越界校验与残留文件清理等磁盘 I/O，原实现在 Compose 点击回调（主线程）中同步执行会阻塞主线程；
     * 现改到 IO 线程发起，结果码经 [downloadStartResults] 异步回传，由界面弹提示。
     * quality 统一传 `quality.quality`，与批次页保持一致，才能让「文件名 + 去重键」对上。
     */
    fun startDownload(quality: DownloadQuality) {
        val detail = _videoDetail.value
        val title = detail?.title ?: "video"
        val thumbnailUrl = detail?.posterUrl ?: ""
        // 切到 IO 线程前快照视频 id：协程可能晚于「用户已切到另一个视频」才执行，
        // 而任务必须归属于发起时那部视频（原同步实现读到的也是发起时的值）。
        val videoId = currentVideoId
        viewModelScope.launch(Dispatchers.IO) {
            val result = downloads.startDownload(
                title,
                quality.quality,
                quality.downloadUrl,
                thumbnailUrl,
                videoId
            )
            AppLogger.d("DetailViewModel", "Start download result=$result, quality=${quality.quality}")
            _downloadStartResults.trySend(result)
        }
    }

    fun toggleFavorite() {
        if (currentVideoId.isEmpty()) return
        AppLogger.d("DetailViewModel", "toggleFavorite called, currentVideoId: $currentVideoId")

        viewModelScope.launch {
            try {
                val currentState = _isFavorite.value
                val detail = _videoDetail.value

                if (currentState) {
                    withContext(Dispatchers.IO) {
                        library.removeFavorite(currentVideoId)
                    }
                    AppLogger.d("DetailViewModel", "Removed from favorites")
                } else {
                    if (detail != null) {
                        val favoriteVideo = FavoriteVideo(
                            id = currentVideoId,
                            title = detail.title,
                            thumbnailUrl = detail.posterUrl,
                            videoUrl = currentVideoUrl,
                            author = detail.author,
                            duration = "",
                            likeRate = "",
                            viewCount = "",
                            createdAt = System.currentTimeMillis()
                        )
                        withContext(Dispatchers.IO) {
                            library.addFavorite(favoriteVideo)
                        }
                        AppLogger.d("DetailViewModel", "Added to favorites")
                    }
                }
                val verified = withContext(Dispatchers.IO) { library.isFavorite(currentVideoId) }
                _isFavorite.value = verified
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DetailViewModel", "Error toggling favorite: ${e.message}", e)
            }
        }
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updated = library.updateWatchProgress(id, position, duration)
                if (updated) return@launch
                // 尚无历史记录（例如首次播放即调用）：插入完整记录
                library.addWatchHistory(
                    WatchHistory(
                        id = id,
                        title = detail.title,
                        thumbnailUrl = detail.posterUrl,
                        videoUrl = currentVideoUrl,
                        author = detail.author,
                        duration = "",
                        watchedAt = System.currentTimeMillis(),
                        playbackPosition = position,
                        playbackDuration = duration
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 进度落盘失败不应影响播放：记录后吞掉，避免异常逃逸出 viewModelScope 导致崩溃
                AppLogger.e("DetailViewModel", "Error saving playback progress: ${e.message}", e)
            }
        }
    }

    /** 读取某视频的已保存播放进度（毫秒），用于进入时续播。 */
    suspend fun getSavedPlaybackPosition(videoId: String): Long =
        withContext(Dispatchers.IO) { library.getWatchHistory(videoId)?.playbackPosition ?: 0L }

    /**
     * 订阅 / 取消订阅作者。调用官网 /subscribe 接口，凭 subscribe-status 区分。
     * 采用乐观更新：先本地切换，接口成功用服务端返回的新状态 / CSRF Token 修正，失败回滚。
     */
    fun toggleSubscribe() {
        val detail = _videoDetail.value
        if (detail == null || detail.subscribeArtistId.isBlank()) {
            _subscribeError.value = SubscribeError.ARTIST_ID_MISSING
            return
        }
        if (detail.csrfToken.isBlank()) {
            _subscribeError.value = SubscribeError.CSRF_MISSING
            return
        }
        if (detail.subscribeUserId.isBlank()) {
            _subscribeError.value = SubscribeError.NOT_LOGGED_IN
            return
        }
        // 防止请求未完成前重复点击
        if (_isSubscribing.value) return

        val willSubscribe = !_isSubscribed.value
        _isSubscribed.value = willSubscribe
        _isSubscribing.value = true
        // 绑定发起时的视频：结果落地与加载态复位都要校验，避免旧视频的订阅响应写进新视频的状态
        val requestVideoId = currentVideoId

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    account.toggleSubscribe(
                        userId = detail.subscribeUserId,
                        artistId = detail.subscribeArtistId,
                        willSubscribe = willSubscribe,
                        csrfToken = detail.csrfToken
                    )
                }
                if (!isSameVideoAsRequest(requestVideoId)) {
                    // 已切到其他视频：订阅状态已由 loadVideoDetail 整体重置，此处不得回写
                    AppLogger.d("DetailViewModel", "Discard stale subscribe result: ${detail.subscribeArtistId}")
                    return@launch
                }
                when (result) {
                    is AppResult.Success -> {
                        val res = result.data
                        _isSubscribed.value = res.subscribeStatus == "1"
                        // 用接口回传的新令牌刷新 CSRF，保证后续请求有效
                        if (res.csrfToken.isNotBlank()) {
                            _videoDetail.value = _videoDetail.value?.copy(csrfToken = res.csrfToken)
                        }
                        AppLogger.d("DetailViewModel", "Subscribe toggled for artist ${detail.subscribeArtistId}, willSubscribe=$willSubscribe")
                    }
                    is AppResult.Error -> {
                        _isSubscribed.value = !willSubscribe
                        _subscribeError.value = SubscribeError.FAILED
                        AppLogger.e("DetailViewModel", "Error toggling subscribe: ${result.message}", result.exception)
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("DetailViewModel", "Error toggling subscribe: ${e.message}", e)
                if (isSameVideoAsRequest(requestVideoId)) {
                    _isSubscribed.value = !willSubscribe
                    _subscribeError.value = SubscribeError.FAILED
                }
            } finally {
                // 切到其他视频时该标记已由 loadVideoDetail 复位，不得覆盖新一轮订阅的进行中状态
                if (isSameVideoAsRequest(requestVideoId)) _isSubscribing.value = false
            }
        }
    }

    /** 清除订阅操作的错误状态。 */
    /**
     * 评论数变化回写：评论发表成功后由 CommentViewModel 回调，
     * 保持 VideoDetail.commentCount 与列表头部的新评论一致。
     *
     * 评论域与视频域是两个独立 VM，此回调是二者唯一的反向通道（UI 层传入）。
     */
    fun onCommentCountUpdated(newCount: Int) {
        _videoDetail.value = _videoDetail.value?.copy(commentCount = newCount)
    }

    fun clearSubscribeError() {
        _subscribeError.value = null
    }
}

/** 订阅作者操作的错误类型，用于 UI 侧按类型选择本地化文案。 */
enum class SubscribeError {
    NOT_LOGGED_IN,   // 未登录
    CSRF_MISSING,    // CSRF Token 缺失
    ARTIST_ID_MISSING, // 作者 ID 缺失
    FAILED           // 其他失败（网络/解析等）
}
