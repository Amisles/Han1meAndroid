package app.amisles.hanime.feature.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadQuality
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.result.AppResult
import javax.inject.Inject

class BatchQualityLoader @Inject constructor(
    private val content: ContentRepository
) {
    suspend fun load(videoId: String): List<DownloadQuality> =
        when (val result = content.getDownloadQualities(videoId)) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                AppLogger.e("BatchQualityLoader", "获取画质失败: " + result.message)
                emptyList()
            }
            is AppResult.Loading -> emptyList()
        }
}
