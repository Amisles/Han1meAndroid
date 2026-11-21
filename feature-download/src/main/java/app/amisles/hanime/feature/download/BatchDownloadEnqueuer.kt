package app.amisles.hanime.feature.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.BatchVideoItem
import app.amisles.hanime.domain.model.StartDownloadResult
import app.amisles.hanime.domain.repository.DownloadRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class BatchDownloadEnqueuer @Inject constructor(
    private val downloads: DownloadRepository
) {
    suspend fun enqueue(videos: List<BatchVideoItem>) {
        videos.forEach { video ->
            try {
                val quality = if (video.qualities.isNotEmpty() && video.selectedQualityIndex < video.qualities.size) {
                    video.qualities[video.selectedQualityIndex]
                } else {
                    null
                }
                if (quality == null) {
                    AppLogger.w("BatchDownloadEnqueuer", "跳过无画质信息的视频: " + video.title)
                } else {
                    val result = downloads.startDownload(
                        title = video.title,
                        quality = quality.quality,
                        url = quality.downloadUrl,
                        thumbnailUrl = video.thumbnailUrl,
                        videoId = video.videoId
                    )
                    if (result !is StartDownloadResult.Started) {
                        AppLogger.w("BatchDownloadEnqueuer", "未创建下载任务($result): " + video.title)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("BatchDownloadEnqueuer", "添加下载失败: " + video.title, e)
            }
        }
    }
}
