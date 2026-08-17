package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomePageParserTest {

    private lateinit var parser: HomePageParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = HomePageParser(VideoListParser())
    }

    @Test
    fun `parse with sections returns HomePageData with sections`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title" href="/sort/latest">
                    <h3>最新上市</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=100"></a>
                        <img class="main-thumb" src="https://cdn.example.com/100.jpg"/>
                        <div class="title">First</div>
                    </div>
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=200"></a>
                        <img class="main-thumb" src="https://cdn.example.com/200.jpg"/>
                        <div class="title">Second</div>
                    </div>
                </div>
                <a class="horizontal-row-title" href="/sort/uploaded">
                    <h3>最新上傳</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=300"></a>
                        <img class="main-thumb" src="https://cdn.example.com/300.jpg"/>
                        <div class="title">Third</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()

        val data = parser.parse(html, baseUrl)

        assertEquals(2, data.sections.size)
        // 繁体「最新上傳」应被转换为简体「最新上传」
        assertEquals("最新上市", data.sections[0].title)
        assertEquals("最新上传", data.sections[1].title)
        assertEquals("$baseUrl/sort/latest", data.sections[0].moreUrl)
        assertEquals("$baseUrl/sort/uploaded", data.sections[1].moreUrl)
        assertEquals(2, data.sections[0].videos.size)
        assertEquals(1, data.sections[1].videos.size)
        assertEquals("100", data.sections[0].videos[0].id)
        assertEquals("300", data.sections[1].videos[0].id)
    }

    @Test
    fun `parse falls back to flat video list when no horizontal-row-title`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=1"></a>
                    <img class="main-thumb" src="https://cdn.example.com/1.jpg"/>
                    <div class="title">V1</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=2"></a>
                    <img class="main-thumb" src="https://cdn.example.com/2.jpg"/>
                    <div class="title">V2</div>
                </div>
            </body></html>
        """.trimIndent()

        val data = parser.parse(html, baseUrl)
        // 回退策略：将前 8 个分到「最新上市」，剩余分到「最新上传」
        assertEquals(2, data.sections.size)
        assertEquals("最新上市", data.sections[0].title)
        assertEquals("最新上传", data.sections[1].title)
        assertEquals(2, data.sections[0].videos.size)
        assertEquals(0, data.sections[1].videos.size)
    }

    @Test
    fun `parse with empty page returns empty data`() {
        val data = parser.parse("<html><body></body></html>", baseUrl)
        assertNull(data.banner)
        assertTrue(data.sections.isEmpty())
    }

    @Test
    fun `parse extracts banner with title and meta`() {
        val html = """
            <html><body>
                <div id="home-banner-wrapper">
                    <h1>Featured Banner Video</h1>
                    <h4>BannerAuthor • 9999 views • 2024-03-01</h4>
                    <span style="border-radius: 4px;">Tag1</span>
                    <span style="border-radius: 4px;">Tag2</span>
                    <a class="home-banner-play-btn" href="/watch?v=99999">Play</a>
                </div>
                <div style="aspect-ratio: 16/9;">
                    <img src="https://cdn.example.com/banner.jpg"/>
                </div>
            </body></html>
        """.trimIndent()

        val data = parser.parse(html, baseUrl)
        val banner = data.banner
        assertNotNull(banner)
        assertEquals("Featured Banner Video", banner!!.title)
        assertEquals("BannerAuthor", banner.author)
        assertEquals("9999 views", banner.viewCount)
        assertEquals("2024-03-01", banner.publishTime)
        assertEquals(listOf("Tag1", "Tag2"), banner.tags)
        assertEquals("$baseUrl/watch?v=99999", banner.videoUrl)
        assertEquals("https://cdn.example.com/banner.jpg", banner.imageUrl)
    }

    @Test
    fun `parse returns null banner when wrapper missing`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3>最新上市</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=1"></a>
                        <div class="title">V1</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)
        assertNull(data.banner)
    }

    @Test
    fun `parse returns null banner when h1 missing`() {
        val html = """
            <html><body>
                <div id="home-banner-wrapper">
                    <h4>Only meta</h4>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)
        assertNull(data.banner)
    }

    @Test
    fun `parse skips section with empty title`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3></h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=1"></a>
                        <div class="title">V1</div>
                    </div>
                </div>
                <a class="horizontal-row-title" href="/sort/latest"><h3>最新上市</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=2"></a>
                        <div class="title">V2</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)
        assertEquals(1, data.sections.size)
        assertEquals("最新上市", data.sections[0].title)
    }

    @Test
    fun `parse skips section whose wrapper has only invalid containers and no later siblings`() {
        // wrapper 内无有效视频且后续兄弟节点也无可解析视频时，section 被跳过（最多向后扫描 5 个兄弟）
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3>最新上市</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <!-- 无 video-link 和 title，parseSingleVideoContainer 返回 null -->
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)
        // 无有效视频，section 被跳过
        assertTrue(data.sections.isEmpty())
    }

    @Test
    fun `parse with traditional chinese section title converts to simplified`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title" href="/x"><h3>他們在看</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=1"></a>
                        <div class="title">V1</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)
        assertEquals(1, data.sections.size)
        assertEquals("他们在看", data.sections[0].title)
    }

    @Test
    fun `parseHomeSections with h3 having nested elements uses ownText`() {
        // h3.ownText() 只取直接文本，忽略子元素
        val html = """
            <html><body>
                <a class="horizontal-row-title" href="/x">
                    <h3>最新上市 <small>更多</small></h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=1"></a>
                        <div class="title">V1</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)
        assertEquals(1, data.sections.size)
        // ownText 仅取 "最新上市" 部分（去除 <small>更多</small>）
        assertEquals("最新上市", data.sections[0].title)
    }
}
