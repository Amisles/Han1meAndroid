package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载任务的权威内存存储：唯一的 id 来源、暂停序号与 UI 快照派生。
 *
 * 调度器、会话控制与更新器经由本类读写任务，避免多个协作者各自持有一份任务表。
 */
internal class DownloadTaskStore {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** 保护「读-改-写」任务与列表快照；持有期间禁止磁盘 I/O。 */
    val lock = ReentrantLock()
    val tasksById = ConcurrentHashMap<Int, DownloadTask>()

    private val idCounter = AtomicInteger(0)

    /** 备份导入等外部写入会占用任意 id；记录已占用的下界，新建任务的 id 一律跳过该区间。 */
    @Volatile
    private var reservedIdFloor = 0

    /**
     * 每个任务最近一次「暂停」的序号（单调递增）。
     *
     * 调度决定与真正开跑之间隔着锁外的续传点推导（磁盘 IO），这段窗口里用户完全可能按下暂停。
     * 任务处于 PENDING 时复核 status 即可发现；但从 PAUSED 恢复时「再次暂停」不改变 status
     * （本来就是 PAUSED），status 复核失效，故用序号记录「暂停意图」：调度前取号、开跑前比对，
     * 序号变了就说明期间有过暂停，放弃启动。pauseAll 会给所有任务取号，使「暂停全部」同样能
     * 打断正在进行的批量恢复。
     */
    private val pauseEpochs = ConcurrentHashMap<Int, AtomicLong>()

    /** 由任务集合派生 UI 列表快照；结构性变化（增删、状态切换）调用。 */
    fun emit() {
        _tasks.value = tasksById.values.sortedByDescending { it.id }
    }

    /** 纯进度刷新仅就地替换对应元素，避免每次 500ms 都全量重建列表。 */
    fun emitReplacing(task: DownloadTask) {
        val current = _tasks.value
        val index = current.indexOfFirst { it.id == task.id }
        if (index < 0) {
            emit()
            return
        }
        if (current[index] === task) return
        _tasks.value = current.toMutableList().also { it[index] = task }
    }

    fun nextId(): Int {
        while (true) {
            val candidate = idCounter.incrementAndGet()
            if (candidate < reservedIdFloor) {
                idCounter.updateAndGet { if (it < reservedIdFloor) reservedIdFloor else it }
                continue
            }
            return candidate
        }
    }

    fun resetCounter(maxId: Int) {
        idCounter.set(maxId)
    }

    fun adoptCeiling(maxId: Int) {
        if (maxId <= 0) return
        while (true) {
            val current = idCounter.get()
            if (current >= maxId) return
            if (idCounter.compareAndSet(current, maxId)) {
                AppLogger.log("DownloadManager", "已采纳外部下载 id 上界: $maxId")
                return
            }
        }
    }

    fun adoptFloor(minId: Int) {
        if (minId <= 0) return
        if (minId > reservedIdFloor) reservedIdFloor = minId
    }

    fun bumpPauseEpoch(taskId: Int): Long =
        pauseEpochs.computeIfAbsent(taskId) { AtomicLong() }.incrementAndGet()

    fun pauseEpochOf(taskId: Int): Long = pauseEpochs[taskId]?.get() ?: 0L

    fun forgetPauseEpoch(taskId: Int) {
        pauseEpochs.remove(taskId)
    }

    fun clearPauseEpochs() {
        pauseEpochs.clear()
    }
}