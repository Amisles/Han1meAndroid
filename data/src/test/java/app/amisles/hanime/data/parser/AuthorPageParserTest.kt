package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthorPageParserTest {

    private lateinit var parser: AuthorPageParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = AuthorPageParser(
            videoListParser = VideoListParser(),
            playlistParser = PlaylistParser(),
            searchPageParser = SearchPageParser(VideoListParser())
        )
    }

    @Test
    fun `parse extracts author profile and video sections`() {
        val html = """
            <html><body>
                <div class="profile-display-name">TestAuthor</div>
                <div class="profile-avatar-wrapper">
                    <img src="https://cdn.example.com/avatar.jpg"/>
                </div>
                <div class="profile-sub-stats-id">@12345</div>
                <div class="profile-sub-stats-new-line">100 位訂閱者 • 50 部影片</div>

                <a class="horizontal-row-title" href="/user/12345/videos">
                    <h3>影片</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=1"></a>
                        <img class="main-thumb" src="https://cdn.example.com/1.jpg"/>
                        <div class="duration">01:00</div>
                        <div class="stats-container">
                            <div class="stat-item">90%</div>
                            <div class="stat-item">100</div>
                        </div>
                        <div class="title">Video 1</div>
                        <div class="subtitle"><a>TestAuthor</a></div>
                        <div class="subtitle-time">• 2024-01-01</div>
                    </div>
                    <div class="video-item-container">
                        <a class="video-link" href="/watch?v=2"></a>
                        <img class="main-thumb" src="https://cdn.example.com/2.jpg"/>
                        <div class="duration">02:00</div>
                        <div class="stats-container">
                            <div class="stat-item">80%</div>
                            <div class="stat-item">200</div>
                        </div>
                        <div class="title">Video 2</div>
                        <div class="subtitle"><a>TestAuthor</a></div>
                        <div class="subtitle-time">• 2024-02-01</div>
                    </div>
                </div>

                <a class="horizontal-row-title" href="/user/12345/playlists">
                    <h3>播放清單</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/10"></a>
                        <img class="main-thumb" src="https://cdn.example.com/pl10.jpg"/>
                        <div class="title">My Playlist 1</div>
                        <div class="subtitle"><a>TestAuthor</a></div>
                        <div class="subtitle-time">• 2024-03-01</div>
                        <div class="stats-container">
                            <div class="stat-item">5</div>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()

        val data = parser.parse(html, baseUrl)!!

        assertEquals("12345", data.authorId)
        assertEquals("TestAuthor", data.authorName)
        assertEquals("https://cdn.example.com/avatar.jpg", data.authorAvatarUrl)
        assertEquals("100", data.subscriberCount)
        assertEquals("50", data.videoCount)
        assertEquals(2, data.videos.size)
        assertEquals("1", data.videos[0].id)
        assertEquals("2", data.videos[1].id)
        assertEquals(1, data.playlists.size)
        assertEquals("My Playlist 1", data.playlists[0].title)
        assertEquals("$baseUrl/playlist/10", data.playlists[0].playlistUrl)
        assertEquals("$baseUrl/user/12345/videos", data.uploadedPageUrl)
        assertEquals("$baseUrl/user/12345/playlists", data.playlistsPageUrl)
    }

    @Test
    fun `parse strips @ prefix from author id`() {
        val html = """
            <html><body>
                <div class="profile-display-name">A</div>
                <div class="profile-avatar-wrapper"><img src="x.jpg"/></div>
                <div class="profile-sub-stats-id">@99999</div>
                <div class="profile-sub-stats-new-line"></div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)!!
        assertEquals("99999", data.authorId)
    }

    @Test
    fun `parse with simplified chinese stats parses subscriber and video counts`() {
        val html = """
            <html><body>
                <div class="profile-display-name">A</div>
                <div class="profile-avatar-wrapper"><img src="x.jpg"/></div>
                <div class="profile-sub-stats-id">@1</div>
                <div class="profile-sub-stats-new-line">200 位订阅者 • 80 个视频</div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)!!
        assertEquals("200", data.subscriberCount)
        assertEquals("80", data.videoCount)
    }

    @Test
    fun `parse with subscriber stats without separator still extracts counts`() {
        val html = """
            <html><body>
                <div class="profile-display-name">A</div>
                <div class="profile-avatar-wrapper"><img src="x.jpg"/></div>
                <div class="profile-sub-stats-id">@1</div>
                <div class="profile-sub-stats-new-line">500位訂閱者 30部影片</div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)!!
        assertEquals("500", data.subscriberCount)
        assertEquals("30", data.videoCount)
    }

    @Test
    fun `parse with empty stats returns empty counts`() {
        val html = """
            <html><body>
                <div class="profile-display-name">A</div>
                <div class="profile-avatar-wrapper"><img src="x.jpg"/></div>
                <div class="profile-sub-stats-id">@1</div>
                <div class="profile-sub-stats-new-line"></div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)!!
        assertEquals("", data.subscriberCount)
        assertEquals("", data.videoCount)
    }

    @Test
    fun `parse with comma-formatted counts extracts full number`() {
        val html = """
            <html><body>
                <div class="profile-display-name">A</div>
                <div class="profile-avatar-wrapper"><img src="x.jpg"/></div>
                <div class="profile-sub-stats-id">@1</div>
                <div class="profile-sub-stats-new-line">1,234 位訂閱者 • 567 部影片</div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)!!
        assertEquals("1,234", data.subscriberCount)
        assertEquals("567", data.videoCount)
    }

    @Test
    fun `parse with empty page returns object with empty fields`() {
        val data = parser.parse("<html><body></body></html>", baseUrl)
        assertNotNull(data)
        assertEquals("", data!!.authorName)
        assertEquals("", data.authorId)
        assertTrue(data.videos.isEmpty())
        assertTrue(data.playlists.isEmpty())
    }

    @Test
    fun `parseVideoListPage extracts all video containers`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=1"></a>
                    <img class="main-thumb" src="https://cdn.example.com/1.jpg"/>
                    <div class="duration">01:00</div>
                    <div class="stats-container">
                        <div class="stat-item">90%</div>
                        <div class="stat-item">100</div>
                    </div>
                    <div class="title">V1</div>
                    <div class="subtitle"><a>A</a></div>
                    <div class="subtitle-time">• 2024-01-01</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=2"></a>
                    <img class="main-thumb" src="https://cdn.example.com/2.jpg"/>
                    <div class="duration">02:00</div>
                    <div class="stats-container">
                        <div class="stat-item">80%</div>
                        <div class="stat-item">200</div>
                    </div>
                    <div class="title">V2</div>
                    <div class="subtitle"><a>A</a></div>
                    <div class="subtitle-time">• 2024-02-01</div>
                </div>
            </body></html>
        """.trimIndent()
        val videos = parser.parseVideoListPage(html, baseUrl)
        assertEquals(2, videos.size)
        assertEquals("1", videos[0].id)
        assertEquals("2", videos[1].id)
    }

    @Test
    fun `parseUserVideoList parses user-tab-item-wrapper items`() {
        val html = """
            <html><body>
                <div class="profile-display-name">TabAuthor</div>
                <div class="user-tab-item-wrapper">
                    <a href="/watch?v=111">
                        <img class="main-thumb" src="https://cdn.example.com/111.jpg"/>
                        <div class="duration">10:00</div>
                        <div class="title">Tab Video 1</div>
                    </a>
                </div>
                <div class="user-tab-item-wrapper">
                    <a href="/watch?v=222">
                        <img class="main-thumb" src="https://cdn.example.com/222.jpg"/>
                        <div class="duration">20:00</div>
                        <div class="title">Tab Video 2</div>
                    </a>
                </div>
                <ul class="pagination">
                    <li class="page-item active"><span class="page-link">1</span></li>
                    <li class="page-item"><a class="page-link" href="?page=2">2</a></li>
                    <li class="page-item"><a class="page-link" rel="next" href="?page=2">»</a></li>
                </ul>
            </body></html>
        """.trimIndent()
        val result = parser.parseUserVideoList(html, "$baseUrl/user/555")
        assertEquals(2, result.videos.size)
        assertEquals("111", result.videos[0].id)
        assertEquals("222", result.videos[1].id)
        assertEquals("TabAuthor", result.authorName)
        assertEquals("555", result.authorId)
        assertEquals(1, result.currentPage)
        assertEquals(2, result.totalPages)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `parseUserVideoList falls back to video-item-container when no user-tab-item-wrapper`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=333"></a>
                    <img class="main-thumb" src="https://cdn.example.com/333.jpg"/>
                    <div class="duration">05:00</div>
                    <div class="stats-container">
                        <div class="stat-item">90%</div>
                        <div class="stat-item">100</div>
                    </div>
                    <div class="title">Fallback Video</div>
                    <div class="subtitle"><a>A</a></div>
                    <div class="subtitle-time">• 2024-01-01</div>
                </div>
            </body></html>
        """.trimIndent()
        val result = parser.parseUserVideoList(html, "$baseUrl/user/777")
        assertEquals(1, result.videos.size)
        assertEquals("333", result.videos[0].id)
        assertEquals("777", result.authorId)
    }

    @Test
    fun `parseUserVideoList with empty page returns empty videos`() {
        val html = "<html><body></body></html>"
        val result = parser.parseUserVideoList(html, "$baseUrl/user/1")
        assertTrue(result.videos.isEmpty())
        assertEquals(1, result.currentPage)
        assertEquals(1, result.totalPages)
    }

    @Test
    fun `parseUserVideoList without numeric user id in URL returns empty authorId`() {
        val html = "<html><body></body></html>"
        val result = parser.parseUserVideoList(html, "$baseUrl/user/abc")
        assertEquals("", result.authorId)
    }

    @Test
    fun `parse with playlist section matching both simplified and traditional chinese`() {
        val html = """
            <html><body>
                <div class="profile-display-name">A</div>
                <div class="profile-sub-stats-id">@1</div>
                <div class="profile-sub-stats-new-line"></div>
                <a class="horizontal-row-title" href="/user/1/playlists">
                    <h3>播放清单</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/20"></a>
                        <img class="main-thumb" src="https://cdn.example.com/pl20.jpg"/>
                        <div class="title">Simplified Playlist</div>
                        <div class="subtitle"><a>A</a></div>
                        <div class="subtitle-time">• 2024-04-01</div>
                        <div class="stats-container">
                            <div class="stat-item">8</div>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val data = parser.parse(html, baseUrl)!!
        // 简体「播放清单」应被识别
        assertEquals(1, data.playlists.size)
        assertEquals("Simplified Playlist", data.playlists[0].title)
        assertEquals("8", data.playlists[0].videoCount)
    }
}
