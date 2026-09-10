package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.jsoup.Jsoup

class VideoListParserTest {

    private lateinit var parser: VideoListParser
    private val baseUrl = "https://hanime1.me"

    private val fullContainerHtml = """
        <div class="video-item-container">
            <a class="video-link" href="/watch?v=12345"></a>
            <img class="main-thumb" src="https://cdn.example.com/thumb/12345.jpg"/>
            <div class="title">Test Video Title</div>
            <div class="duration">10:30</div>
            <div class="stats-container">
                <div class="stat-item">95%</div>
                <div class="stat-item">1,234</div>
            </div>
            <div class="subtitle"><a>TestAuthor • 2024-01-15</a></div>
        </div>
    """.trimIndent()

    @Before
    fun setUp() {
        parser = VideoListParser()
    }

    @Test
    fun `parseVideoList with single fully-populated container returns one video`() {
        val doc = Jsoup.parse(fullContainerHtml, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)

        assertEquals(1, videos.size)
        val video = videos[0]
        assertEquals("12345", video.id)
        assertEquals("Test Video Title", video.title)
        assertEquals("10:30", video.duration)
        assertEquals("95%", video.likeRate)
        assertEquals("1,234", video.viewCount)
        assertEquals("TestAuthor", video.author)
        assertEquals("2024-01-15", video.publishTime)
        assertEquals("https://hanime1.me/watch?v=12345", video.videoUrl)
        assertEquals("https://cdn.example.com/thumb/12345.jpg", video.thumbnailUrl)
    }

    @Test
    fun `parseVideoList with multiple containers returns all`() {
        val html = """
            <div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=1"></a>
                    <img class="main-thumb" src="https://cdn.example.com/thumb/1.jpg"/>
                    <div class="title">First</div>
                    <div class="duration">01:00</div>
                    <div class="stats-container">
                        <div class="stat-item">90%</div>
                        <div class="stat-item">100</div>
                    </div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=2"></a>
                    <img class="main-thumb" src="https://cdn.example.com/thumb/2.jpg"/>
                    <div class="title">Second</div>
                    <div class="duration">02:00</div>
                    <div class="stats-container">
                        <div class="stat-item">80%</div>
                        <div class="stat-item">200</div>
                    </div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=3"></a>
                    <img class="main-thumb" src="https://cdn.example.com/thumb/3.jpg"/>
                    <div class="title">Third</div>
                    <div class="duration">03:00</div>
                    <div class="stats-container">
                        <div class="stat-item">70%</div>
                        <div class="stat-item">300</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)

        val videos = parser.parseVideoList(doc, baseUrl)
        assertEquals(3, videos.size)
        assertEquals("1", videos[0].id)
        assertEquals("2", videos[1].id)
        assertEquals("3", videos[2].id)
    }

    // 「里番 / 泡面番」搜索结果页使用的简化卡片：无 .video-item-container，
    // 链接在卡片外层的 <a> 上，标题是 .home-rows-videos-title。
    private val simpleSearchCardsHtml = """
        <div class="home-rows-videos-wrapper" style="white-space: normal;">
            <a style="text-decoration: none;" href="https://www.hanime2.one/watch?v=407947">
                <div class="home-rows-videos-div search-videos hover-lighter">
                    <div class="video-card-inner">
                        <img loading="lazy" src="https://cdn.example.com/image/cover/407947.jpg?secure=aaa"/>
                        <div class="home-rows-videos-title"> 女友催眠 2 </div>
                    </div>
                </div>
            </a>
            <a style="text-decoration: none;" href="https://www.hanime2.one/watch?v=407946">
                <div class="home-rows-videos-div search-videos hover-lighter">
                    <div class="video-card-inner">
                        <img loading="lazy" src="https://cdn.example.com/image/cover/407946.jpg?secure=bbb"/>
                        <div class="home-rows-videos-title"> 女友催眠 1 </div>
                    </div>
                </div>
            </a>
        </div>
    """.trimIndent()

    @Test
    fun `parseVideoList parses simplified search cards used by 里番 and 泡面番`() {
        val doc = Jsoup.parse(simpleSearchCardsHtml, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)

        assertEquals(2, videos.size)
        assertEquals("407947", videos[0].id)
        assertEquals("女友催眠 2", videos[0].title)
        assertEquals("https://www.hanime2.one/watch?v=407947", videos[0].videoUrl)
        assertEquals("https://cdn.example.com/image/cover/407947.jpg?secure=aaa", videos[0].thumbnailUrl)
        assertEquals("407946", videos[1].id)
        assertEquals("女友催眠 1", videos[1].title)
    }

    @Test
    fun `parseVideoList with simplified card leaves absent fields empty`() {
        val doc = Jsoup.parse(simpleSearchCardsHtml, baseUrl)
        val video = parser.parseVideoList(doc, baseUrl).first()

        // 简化卡片没有 .duration / .stats-container / .subtitle，字段应保持为空而不是报错
        assertTrue("duration should be empty", video.duration.isEmpty())
        assertTrue("likeRate should be empty", video.likeRate.isEmpty())
        assertTrue("viewCount should be empty", video.viewCount.isEmpty())
        assertTrue("author should be empty", video.author.isEmpty())
        assertTrue("publishTime should be empty", video.publishTime.isEmpty())
    }

    @Test
    fun `parseVideoList dedupes same video present in both card layouts`() {
        val html = """
            <div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=12345"></a>
                    <img class="main-thumb" src="https://cdn.example.com/thumb/12345.jpg"/>
                    <div class="title">Full Card</div>
                    <div class="duration">10:30</div>
                </div>
                <a href="/watch?v=12345">
                    <div class="home-rows-videos-div search-videos">
                        <div class="video-card-inner">
                            <img src="https://cdn.example.com/simple/12345.jpg"/>
                            <div class="home-rows-videos-title">Simple Card</div>
                        </div>
                    </div>
                </a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)

        // 同一视频只保留一条，且优先保留信息更完整的常规卡片
        assertEquals(1, videos.size)
        assertEquals("Full Card", videos[0].title)
        assertEquals("10:30", videos[0].duration)
    }

    @Test
    fun `parseSimpleVideoCard with non-watch parent link returns null`() {
        val html = """
            <a href="/other?v=1">
                <div class="home-rows-videos-div">
                    <div class="home-rows-videos-title">Not A Video</div>
                </div>
            </a>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val card = doc.selectFirst(".home-rows-videos-div")!!
        assertNull(parser.parseSimpleVideoCard(card, baseUrl))
    }

    @Test
    fun `parseVideoList with empty document returns empty list`() {
        val doc = Jsoup.parse("<html><body></body></html>", baseUrl)
        assertTrue(parser.parseVideoList(doc, baseUrl).isEmpty())
    }

    @Test
    fun `parseVideoList with container missing video-link returns empty`() {
        val html = """
            <div class="video-item-container">
                <img class="main-thumb" src="https://cdn.example.com/thumb/12345.jpg"/>
                <div class="title">No Link</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertTrue(parser.parseVideoList(doc, baseUrl).isEmpty())
    }

    @Test
    fun `parseVideoList with container missing title returns empty`() {
        val html = """
            <div class="video-item-container">
                <a class="video-link" href="/watch?v=12345"></a>
                <img class="main-thumb" src="https://cdn.example.com/thumb/12345.jpg"/>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertTrue(parser.parseVideoList(doc, baseUrl).isEmpty())
    }

    @Test
    fun `parseVideoList with URL missing v param returns empty`() {
        val html = """
            <div class="video-item-container">
                <a class="video-link" href="/watch?id=abc"></a>
                <div class="title">Bad URL</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertTrue(parser.parseVideoList(doc, baseUrl).isEmpty())
    }

    @Test
    fun `parseVideoList uses placeholder thumbnail when main-thumb missing`() {
        val html = """
            <div class="video-item-container">
                <a class="video-link" href="/watch?v=12345"></a>
                <div class="title">No Thumb</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)
        assertEquals(1, videos.size)
        assertEquals(
            "https://vdownload.hembed.com/image/thumbnail/12345l.jpg",
            videos[0].thumbnailUrl
        )
    }

    @Test
    fun `parseVideoList with thumb-container fallback when video-link missing`() {
        val html = """
            <div class="video-item-container">
                <div class="thumb-container">
                    <a href="/watch?v=99999"></a>
                </div>
                <div class="title">Fallback Link</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)
        assertEquals(1, videos.size)
        assertEquals("99999", videos[0].id)
    }

    @Test
    fun `parseVideoList with stats-container splits percent and view count`() {
        val html = """
            <div class="video-item-container">
                <a class="video-link" href="/watch?v=12345"></a>
                <div class="title">Stats</div>
                <div class="stats-container">
                    <div class="stat-item">👍88%</div>
                    <div class="stat-item">5,678 views</div>
                </div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)
        assertEquals(1, videos.size)
        // % 文本会经过 cleanLikeRate 处理
        assertEquals("88%", videos[0].likeRate)
        assertEquals("5,678 views", videos[0].viewCount)
    }

    @Test
    fun `parseVideoList with subtitle without bullet sets only author`() {
        val html = """
            <div class="video-item-container">
                <a class="video-link" href="/watch?v=12345"></a>
                <div class="title">No Bullet</div>
                <div class="subtitle"><a>SoloAuthor</a></div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseVideoList(doc, baseUrl)
        assertEquals(1, videos.size)
        assertEquals("SoloAuthor", videos[0].author)
        assertTrue("publishTime should be empty", videos[0].publishTime.isEmpty())
    }

    @Test
    fun `parseSectionVideos finds videos under matching title`() {
        val html = """
            <div>
                <a class="horizontal-row-title" href="/sort/latest">
                    <h3>最新上市</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=100"></a>
                        <img class="main-thumb" src="https://cdn.example.com/100.jpg"/>
                        <div class="title">Video 100</div>
                    </div>
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=200"></a>
                        <img class="main-thumb" src="https://cdn.example.com/200.jpg"/>
                        <div class="title">Video 200</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseSectionVideos(doc, baseUrl, "最新上市")
        assertEquals(2, videos.size)
        assertEquals("100", videos[0].id)
        assertEquals("200", videos[1].id)
    }

    @Test
    fun `parseSectionVideos with non-matching title returns empty`() {
        val html = """
            <div>
                <a class="horizontal-row-title"><h3>他們在看</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=100"></a>
                        <div class="title">X</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertTrue(parser.parseSectionVideos(doc, baseUrl, "最新上市").isEmpty())
    }

    @Test
    fun `parseVideoItem parses container with all fields`() {
        val html = """
            <div class="video-item-container">
                <a class="video-link" href="/watch?v=555"></a>
                <img class="main-thumb" src="https://cdn.example.com/555.jpg"/>
                <div class="duration">05:15</div>
                <div class="stats-container">
                    <div class="stat-item">99%</div>
                    <div class="stat-item">999</div>
                </div>
                <div class="title">Item Title</div>
                <div class="subtitle"><a>ItemAuthor</a></div>
                <div class="subtitle-time">• 2024-02-20</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val item = doc.selectFirst(".video-item-container")!!
        val video = parser.parseVideoItem(item, baseUrl)

        assertEquals("555", video!!.id)
        assertEquals("Item Title", video.title)
        assertEquals("05:15", video.duration)
        assertEquals("99%", video.likeRate)
        assertEquals("999", video.viewCount)
        assertEquals("ItemAuthor", video.author)
        assertEquals("2024-02-20", video.publishTime)
    }

    @Test
    fun `parseUserTabVideoItem parses minimal tab item`() {
        val html = """
            <div class="user-tab-item-wrapper">
                <a href="/watch?v=88888">
                    <img class="main-thumb" src="https://cdn.example.com/88888.jpg"/>
                    <div class="duration">22:22</div>
                    <div class="title">Tab Title</div>
                </a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val item = doc.selectFirst(".user-tab-item-wrapper")!!
        val video = parser.parseUserTabVideoItem(item, baseUrl)

        assertEquals("88888", video!!.id)
        assertEquals("Tab Title", video.title)
        assertEquals("22:22", video.duration)
        assertEquals("https://cdn.example.com/88888.jpg", video.thumbnailUrl)
    }

    @Test
    fun `parseUserTabVideoItem with missing watch link returns null`() {
        val html = """
            <div class="user-tab-item-wrapper">
                <a href="/other?v=88888">
                    <div class="title">No Watch</div>
                </a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val item = doc.selectFirst(".user-tab-item-wrapper")!!
        assertNull(parser.parseUserTabVideoItem(item, baseUrl))
    }

    @Test
    fun `parseAuthorVideos collects all video-item-container`() {
        val html = """
            <div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=1"></a>
                    <div class="title">A</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=2"></a>
                    <div class="title">B</div>
                </div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val videos = parser.parseAuthorVideos(doc, baseUrl)
        assertEquals(2, videos.size)
    }
}
