package app.amisles.hanime.data.download

import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载并发预算：软槽位（同时运行的任务数）与单任务并行连接上限的推导依据。
 *
 * 软槽位用 CAS 原子获取，便于启动链路在做任何磁盘 I/O 之前快速拒绝。
 */
internal class DownloadConcurrencyBudget(initialLimit: Int) {

    private val active = AtomicInteger(0)

    @Volatile
    var limit: Int = initialLimit.coerceIn(1, MAX_CONCURRENT)
        private set

    val activeCount: Int get() = active.get()

    fun updateLimit(maxConcurrent: Int) {
        limit = maxConcurrent.coerceIn(1, MAX_CONCURRENT)
    }

    fun tryAcquire(): Boolean {
        while (true) {
            val current = active.get()
            // limit 为 @Volatile，每轮重读以立即响应设置变更
            if (current >= limit) return false
            if (active.compareAndSet(current, current + 1)) return true
        }
    }

    fun release(): Int = active.decrementAndGet()

    fun reset() {
        active.set(0)
    }

    /**
     * 使「并发任务数 × 每任务连接数」不超过全局软预算 [MAX_TOTAL_CONNECTIONS]。
     *
     * 软预算只是启发式，硬上限由连接信号量保证。
     */
    fun effectiveParallelismCap(): Int {
        val activeTasks = maxOf(active.get(), 1)
        // 整数除法在任务数接近上限时会把每任务连接数压到 1，故给每任务一个保底值，
        // 超出软预算的部分由全局信号量兜住
        return maxOf(MIN_PARALLEL_PER_TASK, MAX_TOTAL_CONNECTIONS / activeTasks)
    }
}
