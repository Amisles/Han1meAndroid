package app.amisles.hanime.data.download

import android.content.Context
import android.database.sqlite.SQLiteException
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.domain.model.DownloadGateState
import app.amisles.hanime.domain.model.DownloadStatus
import app.amisles.hanime.domain.model.DownloadStorageInfo
import app.amisles.hanime.domain.model.DownloadTask
import app.amisles.hanime.domain.model.StartDownloadResult
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.DownloadSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 下载子系统的对外门面：只做组件装配与仓储接口转发。
 *
 * 任务状态、持久化、调度、续传判定、闸门监听、迁移与存储维护分别由下列协作者持有，
 * 本类不保存任务集合或传输状态，避免再次演变成承担全部职责的单体。
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext context: Context,
    private val downloadDao: DownloadDao,
    settings: DownloadSettings
) : DownloadRepository {

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)

    private val store = DownloadTaskStore()
    private val persistence = DownloadPersistence(downloadDao, scope)
    private val bridge = DownloadServiceBridge(context)
    private val updater = DownloadTaskUpdater(store, persistence, bridge)
    private val calls = DownloadCallTracker()
    private val resolver = DownloadResumeResolver(context)
    private val budget = DownloadConcurrencyBudget(settings.maxDownloadConcurrent)
    private val host = DefaultDownloadTransferHost(context, store, calls, updater, resolver, budget)
    private val gate = DownloadGateController(context, settings, scope)
    private val maintenance = DownloadStorageMaintenance(context, store, persistence, settings)
    private val scheduler = DownloadQueueScheduler(
        store,
        budget,
        calls,
        updater,
        resolver,
        host,
        gate,
        settings,
        scope,
        scopeJob
    )
    private val session = DownloadSessionController(
        store,
        persistence,
        scheduler,
        resolver,
        updater,
        calls,
        gate,
        maintenance,
        scope
    )
    private val migration = DownloadMigrationArchive(store, persistence) {
        maintenance.resolveDownloadDir()
    }
    private val restorer = DownloadRestorer(
        downloadDao,
        store,
        persistence,
        maintenance,
        updater,
        session,
        scheduler,
        gate
    )

    override val tasks: StateFlow<List<DownloadTask>> = store.tasks
    override val gateState: StateFlow<DownloadGateState> = gate.state

    init {
        persistence.start()

        scope.launch {
            settings.maxDownloadConcurrentFlow.collect { scheduler.updateConcurrencyLimit(it) }
        }

        scope.launch {
            settings.downloadRateLimitFlow.collect { megabytesPerSecond ->
                GlobalDownloadRateLimiter.instance.setLimit(megabytesPerSecond.toLong() * 1024L * 1024L)
            }
        }

        gate.start(
            onOpen = { scheduler.startNextPendingTask() },
            onNetworkLost = { session.pauseForNetworkLoss() },
            onNetworkAvailable = { session.resumeAfterNetwork() }
        )

        scope.launch { restorer.restore() }
    }

    override fun startDownload(
        title: String,
        quality: String,
        url: String,
        thumbnailUrl: String,
        videoId: String
    ): StartDownloadResult = session.startDownload(title, quality, url, thumbnailUrl, videoId)

    override fun pauseDownload(taskId: Int) = session.pauseDownload(taskId)

    override fun resumeDownload(taskId: Int) = session.resumeDownload(taskId)

    override fun cancelDownload(taskId: Int) = session.cancelDownload(taskId)

    override fun retryAllFailed() = session.retryAllFailed()

    override fun pauseAll() = session.pauseAll()

    override fun pauseAllForQuota() = session.pauseAllForQuota()

    override fun resumeAll() = session.resumeAll()

    override fun moveToFront(taskId: Int) = scheduler.moveToFront(taskId)

    override fun reevaluateGate() = gate.reevaluate()

    override fun pendingCount(): Int = session.pendingCount()

    override fun adoptDownloadIdCeiling(maxId: Int) = store.adoptCeiling(maxId)

    override fun adoptDownloadIdFloor(minId: Int) = store.adoptFloor(minId)

    override fun getCompletedDownloadCountFlow(): Flow<Int> = downloadDao.observeCompletedCount()

    override suspend fun getCompletedDownloadCount(): Int = try {
        downloadDao.getCompletedCount()
    } catch (e: SQLiteException) {
        store.tasksById.values.count { it.status == DownloadStatus.COMPLETED }
    }

    override fun isVideoDownloaded(videoId: String, quality: String): Boolean =
        if (videoId.isBlank()) {
            false
        } else {
            store.tasksById.values.any {
                it.videoId == videoId && it.status == DownloadStatus.COMPLETED &&
                    (quality.isBlank() || it.quality == quality)
            }
        }

    override fun isVideoDownloading(videoId: String, quality: String): Boolean =
        if (videoId.isBlank()) {
            false
        } else {
            store.tasksById.values.any {
                it.videoId == videoId &&
                    (quality.isBlank() || it.quality == quality) &&
                    (it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.PENDING ||
                        it.status == DownloadStatus.PAUSED)
            }
        }

    override fun getDownloadStatus(videoId: String): DownloadStatus? =
        if (videoId.isBlank()) {
            null
        } else {
            store.tasksById.values.find { it.videoId == videoId }?.status
        }

    override suspend fun getStorageInfo(): DownloadStorageInfo = maintenance.storageInfo()

    override suspend fun clearOrphanPartmaps(): Long = maintenance.clearOrphanPartmaps()

    override suspend fun rescanExistingDownloads(): Int = maintenance.rescanExistingDownloads()

    override suspend fun exportMigration(): ByteArray? = migration.export()

    override suspend fun importMigration(bytes: ByteArray): Int = migration.import(bytes)

    override suspend fun suspendForBackupImport() = session.suspendForBackupImport()

    override suspend fun reloadTasksFromDatabase() = restorer.reload()

    /**
     * 显式生命周期出口。单例随进程存在是预期行为，但提供结构化取消入口：进程退出、测试拆卸、
     * 或需要强制中断所有在途下载时调用，保证协程与在途请求被确定性回收。
     * scope 根使用 SupervisorJob，单个下载任务的异常不会株连其它在途任务。
     */
    fun shutdown() {
        gate.shutdown()
        scopeJob.cancel()
        scheduler.clearJobs()
        calls.reset()
        updater.clearTransient()
        budget.reset()
        AppLogger.log("DownloadManager", "shutdown: 已取消下载作用域并清理在途状态")
    }
}