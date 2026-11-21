package app.amisles.hanime.domain.model

/**
 * 下载状态的唯一聚合口径。
 *
 * 同一视频可能有多个画质任务；各页面必须用同一优先级判断“这个视频现在算什么状态”，
 * 否则详情、批量下载和下载列表会同时显示不同结果。
 */
object DownloadStatusPolicy {

    /** “下载中”只包含真正会自行推进的状态；PAUSED 需要用户恢复，不能算进行中。 */
    fun isActive(status: DownloadStatus): Boolean =
        status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PENDING

    /**
     * 多任务聚合优先级：进行中 > 暂停 > 失败 > 已完成。
     *
     * 进行中优先，避免已有一档完成时把仍在下载的视频显示成“已下载”；
     * 暂停与失败优先于完成，让用户还能看到可恢复 / 可重试的入口。
     */
    fun aggregate(statuses: Iterable<DownloadStatus>): DownloadStatus? =
        statuses.minByOrNull(::priority)

    private fun priority(status: DownloadStatus): Int = when (status) {
        DownloadStatus.DOWNLOADING -> 0
        DownloadStatus.PENDING -> 1
        DownloadStatus.PAUSED -> 2
        DownloadStatus.FAILED -> 3
        DownloadStatus.COMPLETED -> 4
    }
}
