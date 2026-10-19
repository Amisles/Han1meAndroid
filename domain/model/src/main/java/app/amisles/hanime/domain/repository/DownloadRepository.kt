package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.DownloadStatus

/**
 * 下载仓储：把"发起下载 / 暂停 / 续传 / 查询状态"抽象出来。
 *
 * 上层（详情页、批量下载页、个人页）只关心**结果码与状态**，不关心分块策略、
 * 位图表落盘或前台服务桥接 —— 那些都属于 `:data` 内部实现。
 *
 * 之所以单列而不是并入 [ContentRepository]：下载依赖本地文件系统与前台服务，
 * 其失败模式（空间不足、路径不可写、服务被系统限制）与网络内容抓取完全不同。
 */
interface DownloadRepository {
    /**
     * 新建下载任务。
     *
     * @return 新建任务的 id；负数表示未新建：
     *   [RESULT_INVALID_URL] 直链非法或路径越界，
     *   [RESULT_ALREADY_ACTIVE] 同一文件已有进行中 / 等待中的任务，
     *   [RESULT_ALREADY_COMPLETED] 同一文件已下载完成。
     */
    fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String,
        videoId: String
    ): Int

    fun pauseDownload(taskId: Int)
    fun resumeDownload(taskId: Int)
    fun cancelDownload(taskId: Int)

    /** 一键重试全部失败任务。 */
    fun retryAllFailed()

    suspend fun getCompletedDownloadCount(): Int
    fun isVideoDownloaded(videoId: String, quality: String = ""): Boolean
    fun isVideoDownloading(videoId: String, quality: String = ""): Boolean
    fun getDownloadStatus(videoId: String): DownloadStatus?

    companion object {
        const val RESULT_INVALID_URL = -1
        const val RESULT_ALREADY_ACTIVE = -2
        const val RESULT_ALREADY_COMPLETED = -3
    }
}
