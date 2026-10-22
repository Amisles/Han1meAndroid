package app.amisles.hanime.data.download

import kotlinx.coroutines.delay

/**
 * 全局限速器：令牌桶，所有下载任务共享同一份带宽预算。
 *
 * 关键约束：只有**真正拿到令牌**时才扣减。原实现无论是否足够都先扣 `need`，
 * 令牌不足时留下负值，下一轮重试再扣一次，`tokens` 恒定为一个不断加深的负数，
 * 等待时间算出来永远为正 —— 表现为下载卡死在第一个 buffer 上，一个字节都不写。
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
            if (this.bytesPerSecond > 0L && tokens <= 0.0) {
                tokens = this.bytesPerSecond.toDouble()
            }
            lastRefillNanos = System.nanoTime()
        }
    }

    suspend fun acquire(bytes: Int) {
        if (bytes <= 0) return
        while (true) {
            val waitNanos = synchronized(lock) { reserveOrWait(bytes.toLong()) }
            if (waitNanos <= 0L) return
            delay(minOf(waitNanos / 1_000_000L + 1L, MAX_SLEEP_MS))
        }
    }

    /** @return 0 表示令牌已扣减、可以放行；>0 表示还需等待的纳秒数（不扣减）。 */
    private fun reserveOrWait(need: Long): Long {
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
        if (tokens >= need) {
            tokens -= need
            return 0L
        }
        return ((need - tokens) / bytesPerSecond * 1_000_000_000.0).toLong()
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
