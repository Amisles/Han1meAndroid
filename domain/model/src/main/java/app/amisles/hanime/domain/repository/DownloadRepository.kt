package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.DownloadGateState
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadStorageInfo
import app.amisles.hanime.domain.model.DownloadTask
import kotlinx.coroutines.flow.StateFlow

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
     * 全部任务的实时快照（含进度）。UI 订阅它渲染列表与统计。
     *
     * 放在接口上而非让界面注入具体 DownloadManager：下载页与个人页都只需要「任务列表」，
     * 不该知道背后是文件分块、位图表还是前台服务。
     */
    val tasks: StateFlow<List<DownloadTask>>

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

    /** 存储面板数据：目录、已用 / 剩余空间、孤儿位图统计。 */
    suspend fun getStorageInfo(): DownloadStorageInfo

    /** 调度闸门状态：因「仅 Wi-Fi / 仅充电 / 仅夜间」中的哪些条件而暂停启动新任务。 */
    val gateState: StateFlow<DownloadGateState>

    /** 重新求值闸门并尝试继续排队任务（设置变更后调用）。 */
    fun reevaluateGate()

    /** 处于 PENDING 的任务数，用于判断是否值得展示阻塞提示。 */
    fun pendingCount(): Int

    /** 把任务提到队首（下次有空闲槽位时优先启动）。 */
    fun moveToFront(taskId: Int)

    /** 暂停全部进行中与排队中的任务。 */
    fun pauseAll()

    /** 恢复全部已暂停的任务，受并发上限与调度闸门约束。 */
    fun resumeAll()

    /**
     * 导出下载列表迁移包（zip：manifest.json + 各任务续传位图）。
     *
     * @return zip 字节；没有可迁移任务时返回 null。
     */
    suspend fun exportMigration(): ByteArray?

    /**
     * 导入迁移包。任务一律落到 PAUSED，位图按新目录重定位，需用户确认后再继续。
     *
     * @return 成功导入的任务数。
     */
    suspend fun importMigration(bytes: ByteArray): Int

    /** 清理无任务引用的位图文件，返回释放字节数。 */
    suspend fun clearOrphanPartmaps(): Long
    fun isVideoDownloaded(videoId: String, quality: String = ""): Boolean
    fun isVideoDownloading(videoId: String, quality: String = ""): Boolean
    fun getDownloadStatus(videoId: String): DownloadStatus?

    companion object {
        const val RESULT_INVALID_URL = -1
        const val RESULT_ALREADY_ACTIVE = -2
        const val RESULT_ALREADY_COMPLETED = -3
    }
}
