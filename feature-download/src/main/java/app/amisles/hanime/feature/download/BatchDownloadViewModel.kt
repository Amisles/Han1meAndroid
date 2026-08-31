package app.amisles.hanime.feature.download

import android.util.Log
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
import java.io.IOException
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
    val downloadingVideoIds: Set<String> = emptySet()
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

    init {
        // 观察下载任务变化，自动更新视频列表中的下载状态
        viewModelScope.launch {
            downloadManager.tasks.collect { tasks ->
                syncDownloadStatuses(tasks)
            }
        }
    }

    /**
     * 根据下载任务状态同步更新视频列表。
     * 当下载完成或失败时，自动更新对应视频项的状态并清理 downloadingVideoIds。
     */
    private fun syncDownloadStatuses(tasks: List<DownloadTask>) {
        _state.update { currentState ->
            if (currentState.videos.isEmpty()) return@update currentState

            val updatedVideos = currentState.videos.map { video ->
                // 通过下载URL精确匹配任务
                val task = tasks.find { task ->
                    video.qualities.any { q -> q.downloadUrl == task.url }
                }
                if (task != null) {
                    video.copy(
                        isDownloaded = task.status == DownloadStatus.COMPLETED,
                        isDownloading = task.status == DownloadStatus.DOWNLOADING ||
                                        task.status == DownloadStatus.PENDING
                    )
                } else {
                    video
                }
            }

            // 从 downloadingVideoIds 中移除已完成或失败的任务
            val newDownloadingIds = currentState.downloadingVideoIds.filterNot { id ->
                val video = updatedVideos.find { it.videoId == id }
                val task = video?.let { v ->
                    tasks.find { task -> v.qualities.any { q -> q.downloadUrl == task.url } }
                }
                task != null && (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.FAILED)
            }.toSet()

            currentState.copy(
                videos = updatedVideos,
                downloadingVideoIds = newDownloadingIds,
                isDownloading = newDownloadingIds.isNotEmpty()
            )
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

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSearching = true,
                    error = null,
                    videos = emptyList(),
                    authorName = "",
                    authorId = ""
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

                val batchVideos = result.videos.map { video ->
                    val downloaded = downloadManager.isVideoDownloaded(video.id)
                    val downloading = downloadManager.isVideoDownloading(video.id)
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

            } catch (e: IOException) {
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

        viewModelScope.launch {
            _state.update { it.copy(isLoadMore = true) }

            try {
                val nextPage = currentState.currentPage + 1
                val result = withContext(Dispatchers.IO) {
                    networkService.fetchUserVideoList(currentState.authorId, page = nextPage)
                }

                if (result != null) {
                    val newBatchVideos = result.videos.map { video ->
                        val downloaded = downloadManager.isVideoDownloaded(video.id)
                        val downloading = downloadManager.isVideoDownloading(video.id)
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
                        isLoadMore = false,
                        videos = it.videos + newBatchVideos,
                        currentPage = result.currentPage,
                        totalPages = result.totalPages,
                        hasNextPage = result.hasNextPage
                    )}
                } else {
                    // G12：此前 result == null 时只走空分支，isLoadMore 永远不复位，
                    // 加载按钮停在 loading 态，且 loadMore() 开头的 isLoadMore 守卫会让分页彻底失效
                    AppLogger.e("BatchDownloadViewModel", "加载更多失败: 第 $nextPage 页返回空结果")
                    _state.update { it.copy(
                        isLoadMore = false,
                        error = context.getString(R.string.batch_load_more_failed)
                    )}
                }
            } catch (e: IOException) {
                AppLogger.e("BatchDownloadViewModel", "加载更多失败: ${e.message}", e)
                _state.update { it.copy(
                    isLoadMore = false,
                    error = context.getString(R.string.batch_load_more_failed)
                )}
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
            // 仅对可选择的视频（非已下载、非下载中）进行全选/取消全选
            val selectableVideos = currentState.videos.filter { !it.isDownloaded && !it.isDownloading }
            val allSelectableSelected = selectableVideos.all { it.isSelected }
            val updatedVideos = currentState.videos.map { video ->
                if (video.isDownloaded || video.isDownloading) {
                    video // 保持已下载/下载中视频的选中状态不变
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

                            // 选择视频并拉取画质后，将下载直链打印到 logcat（INFO 级别，仅打印链接本身）
                            qualities.forEach { quality ->
                                Log.i("BatchDownload", quality.downloadUrl)
                            }
                        } catch (e: IOException) {
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
            _state.update { it.copy(error = context.getString(R.string.batch_no_videos)) }
            return
        }

        // 仅记录有画质信息且实际会下载的视频ID
        val downloadableVideos = selectedVideos.filter {
            it.qualities.isNotEmpty() && it.selectedQualityIndex < it.qualities.size
        }
        val downloadingIds = downloadableVideos.map { it.videoId }.toSet()

        if (downloadingIds.isEmpty()) {
            _state.update { it.copy(error = context.getString(R.string.batch_qualities_not_loaded)) }
            return
        }

        // G12：被跳过的视频需要 UI 反馈，否则「已选 N 个」与实际入队数不一致且用户无从得知
        val skippedCount = selectedVideos.size - downloadableVideos.size
        _state.update {
            it.copy(
                isDownloading = true,
                downloadingVideoIds = downloadingIds,
                error = if (skippedCount > 0) {
                    context.getString(R.string.batch_skipped_no_quality, skippedCount)
                } else {
                    it.error
                }
            )
        }

        viewModelScope.launch {
            selectedVideos.forEach { video ->
                try {
                    val quality = if (video.qualities.isNotEmpty() && video.selectedQualityIndex < video.qualities.size) {
                        video.qualities[video.selectedQualityIndex]
                    } else {
                        null
                    }

                    // 画质为空时跳过下载（videoUrl是网页URL不是视频直链）
                    if (quality == null) {
                        Log.w("BatchDownload", "跳过无画质信息的视频: ${video.title}")
                    } else {
                        downloadManager.startDownload(
                            title = video.title,
                            quality = quality.quality,
                            url = quality.downloadUrl,
                            thumbnailUrl = video.thumbnailUrl,
                            videoId = video.videoId
                        )
                    }

                } catch (e: IndexOutOfBoundsException) {
                    AppLogger.e("BatchDownloadViewModel", "添加下载失败: ${video.title}", e)
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
