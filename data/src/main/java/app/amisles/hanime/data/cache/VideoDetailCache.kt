package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.VideoDetail
import javax.inject.Inject
import javax.inject.Singleton

/** 详情解析结果的进程内缓存：只驻留内存，进程结束后自然清空，不落盘。 */
@Singleton
class VideoDetailCache @Inject constructor() {

    private val entries = object : LinkedHashMap<String, VideoDetail>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoDetail>): Boolean =
            size > MAX_ENTRIES
    }

    fun get(key: String): VideoDetail? = synchronized(entries) { entries[key] }

    fun put(key: String, detail: VideoDetail) {
        synchronized(entries) { entries[key] = detail }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    private companion object {
        const val MAX_ENTRIES = 12
    }
}
