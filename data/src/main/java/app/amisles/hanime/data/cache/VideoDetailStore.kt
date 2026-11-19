package app.amisles.hanime.data.cache

import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.repository.VideoDetailCacheRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频详情页的常驻缓存：内存 LRU + 一条目一文件。
 *
 * 落 filesDir 而不是 cacheDir：cacheDir 会被系统在存储紧张时整体清掉，「打开过一次就常驻」的预期会落空。
 * 条目上限由设置项决定，超出后按文件最后修改时间淘汰最久没被打开的条目。
 */
class VideoDetailStore(private val dir: File) : VideoDetailCacheRepository {

    private val memory = object : LinkedHashMap<String, VideoDetail>(MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoDetail>): Boolean =
            size > MEMORY_ENTRIES
    }

    // 已落盘条目数的近似值：-1 表示尚未统计。只在超过上限时才重新列目录并淘汰，
    // 否则每写一条都要列一次上千个文件。
    private var knownFileCount = -1

    // 落盘与淘汰串行化：并发打开两个详情页时，同一键的临时文件不能被两路写入交替覆盖
    private val fileLock = Any()

    suspend fun get(key: String): VideoDetail? = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[key] }?.let { return@withContext it }
        val file = fileFor(key)
        if (!file.isFile) return@withContext null
        val entry = runCatching { file.readText() }.getOrNull()?.let(VideoDetailCacheCodec::decodeEntry)
        if (entry == null || entry.key != key) {
            // 文件损坏或哈希撞上了别的键：删掉当未命中，别每次打开都白解析一遍
            runCatching { file.delete() }
            return@withContext null
        }
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        synchronized(memory) { memory[key] = entry.detail }
        entry.detail
    }

    suspend fun put(key: String, detail: VideoDetail, limit: Int) = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[key] = detail }
        synchronized(fileLock) {
            val target = fileFor(key)
            val ok = writeCacheFile(target, VideoDetailCacheCodec.encodeEntry(key, detail))
            if (!ok) AppLogger.logError("VideoDetailStore", "详情缓存写入失败: " + target.name)
            if (ok) {
                if (knownFileCount < 0) knownFileCount = detailFiles().size else knownFileCount++
                if (limit > 0 && knownFileCount > limit) trimToLocked(limit)
            }
        }
    }

    /** 用户调小上限后立即按新上限淘汰；不调用的话要等下一次写入才会收拢。 */
    override suspend fun trimTo(limit: Int) = withContext(Dispatchers.IO) {
        if (limit > 0) synchronized(fileLock) { trimToLocked(limit) }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            synchronized(memory) { memory.clear() }
            runCatching { dir.deleteRecursively() }
            knownFileCount = 0
        }
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) { detailFiles().size }

    private fun trimToLocked(limit: Int) {
        // 已由调用方持有 fileLock
        val files = detailFiles()
        if (files.size <= limit) {
            knownFileCount = files.size
            return
        }
        files.sortedBy { it.lastModified() }
            .take(files.size - limit)
            .forEach { runCatching { it.delete() } }
        // 磁盘上被淘汰的条目必须同步从内存层清掉，否则它们还能被读到，「上限」形同虚设
        synchronized(memory) { memory.clear() }
        knownFileCount = limit
    }

    private fun detailFiles(): List<File> =
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(SUFFIX) } ?: emptyList()

    private fun fileFor(key: String): File = File(dir, digest(key) + SUFFIX)

    private fun digest(key: String): String = MessageDigest.getInstance("MD5")
        .digest(key.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    companion object {
        const val DIR_NAME = "video_detail_cache"

        private const val MEMORY_ENTRIES = 24
        private const val SUFFIX = ".json"
    }
}
