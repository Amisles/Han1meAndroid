package app.amisles.hanime.domain.model

/** 按当前速率估算下载剩余毫秒；速率或总量未知时返回 0（界面不显示剩余时间）。 */
object DownloadEta {
    fun remainingMs(downloadedBytes: Long, totalBytes: Long, speedBps: Long): Long {
        if (speedBps <= 0L || totalBytes <= 0L) return 0L
        return (totalBytes - downloadedBytes).coerceAtLeast(0L) * 1_000L / speedBps
    }
}
