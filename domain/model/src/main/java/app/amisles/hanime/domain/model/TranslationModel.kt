package app.amisles.hanime.domain.model

sealed class TranslationModelState {
    data object NotDownloaded : TranslationModelState()

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : TranslationModelState() {
        /** [totalBytes] 为 0 表示响应没有长度，进度未知；此时按字节算比例会得到 NaN 并显示成「NaN%」。 */
        val progress: Float
            get() = if (totalBytes <= 0L) {
                0f
            } else {
                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            }
    }

    data class Ready(val filePath: String) : TranslationModelState()

    data object Failed : TranslationModelState()
}
