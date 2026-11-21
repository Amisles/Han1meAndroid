package app.amisles.hanime.feature.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.model.BatchVideoItem
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class BatchDownloadState(
    val authorIdInput: String = "",
    val isSearching: Boolean = false,
    val isLoadMore: Boolean = false,
    val error: String? = null,
    val authorName: String = "",
    val authorId: String = "",
    val videos: List<BatchVideoItem> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val selectedCount: Int = 0,
    val isDownloading: Boolean = false,
    val downloadingVideoIds: Set<String> = emptySet()
)

@HiltViewModel
class BatchDownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val content: ContentRepository,
    private val downloads: DownloadRepository,
    private val qualityLoader: BatchQualityLoader,
    private val enqueuer: BatchDownloadEnqueuer,
) : ViewModel() {
    private val _state = MutableStateFlow(BatchDownloadState())
    val state: StateFlow<BatchDownloadState> = _state.asStateFlow()

    /**
     * 一次性提示事件（Snackbar）。
     *
     * 用 Channel 而非 State 承载：State 基于值去重，连续两次内容相同的提示（如两次「已添加 N 个
     * 下载任务」且 N 相同）会因值未变化而不再触发收集，第二次提示被静默吞掉；且配置变更后重新
     * 收集非空 State 会重复弹出同一提示。Channel + receiveAsFlow 保证每条事件恰好消费一次。
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    // 搜索世代：每次 searchAuthor 递增，进行中的 loadMore 据此识别自己是否已被新一轮搜索取代
    private var searchGeneration = 0
    private var searchJob: Job? = null

    @Volatile
    private var batchInitInProgress = false

    init {
        // 观察下载任务变化，自动更新视频列表中的下载状态。
        // 进度上报周期为 500ms，tasks 在下载时会持续高频发射；而本页只关心每个视频的
        // 「已完成 / 进行中」判定，与字节数无关。故先投影成 (videoId, status) 再
        // distinctUntilChanged：纯进度变化不会产生新发射，避免每 500ms 对整页视频重扫一遍。
        viewModelScope.launch {
            try {
                downloads.tasks
                    .map { tasks -> tasks.map { it.videoId to it.status } }
                    .distinctUntilChanged()
                    .collect { statusPairs ->
                        syncDownloadStatuses(statusPairs)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 状态同步异常不应让批量下载页崩溃
                AppLogger.e("BatchDownloadViewModel", "同步下载状态失败: ${e.message}", e)
            }
        }
    }

    private fun syncDownloadStatuses(statusPairs: List<Pair<String, DownloadStatus>>) {
        if (batchInitInProgress) return
        _state.update { it.syncDownloadStatuses(statusPairs) }
    }

    fun updateAuthorIdInput(input: String) {
        _state.update { it.copy(authorIdInput = input, error = null) }
    }

    fun searchAuthor() {
        val authorId = _state.value.authorIdInput.trim()
        if (authorId.isEmpty()) {
            _state.update { it.copy(error = context.getString(R.string.batch_enter_author_id)) }
            return
        }

        // 新一轮搜索：递增世代，让进行中的 loadMore 结果失效；同时清掉它的加载态
        val generation = ++searchGeneration
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _state.update { current ->
                if (searchGeneration != generation) current else current.copy(
                    isSearching = true,
                    isLoadMore = false,
                    error = null,
                    videos = emptyList(),
                    authorName = "",
                    authorId = "",
                    selectedCount = 0,
                    downloadingVideoIds = emptySet(),
                    isDownloading = false
                )
            }

            try {
                val result = withContext(Dispatchers.IO) {
                    content.getUserVideoList(authorId, page = 1)
                }

                if (searchGeneration != generation) return@launch
                if (result == null) {
                    _state.update { it.copy(
                        isSearching = false,
                        error = context.getString(R.string.batch_search_failed)
                    )}
                    return@launch
                }

                if (result.videos.isEmpty()) {
                    _state.update { it.copy(
                        isSearching = false,
                        error = context.getString(R.string.batch_no_author_videos)
                    )}
                    return@launch
                }

                val tasksByVideoId = downloads.tasks.value.indexTasksByVideoId()
                val batchVideos = result.videos.toBatchVideos(tasksByVideoId)

                _state.update { current ->
                    if (searchGeneration != generation) current else current.copy(
                        isSearching = false,
                        authorName = result.authorName,
                        authorId = result.authorId,
                        videos = batchVideos,
                        currentPage = result.currentPage,
                        totalPages = result.totalPages,
                        hasNextPage = result.hasNextPage,
                        selectedCount = 0
                    )
                }

            } catch (e: CancellationException) {
                // 协程取消必须原样抛出，否则会被当作普通失败并把页面卡在错误态
                throw e
            } catch (e: Exception) {
                if (searchGeneration != generation) return@launch
                // 捕获宽泛异常：站点改版 / 解析等运行期异常若逃逸出 viewModelScope 会直接崩溃
                AppLogger.e("BatchDownloadViewModel", "搜索失败: ${e.message}", e)
                _state.update { it.copy(
                    isSearching = false,
                    error = context.getString(
                        R.string.batch_search_failed_detail,
                        ErrorMapper.message(context, e, context.getString(R.string.common_load_failed))
                    )
                )}
            }
        }
    }

    fun loadMore() {
        val currentState = _state.value
        if (!currentState.hasNextPage || currentState.isLoadMore) {
            return
        }

        // 绑定发起时的搜索世代与作者：加载中若用户重新搜索，旧作者的下一页 / 失败信息须整批丢弃
        val generation = searchGeneration
        val requestAuthorId = currentState.authorId

        viewModelScope.launch {
            _state.update { current ->
                if (searchGeneration != generation) current else current.copy(isLoadMore = true)
            }

            try {
                val nextPage = currentState.currentPage + 1
                val result = withContext(Dispatchers.IO) {
                    content.getUserVideoList(requestAuthorId, page = nextPage)
                }

                if (result != null) {
                    val tasksByVideoId = downloads.tasks.value.indexTasksByVideoId()
                    val newBatchVideos = result.videos.toBatchVideos(tasksByVideoId)

                    _state.update { current ->
                        if (searchGeneration != generation) {
                            // 已被新一轮搜索取代：不追加、不改分页信息，isLoadMore 由新一轮搜索重置
                            current
                        } else {
                            current.copy(
                                isLoadMore = false,
                                videos = (current.videos + newBatchVideos).distinctBy { v -> v.videoId },
                                currentPage = result.currentPage,
                                totalPages = result.totalPages,
                                hasNextPage = result.hasNextPage
                            )
                        }
                    }
                } else {
                    // 空结果也必须复位 isLoadMore，否则加载按钮停在 loading 态、分页彻底失效
                    AppLogger.e("BatchDownloadViewModel", "加载更多失败: 第 $nextPage 页返回空结果")
                    _state.update { current ->
                        if (searchGeneration != generation) {
                            current
                        } else {
                            current.copy(
                                isLoadMore = false,
                                error = context.getString(R.string.batch_load_more_failed)
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BatchDownloadViewModel", "加载更多失败: ${e.message}", e)
                _state.update { current ->
                    if (searchGeneration != generation) {
                        current
                    } else {
                        current.copy(
                            isLoadMore = false,
                            error = context.getString(R.string.batch_load_more_failed)
                        )
                    }
                }
            }
        }
    }

    fun toggleVideoSelection(videoId: String) {
        _state.update { currentState ->
            val updatedVideos = currentState.videos.map { video ->
                if (video.videoId == videoId) {
                    val newSelected = !video.isSelected
                    video.copy(isSelected = newSelected)
                } else {
                    video
                }
            }
            val selectedCount = updatedVideos.count { it.isSelected }
            currentState.copy(videos = updatedVideos, selectedCount = selectedCount)
        }

        // 自动获取选中视频的画质列表
        loadQualitiesForSelectedVideos()
    }

    fun toggleAllSelection() {
        _state.update { currentState ->
            // 仅对可选择的视频（非已下载、非下载中）全选 / 取消全选
            val selectableVideos = currentState.videos.filter { !it.isDownloaded && !it.isDownloading }
            val allSelectableSelected = selectableVideos.all { it.isSelected }
            val updatedVideos = currentState.videos.map { video ->
                if (video.isDownloaded || video.isDownloading) {
                    video // 保持已下载 / 下载中视频的选中状态不变
                } else {
                    video.copy(isSelected = !allSelectableSelected)
                }
            }
            val selectedCount = updatedVideos.count { it.isSelected }
            currentState.copy(videos = updatedVideos, selectedCount = selectedCount)
        }

        // 自动获取选中视频的画质列表
        loadQualitiesForSelectedVideos()
    }

    fun updateVideoQuality(videoId: String, qualityIndex: Int) {
        _state.update { currentState ->
            val updatedVideos = currentState.videos.map { video ->
                if (video.videoId == videoId) {
                    video.copy(selectedQualityIndex = qualityIndex)
                } else {
                    video
                }
            }
            currentState.copy(videos = updatedVideos)
        }
    }

    fun loadQualitiesForSelectedVideos() {
        val selectedVideos = _state.value.videos.filter { it.isSelected && it.qualities.isEmpty() && !it.isLoadingQualities && !it.isDownloaded && !it.isDownloading }
        if (selectedVideos.isEmpty()) return

        viewModelScope.launch {
            // 标记为正在加载
            selectedVideos.forEach { video ->
                _state.update { currentState ->
                    val updatedVideos = currentState.videos.map { v ->
                        if (v.videoId == video.videoId) {
                            v.copy(isLoadingQualities = true)
                        } else {
                            v
                        }
                    }
                    currentState.copy(videos = updatedVideos)
                }
            }

            // 并发获取画质列表
            val qualitySemaphore = Semaphore(3)
            selectedVideos.map { video ->
                async {
                    qualitySemaphore.withPermit {
                        try {
                            val qualities = qualityLoader.load(video.videoId)

                            _state.update { currentState ->
                                val updatedVideos = currentState.videos.map { v ->
                                    if (v.videoId == video.videoId) {
                                        v.copy(
                                            qualities = qualities,
                                            isLoadingQualities = false
                                        )
                                    } else {
                                        v
                                    }
                                }
                                currentState.copy(videos = updatedVideos)
                            }

                            // 仅记录条数，不打印直链：downloadUrl 属敏感凭据
                            AppLogger.d("BatchDownloadViewModel", "已获取画质 ${video.videoId}: ${qualities.size} 项")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 捕获宽泛异常：否则会经 awaitAll() 抛回并取消整轮加载
                            AppLogger.e("BatchDownloadViewModel", "获取画质失败: ${video.videoId}", e)
                            // 标记加载失败
                            _state.update { currentState ->
                                val updatedVideos = currentState.videos.map { v ->
                                    if (v.videoId == video.videoId) {
                                        v.copy(isLoadingQualities = false)
                                    } else {
                                        v
                                    }
                                }
                                currentState.copy(videos = updatedVideos)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    fun startBatchDownload() {
        val selectedVideos = _state.value.videos.filter {
            it.isSelected && !it.isDownloaded && !it.isDownloading
        }

        if (selectedVideos.isEmpty()) {
            _messages.trySend(context.getString(R.string.batch_no_videos))
            return
        }

        // 仅记录有画质信息且实际会下载的视频ID
        val downloadableVideos = selectedVideos.filter {
            it.qualities.isNotEmpty() && it.selectedQualityIndex < it.qualities.size
        }
        val downloadingIds = downloadableVideos.map { it.videoId }.toSet()

        if (downloadingIds.isEmpty()) {
            _messages.trySend(context.getString(R.string.batch_qualities_not_loaded))
            return
        }

        // 被跳过视频的 UI 反馈
        val skippedCount = selectedVideos.size - downloadableVideos.size
        val message = if (skippedCount > 0) {
            context.getString(R.string.batch_skipped_no_quality, skippedCount)
        } else {
            context.getString(R.string.batch_download_added, downloadableVideos.size)
        }
        _messages.trySend(message)
        // 先屏蔽同步收敛，再置位「下载中」，避免两者之间被 syncDownloadStatuses 误判为已结束
        batchInitInProgress = true
        _state.update {
            it.copy(
                isDownloading = true,
                downloadingVideoIds = downloadingIds
            )
        }

        // startDownload 内部含目录解析、canonicalPath 校验等磁盘 I/O，改到 IO 线程避免批量时卡顿
        viewModelScope.launch(Dispatchers.IO) {
            try {
                enqueuer.enqueue(selectedVideos)
            } finally {
                batchInitInProgress = false
                // 与 init 里的投影保持一致：这里同样只需要 (videoId, status)
                syncDownloadStatuses(downloads.tasks.value.map { it.videoId to it.status })
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
