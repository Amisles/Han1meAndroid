package app.amisles.hanime.feature.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.amisles.hanime.data.remote.NetworkService
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.data.parser.DownloadPageParser
import app.amisles.hanime.domain.model.BatchVideoItem
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isLoading: Boolean = false,
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
    val downloadingVideoIds: Set<String> = emptySet(),
    val downloadMessage: String? = null
)

@HiltViewModel
class BatchDownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkService: NetworkService,
    private val downloadManager: DownloadManager,
    private val downloadPageParser: DownloadPageParser
) : ViewModel() {
    private val _state = MutableStateFlow(BatchDownloadState())
    val state: StateFlow<BatchDownloadState> = _state.asStateFlow()

    // 搜索世代：每次 searchAuthor 递增，进行中的 loadMore 据此识别自己是否已被新一轮搜索取代
    private var searchGeneration = 0

    @Volatile
    private var batchInitInProgress = false

    init {
        // 观察下载任务变化，自动更新视频列表中的下载状态
        viewModelScope.launch {
            try {
                downloadManager.tasks.collect { tasks ->
                    syncDownloadStatuses(tasks)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 状态同步异常不应让批量下载页崩溃
                AppLogger.e("BatchDownloadViewModel", "同步下载状态失败: ${e.message}", e)
            }
        }
    }

    /**
     * 按 videoId 聚合下载任务。
     *
     * 不能按 url 匹配：downloadUrl 是带时效签名的 CDN 直链，重新拉取画质页会得到不同 url，
     * 与已存在任务的 url 必然不相等，导致漏配。
     */
    private fun indexTasksByVideoId(tasks: List<DownloadTask>): Map<String, List<DownloadTask>> =
        tasks.filter { it.videoId.isNotBlank() }.groupBy { it.videoId }

    /**
     * 是否「下载中」。仅 DOWNLOADING / PENDING 计入；PAUSED 不计入，使已暂停的视频仍可在本页
     * 重新选中加入队列以恢复下载。
     */
    private fun List<DownloadTask>.hasActiveDownload(): Boolean = any {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
    }

    /**
     * 单个视频的（已下载, 下载中）判定。加载初始态与后续同步共用，避免两处谓词不一致导致
     * 首次同步后界面状态跳变。
     */
    private fun resolveVideoFlags(
        videoId: String,
        tasksByVideoId: Map<String, List<DownloadTask>>
    ): Pair<Boolean, Boolean> {
        val videoTasks = tasksByVideoId[videoId].orEmpty()
        return videoTasks.any { it.status == DownloadStatus.COMPLETED } to videoTasks.hasActiveDownload()
    }

    /**
     * 根据下载任务状态同步更新视频列表与 downloadingVideoIds。
     *
     * 判定键统一为 videoId，且与 [searchAuthor] / [loadMore] 的初始判定使用同一套逻辑，
     * 避免首次同步后状态跳变。tasks 发射频率跟随下载进度（可达每秒数次），故先建索引避免嵌套遍历。
     */
    private fun syncDownloadStatuses(tasks: List<DownloadTask>) {
        if (batchInitInProgress) return

        val tasksByVideoId = indexTasksByVideoId(tasks)

        _state.update { currentState ->
            if (currentState.videos.isEmpty() && currentState.downloadingVideoIds.isEmpty()) {
                return@update currentState
            }

            var changed = false
            val updatedVideos = currentState.videos.map { video ->
                val (isDownloaded, isDownloading) = resolveVideoFlags(video.videoId, tasksByVideoId)
                if (video.isDownloaded == isDownloaded && video.isDownloading == isDownloading) {
                    // 状态未变时复用原对象，避免下游无意义重组
                    video
                } else {
                    changed = true
                    video.copy(isDownloaded = isDownloaded, isDownloading = isDownloading)
                }
            }

            val newDownloadingIds = currentState.downloadingVideoIds
                .filter { tasksByVideoId[it].orEmpty().hasActiveDownload() }
                .toSet()

            if (!changed && newDownloadingIds == currentState.downloadingVideoIds) {
                currentState
            } else {
                currentState.copy(
                    videos = updatedVideos,
                    downloadingVideoIds = newDownloadingIds,
                    isDownloading = newDownloadingIds.isNotEmpty()
                )
            }
        }
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
        searchGeneration++

        viewModelScope.launch {
            _state.update {
                it.copy(
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
                    networkService.fetchUserVideoList(authorId, page = 1)
                }

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

                val tasksByVideoId = indexTasksByVideoId(downloadManager.tasks.value)
                val batchVideos = result.videos.distinctBy { it.id }.map { video ->
                    val (downloaded, downloading) = resolveVideoFlags(video.id, tasksByVideoId)
                    BatchVideoItem(
                        videoId = video.id,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        videoUrl = video.videoUrl,
                        duration = video.duration,
                        author = video.author,
                        publishTime = video.publishTime,
                        isSelected = false,
                        qualities = emptyList(),
                        selectedQualityIndex = 0,
                        isLoadingQualities = false,
                        isDownloaded = downloaded,
                        isDownloading = downloading
                    )
                }

                _state.update { it.copy(
                    isSearching = false,
                    authorName = result.authorName,
                    authorId = result.authorId,
                    videos = batchVideos,
                    currentPage = result.currentPage,
                    totalPages = result.totalPages,
                    hasNextPage = result.hasNextPage,
                    selectedCount = 0
                )}

            } catch (e: CancellationException) {
                // 协程取消必须原样抛出，否则会被当作普通失败并把页面卡在错误态
                throw e
            } catch (e: Exception) {
                // 捕获宽泛异常：站点改版 / 解析等运行期异常若逃逸出 viewModelScope 会直接崩溃
                AppLogger.e("BatchDownloadViewModel", "搜索失败: ${e.message}", e)
                _state.update { it.copy(
                    isSearching = false,
                    error = context.getString(R.string.batch_search_failed_detail, e.message ?: "")
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
                    networkService.fetchUserVideoList(requestAuthorId, page = nextPage)
                }

                if (result != null) {
                    val tasksByVideoId = indexTasksByVideoId(downloadManager.tasks.value)
                    val newBatchVideos = result.videos.map { video ->
                        val (downloaded, downloading) = resolveVideoFlags(video.id, tasksByVideoId)
                        BatchVideoItem(
                            videoId = video.id,
                            title = video.title,
                            thumbnailUrl = video.thumbnailUrl,
                            videoUrl = video.videoUrl,
                            duration = video.duration,
                            author = video.author,
                            publishTime = video.publishTime,
                            isSelected = false,
                            qualities = emptyList(),
                            selectedQualityIndex = 0,
                            isLoadingQualities = false,
                            isDownloaded = downloaded,
                            isDownloading = downloading
                        )
                    }

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
                            val downloadPageHtml = withContext(Dispatchers.IO) {
                                networkService.fetchDownloadPageWithBaseUrl(video.videoId)
                            }

                            val qualities = withContext(Dispatchers.IO) {
                                downloadPageParser.parse(downloadPageHtml.html, downloadPageHtml.baseUrl)
                            }

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
            _state.update { it.copy(downloadMessage = context.getString(R.string.batch_no_videos)) }
            return
        }

        // 仅记录有画质信息且实际会下载的视频ID
        val downloadableVideos = selectedVideos.filter {
            it.qualities.isNotEmpty() && it.selectedQualityIndex < it.qualities.size
        }
        val downloadingIds = downloadableVideos.map { it.videoId }.toSet()

        if (downloadingIds.isEmpty()) {
            _state.update { it.copy(downloadMessage = context.getString(R.string.batch_qualities_not_loaded)) }
            return
        }

        // 被跳过视频的 UI 反馈
        val skippedCount = selectedVideos.size - downloadableVideos.size
        val message = if (skippedCount > 0) {
            context.getString(R.string.batch_skipped_no_quality, skippedCount)
        } else {
            context.getString(R.string.batch_download_added, downloadableVideos.size)
        }
        // 先屏蔽同步收敛，再置位「下载中」，避免两者之间被 syncDownloadStatuses 误判为已结束
        batchInitInProgress = true
        _state.update {
            it.copy(
                isDownloading = true,
                downloadingVideoIds = downloadingIds,
                downloadMessage = message
            )
        }

        // startDownload 内部含目录解析、canonicalPath 校验等磁盘 I/O，改到 IO 线程避免批量时卡顿
        viewModelScope.launch(Dispatchers.IO) {
            try {
                selectedVideos.forEach { video ->
                    try {
                        val quality = if (video.qualities.isNotEmpty() && video.selectedQualityIndex < video.qualities.size) {
                            video.qualities[video.selectedQualityIndex]
                        } else {
                            null
                        }

                        // 画质为空时跳过下载（videoUrl 是网页 URL 而非视频直链）
                        if (quality == null) {
                            AppLogger.w("BatchDownloadViewModel", "跳过无画质信息的视频: ${video.title}")
                        } else {
                            val code = downloadManager.startDownload(
                                title = video.title,
                                quality = quality.quality,
                                url = quality.downloadUrl,
                                thumbnailUrl = video.thumbnailUrl,
                                videoId = video.videoId
                            )
                            if (code < 0) {
                                AppLogger.w(
                                    "BatchDownloadViewModel",
                                    "未创建下载任务(code=$code): ${video.title}"
                                )
                            }
                        }

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.e("BatchDownloadViewModel", "添加下载失败: ${video.title}", e)
                    }
                }
            } finally {
                batchInitInProgress = false
                syncDownloadStatuses(downloadManager.tasks.value)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearDownloadMessage() {
        _state.update { it.copy(downloadMessage = null) }
    }
}
