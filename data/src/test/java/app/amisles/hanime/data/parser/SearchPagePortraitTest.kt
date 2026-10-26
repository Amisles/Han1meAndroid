package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchPagePortraitTest {

    private lateinit var parser: SearchPageParser

    @Before
    fun setUp() {
        parser = SearchPageParser(VideoListParser())
    }

    @Test
    fun `hentai genre results are marked as portrait covers`() {
        val html = """
            <html><body>
                <div class="home-rows-videos-wrapper">
                    <a href="https://www.hanime2.one/watch?v=115128">
                        <div class="home-rows-videos-div search-videos">
                            <div class="video-card-inner">
                                <img src="https://cdn.example.com/image/cover/115128.jpg"/>
                                <div class="home-rows-videos-title">里番作品</div>
                            </div>
                        </div>
                    </a>
                </div>
            </body></html>
        """.trimIndent()

        val videos = parser.parse(html, "https://www.hanime2.one")

        assertEquals(1, videos.size)
        assertTrue("里番搜索结果应为竖版封面", videos[0].isPortraitCover)
    }

    @Test
    fun `regular search results stay landscape`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=405188"></a>
                    <img class="main-thumb" src="https://cdn.example.com/thumb/405188.jpg"/>
                    <div class="title">普通作品</div>
                </div>
            </body></html>
        """.trimIndent()

        val videos = parser.parse(html, "https://hanime1.me")

        assertEquals(1, videos.size)
        assertFalse("非里番搜索结果应为横版", videos[0].isPortraitCover)
    }

    @Test
    fun `pagination parse keeps the portrait flag`() {
        val html = """
            <html><body>
                <div class="home-rows-videos-div">
                    <a href="https://www.hanime2.one/watch?v=23030">
                        <img src="https://cdn.example.com/cover/23030.jpg"/>
                        <div class="home-rows-videos-title">竖版作品</div>
                    </a>
                </div>
            </body></html>
        """.trimIndent()

        val result = parser.parseWithPagination(html, "https://www.hanime2.one")

        assertTrue(result.videos.isNotEmpty())
        assertTrue(result.videos[0].isPortraitCover)
    }
}
