package app.amisles.hanime.data.download

import kotlinx.coroutines.delay

/**
 * 全局限速器：令牌桶，所有下载任务共享同一份带宽预算。
 *
 * 单次最多睡 [MAX_SLEEP_MS]，超出的额度留到下一轮，保证暂停 / 取消能及时响应。
 */
internal class DownloadRateLimiter {

    private val lock = Any()
    private var bytesPerSecond: Long = 0L
    private var tokens: Double = 0.0
    private var lastRefillNanos: Long = System.nanoTime()

    val currentLimitBytesPerSecond: Long
        get() = synchronized(lock) { bytesPerSecond }

    fun setLimit(bytesPerSecond: Long) {
        synchronized(lock) {
            this.bytesPerSecond = bytesPerSecond.coerceAtLeast(0L)
            // 调高限额时立即补充令牌，避免用户等了半天还在按旧速率走
            if (this.bytesPerSecond > 0L && tokens <= 0.0) {
                tokens = this.bytesPerSecond.toDouble()
            }
            lastRefillNanos = System.nanoTime()
        }
    }

    suspend fun acquire(bytes: Int) {
        if (bytes <= 0) return
        while (true) {
            val waitNanos = synchronized(lock) { reserve(bytes.toLong()) }
            if (waitNanos <= 0L) return
            delay(minOf(waitNanos / 1_000_000L + 1L, MAX_SLEEP_MS))
        }
    }

    private fun reserve(need: Long): Long {
        if (bytesPerSecond <= 0L) {
            tokens = 0.0
            lastRefillNanos = System.nanoTime()
            return 0L
        }
        val now = System.nanoTime()
        val elapsed = now - lastRefillNanos
        if (elapsed > 0L) {
            tokens = minOf(
                tokens + elapsed / 1_000_000_000.0 * bytesPerSecond,
                bytesPerSecond.toDouble()
            )
            lastRefillNanos = now
        }
        tokens -= need
        if (tokens >= 0.0) return 0L
        return ((-tokens) / bytesPerSecond * 1_000_000_000.0).toLong()
    }

    private companion object {
        const val MAX_SLEEP_MS = 200L
    }
}

/** 进程级单例：分块与单连接两条路径共用同一预算。 */
internal object GlobalDownloadRateLimiter {
    val instance = DownloadRateLimiter()
}

/** 限速档位（MB/s），0 表示不限速。 */
val DOWNLOAD_RATE_LIMIT_OPTIONS = listOf(0, 1, 2, 5, 10, 20)
