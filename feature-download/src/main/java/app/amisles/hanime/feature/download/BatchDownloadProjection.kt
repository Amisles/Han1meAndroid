package app.amisles.hanime.feature.download

import app.amisles.hanime.domain.model.BatchVideoItem
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.model.HanimeVideo

/**
 * 不能按 url 匹配：downloadUrl 是带时效签名的 CDN 直链，重新拉取画质页会得到不同 url，
 * 与已存在任务的 url 必然不相等，导致漏配。
 */
internal fun List<DownloadTask>.indexTasksByVideoId(): Map<String, List<DownloadStatus>> =
    filter { it.videoId.isNotBlank() }.groupBy({ it.videoId }, { it.status })

internal fun List<Pair<String, DownloadStatus>>.indexStatusesByVideoId(): Map<String, List<DownloadStatus>> =
    filter { it.first.isNotBlank() }.groupBy({ it.first }, { it.second })

/**
 * 是否「下载中」。仅 DOWNLOADING / PENDING 计入；PAUSED 不计入，使已暂停的视频仍可在本页
 * 重新选中加入队列以恢复下载。
 */
internal fun List<DownloadStatus>.hasActiveDownload(): Boolean = any {
    it == DownloadStatus.DOWNLOADING || it == DownloadStatus.PENDING
}

internal fun resolveVideoFlags(
    videoId: String,
    tasksByVideoId: Map<String, List<DownloadStatus>>
): Pair<Boolean, Boolean> {
    val statuses = tasksByVideoId[videoId].orEmpty()
    return statuses.any { it == DownloadStatus.COMPLETED } to statuses.hasActiveDownload()
}

internal fun BatchDownloadState.syncDownloadStatuses(
    statusPairs: List<Pair<String, DownloadStatus>>
): BatchDownloadState {
    val tasksByVideoId = statusPairs.indexStatusesByVideoId()
    if (videos.isEmpty() && downloadingVideoIds.isEmpty()) return this

    var changed = false
    val updatedVideos = videos.map { video ->
        val (isDownloaded, isDownloading) = resolveVideoFlags(video.videoId, tasksByVideoId)
        if (video.isDownloaded == isDownloaded && video.isDownloading == isDownloading) {
            video
        } else {
            changed = true
            video.copy(isDownloaded = isDownloaded, isDownloading = isDownloading)
        }
    }

    val newDownloadingIds = downloadingVideoIds
        .filter { tasksByVideoId[it].orEmpty().hasActiveDownload() }
        .toSet()

    return if (!changed && newDownloadingIds == downloadingVideoIds) {
        this
    } else {
        copy(
            videos = updatedVideos,
            downloadingVideoIds = newDownloadingIds,
            isDownloading = newDownloadingIds.isNotEmpty()
        )
    }
}

internal fun List<HanimeVideo>.toBatchVideos(
    tasksByVideoId: Map<String, List<DownloadStatus>>
): List<BatchVideoItem> = distinctBy { it.id }.map { video ->
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
