package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.VideoDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VideoDetailCacheTest {

    private val cache = VideoDetailCache()

    private fun detail(title: String) = VideoDetail(
        title = title,
        posterUrl = "",
        videoSources = emptyList(),
        defaultSourceUrl = "",
        tags = emptyList(),
        releaseDate = "",
        fileSize = "",
        author = "",
        description = ""
    )

    @Test
    fun putThenGetReturnsSameDetail() {
        cache.put("1|100", detail("A"))

        assertEquals("A", cache.get("1|100")?.title)
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(cache.get("1|404"))
    }

    @Test
    fun capacityIsBoundedAndKeepsNewestEntry() {
        repeat(40) { cache.put("1|" + it, detail("v" + it)) }

        assertNull(cache.get("1|0"))
        assertNotNull(cache.get("1|39"))
    }

    @Test
    fun clearDropsEveryEntry() {
        cache.put("1|100", detail("A"))
        cache.clear()

        assertNull(cache.get("1|100"))
    }
}
