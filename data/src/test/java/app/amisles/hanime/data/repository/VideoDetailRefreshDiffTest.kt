package app.amisles.hanime.data.repository

import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.domain.model.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 刷新结果与缓存合并的差异判定。
 *
 * 锁住的语义：直链每次抓页都会换签名，这类差异不能推到界面上（会让播放器重载回片头）；
 * 真正的内容变化要能被识别出来。
 */
class VideoDetailRefreshDiffTest {

    private fun detail(
        title: String = "标题",
        sources: List<Pair<String, Int>> = listOf("https://cdn/a-signed-1.mp4" to 720),
        description: String = "简介"
    ) = VideoDetail(
        title = title,
        posterUrl = "",
        videoSources = sources.map { (url, size) ->
            VideoSource(url = url, resolution = "${size}p", size = size, mimeType = "video/mp4")
        },
        defaultSourceUrl = sources.first().first,
        tags = emptyList(),
        releaseDate = "",
        fileSize = "",
        author = "",
        description = description
    )

    @Test
    fun `only the signed url changed produces no diff`() {
        val cached = detail()
        val fresh = detail(sources = listOf("https://cdn/a-signed-2.mp4" to 720))

        assertEquals(cached, mergeRefreshedVideoDetail(cached, fresh))
    }

    @Test
    fun `content change is kept while the cached url survives`() {
        val cached = detail()
        val fresh = detail(title = "新标题", sources = listOf("https://cdn/a-signed-2.mp4" to 720))

        val merged = mergeRefreshedVideoDetail(cached, fresh)

        assertNotEquals(cached, merged)
        assertEquals("新标题", merged.title)
        assertEquals(cached.defaultSourceUrl, merged.defaultSourceUrl)
    }

    @Test
    fun `new quality set replaces the cached sources`() {
        val cached = detail()
        val fresh = detail(
            sources = listOf(
                "https://cdn/a-signed-2.mp4" to 720,
                "https://cdn/a-1080-signed-2.mp4" to 1080
            )
        )

        val merged = mergeRefreshedVideoDetail(cached, fresh)

        assertEquals(fresh.videoSources, merged.videoSources)
        assertEquals(fresh.defaultSourceUrl, merged.defaultSourceUrl)
    }

}
