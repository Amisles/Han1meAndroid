package app.amisles.hanime.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 下载相关设置：并发、限速、调度条件、队列顺序与存储目录。 */
interface DownloadSettings {
    val maxDownloadConcurrent: Int
    val maxDownloadConcurrentFlow: StateFlow<Int>
    fun setMaxDownloadConcurrent(max: Int)

    /** 全局下载限速（MB/s），0 表示不限速。所有任务共享同一份带宽预算。 */
    val downloadRateLimit: Int
    val downloadRateLimitFlow: StateFlow<Int>
    fun setDownloadRateLimit(megabytesPerSecond: Int)

    /** 下载调度条件：仅在 Wi-Fi 下 / 仅在充电时 / 仅在夜间闲时（0–6 点）。三者可组合。 */
    val downloadWifiOnly: Boolean
    val downloadWifiOnlyFlow: StateFlow<Boolean>
    fun setDownloadWifiOnly(enabled: Boolean)

    val downloadChargingOnly: Boolean
    val downloadChargingOnlyFlow: StateFlow<Boolean>
    fun setDownloadChargingOnly(enabled: Boolean)

    val downloadNightOnly: Boolean
    val downloadNightOnlyFlow: StateFlow<Boolean>
    fun setDownloadNightOnly(enabled: Boolean)

    /** 下载队列顺序：任务 id 按执行先后排列，逗号分隔。未列出的任务排在其后。 */
    val downloadQueueOrder: List<Int>
    fun setDownloadQueueOrder(ids: List<Int>)

    /** 空串表示默认目录，由下载子系统解析。 */
    var downloadStoragePath: String
    val downloadStoragePathFlow: StateFlow<String>
}
