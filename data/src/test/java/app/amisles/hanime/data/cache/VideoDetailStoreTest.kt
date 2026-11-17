package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.VideoDetail
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDetailStoreTest {

    private val dir: File = Files.createTempDirectory("video-detail-store").toFile()

    private fun detail(title: String) = VideoDetail(
        title = title,
        posterUrl = "",
        videoSources = emptyList(),
        defaultSourceUrl = "https://cdn.example.com/v.mp4",
        tags = emptyList(),
        releaseDate = "",
        fileSize = "",
        author = "",
        description = ""
    )

    private fun put(store: VideoDetailStore, key: String, title: String, limit: Int = 1000) = runBlocking {
        store.put(key, detail(title), limit)
        // 淘汰按文件修改时间排序，同一毫秒内的多次写入无法区分先后
        Thread.sleep(20)
    }

    @Test
    fun `detail survives a new store instance`() = runBlocking {
        val first = VideoDetailStore(dir)
        first.put("99|42", detail("标题"), 1000)

        // 新实例没有内存层，能读到说明真的落在磁盘上
        val second = VideoDetailStore(dir)

        assertEquals("标题", second.get("99|42")?.title)
    }

    @Test
    fun `missing key reads as null`() = runBlocking {
        assertNull(VideoDetailStore(dir).get("99|404"))
    }

    @Test
    fun `exceeding the limit evicts the oldest entry`() {
        val store = VideoDetailStore(dir)
        repeat(5) { put(store, "99|" + it, "v" + it, limit = 3) }

        runBlocking {
            assertNull(store.get("99|0"))
            assertNull(store.get("99|1"))
            assertEquals("v4", store.get("99|4")?.title)
        }
    }

    @Test
    fun `trimTo applies a lowered limit immediately`() {
        val store = VideoDetailStore(dir)
        repeat(4) { put(store, "99|" + it, "v" + it, limit = 10) }

        runBlocking {
            store.trimTo(2)

            assertEquals(2, store.count())
            assertEquals("v3", store.get("99|3")?.title)
        }
    }

    @Test
    fun `corrupted file is dropped instead of returned`() = runBlocking {
        val store = VideoDetailStore(dir)
        store.put("99|42", detail("标题"), 1000)
        dir.listFiles()?.filter { it.isFile }?.forEach { it.writeText("broken") }

        assertNull(VideoDetailStore(dir).get("99|42"))
    }

    @Test
    fun `clear drops memory and disk entries`() = runBlocking {
        val store = VideoDetailStore(dir)
        store.put("99|42", detail("标题"), 1000)

        store.clear()

        assertNull(store.get("99|42"))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
