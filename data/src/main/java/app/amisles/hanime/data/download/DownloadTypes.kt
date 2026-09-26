package app.amisles.hanime.data.download

import app.amisles.hanime.domain.model.DownloadStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 下载链路的数据载体：进度回调载荷、分块 IO 诊断计数、Content-Range 正则、首下探测结果。
 */
internal data class ProgressUpdate(
    val taskId: Int,
    val title: String,
    val progress: Int,
    val status: DownloadStatus
)

/**
 * 分块 IO 诊断计数：按采样窗口累计 read / write 的耗时与字节数，用于区分
 * 「等网络」「本地写入阻塞」「设备/链路瓶颈」。由 monitor 周期读取并归零。
 */
internal class ChunkIoStats {
    val readNanos = AtomicLong(0)
    val readBytes = AtomicLong(0)
    val writeNanos = AtomicLong(0)
    val writeBytes = AtomicLong(0)
    val slowWrites = AtomicInteger(0)
    val protocolLogged = AtomicBoolean(false)
}

// Content-Range: bytes <start>-<end>/<total|*>
internal val CONTENT_RANGE_REGEX = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)")

// 首下探测结果
internal data class ProbeResult(val supportsRange: Boolean, val totalBytes: Long, val bps: Long)
