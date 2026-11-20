package app.amisles.hanime.data.download

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call

/**
 * 在途 HTTP 连接与取消标记。
 *
 * 用 ConcurrentLinkedQueue 而非 CopyOnWriteArrayList：后者每次 add 都复制整个底层数组，
 * 而分块路径每块、每次重连都会 track，累积过程为 O(n²)。
 */
internal class DownloadCallTracker {

    private val calls = ConcurrentHashMap<Int, ConcurrentLinkedQueue<Call>>()
    private val cancelFlags = ConcurrentHashMap<Int, AtomicBoolean>()

    fun track(taskId: Int, call: Call) {
        calls.computeIfAbsent(taskId) { ConcurrentLinkedQueue() }.add(call)
        if (cancelFlags[taskId]?.get() == true) {
            runCatching { call.cancel() }
        }
    }

    /**
     * 连接结束后解除跟踪，使列表只保留「在途连接」。
     *
     * 分块路径每块、每次重连都会 [track]，不在此移除会让列表随块数与重试次数无界增长。
     */
    fun untrack(taskId: Int, call: Call) {
        calls[taskId]?.remove(call)
    }

    fun cancelAll(taskId: Int) {
        // 顺序重要：必须「先置位、后排空」，见 [track] 的正确性论证
        markCancelled(taskId)
        calls.remove(taskId)?.forEach { runCatching { it.cancel() } }
    }

    fun markCancelled(taskId: Int) {
        cancelFlags.computeIfAbsent(taskId) { AtomicBoolean(false) }.set(true)
    }

    fun isCancelled(taskId: Int): Boolean = cancelFlags[taskId]?.get() == true

    fun forget(taskId: Int) {
        calls.remove(taskId)
        cancelFlags.remove(taskId)
    }

    fun reset() {
        calls.clear()
        cancelFlags.clear()
    }
}
