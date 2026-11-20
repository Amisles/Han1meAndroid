package app.amisles.hanime.data.download

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.DownloadStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载列表迁移包：zip（manifest.json + 各任务续传位图）的导入导出。
 *
 * 迁移只搬运续传位图，不搬运成品文件；导入后的任务一律落到 PAUSED，由用户确认后再继续。
 */
internal class DownloadMigrationArchive(
    private val store: DownloadTaskStore,
    private val persistence: DownloadPersistence,
    private val downloadDir: () -> File
) {

    suspend fun export(): ByteArray? = withContext(Dispatchers.IO) {
        val migratable = store.tasksById.values.filter {
            it.status != DownloadStatus.COMPLETED && File(it.filePath + PARTMAP_SUFFIX).exists()
        }
        if (migratable.isEmpty()) return@withContext null
        val manifest = DownloadMigrationCodec.encode(migratable) { true }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            migratable.forEach { task ->
                val partmap = File(task.filePath + PARTMAP_SUFFIX)
                if (!partmap.exists()) return@forEach
                zip.putNextEntry(ZipEntry(PARTMAP_DIR + partmap.name))
                partmap.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        AppLogger.log("DownloadManager", "导出迁移包: " + migratable.size + " 个任务")
        out.toByteArray()
    }

    suspend fun import(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val dir = downloadDir()
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (++entryCount > MAX_MIGRATION_ENTRY_COUNT) {
                    throw IllegalArgumentException("迁移包条目数量过多")
                }
                val remaining = MAX_MIGRATION_TOTAL_BYTES - totalBytes
                if (remaining <= 0L) throw IllegalArgumentException("迁移包解压后过大")
                val data = readEntryLimited(zip, remaining)
                totalBytes += data.size
                entries[entry.name] = data
            }
        }
        val manifest = entries[MANIFEST_ENTRY]?.let { String(it) }
            ?: return@withContext 0
        val migrated = DownloadMigrationCodec.decode(manifest)
        if (migrated.isEmpty()) return@withContext 0

        // 位图落盘是磁盘 I/O，必须先做完再进锁：持锁写文件会挡住主线程的暂停 / 取消
        val pending = migrated.mapNotNull { entry ->
            val newPath = File(dir, File(entry.filePath).name).absolutePath
            if (store.lock.withLock { store.tasksById.values.any { it.filePath == newPath } }) {
                return@mapNotNull null
            }
            val partmapBytes = entry.partmapName?.let { entries[PARTMAP_DIR + it] }
            if (partmapBytes != null) {
                runCatching { File(newPath + PARTMAP_SUFFIX).writeBytes(partmapBytes) }
            }
            entry to newPath
        }
        if (pending.isEmpty()) return@withContext 0

        var imported = 0
        store.lock.withLock {
            pending.forEach { (entry, newPath) ->
                // 写盘期间可能有同路径任务被创建，入锁后再确认一次
                if (store.tasksById.values.any { it.filePath == newPath }) return@forEach
                val id = store.nextId()
                val task = DownloadMigrationCodec.toTask(entry, newPath).copy(id = id)
                store.tasksById[id] = task
                persistence.enqueue(task)
                imported++
            }
            if (imported > 0) store.emit()
        }
        AppLogger.log("DownloadManager", "导入迁移包: 成功 " + imported + "/" + migrated.size)
        imported
    }

    private fun readEntryLimited(zip: ZipInputStream, remainingBudget: Long): ByteArray {
        val limit = minOf(MAX_MIGRATION_ENTRY_BYTES.toLong(), remainingBudget)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IllegalArgumentException("迁移包条目过大")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
