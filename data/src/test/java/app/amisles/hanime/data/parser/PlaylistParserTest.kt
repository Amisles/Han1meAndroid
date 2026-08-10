package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.jsoup.Jsoup

class PlaylistParserTest {

    private lateinit var parser: PlaylistParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = PlaylistParser()
    }

    // ============ parse(doc, baseUrl) - 详情页内嵌的播放列表 ============

    @Test
    fun `parse embedded playlist extracts title author videoCount and videos`() {
        val html = """
            <html><body>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1">My Embedded Playlist</a></h4>
                        <div style="font-size: 12px;">
                            <a>PlaylistAuthor</a>
                            <span>5 videos</span>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=111">
                                <img class="main-thumb" src="https://cdn.example.com/111.jpg"/>
                                <div class="duration">01:00</div>
                            </a>
                            <div class="stats-container">
                                <div class="stat-item">90%</div>
                                <div class="stat-item">100</div>
                            </div>
                            <div class="video-title"><a>Video 1</a></div>
                            <div class="meta-author"><a>Author1</a></div>
                            <div class="meta-stats">100 • 2024-01-01</div>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=222">
                                <img class="main-thumb" src="https://cdn.example.com/222.jpg"/>
                                <div class="duration">02:00</div>
                            </a>
                            <div class="stats-container">
                                <div class="stat-item">80%</div>
                                <div class="stat-item">200</div>
                            </div>
                            <div class="video-title"><a>Video 2</a></div>
                            <div class="meta-author"><a>Author2</a></div>
                            <div class="meta-stats">200 • 2024-02-01</div>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlist = parser.parse(doc, baseUrl)!!

        assertEquals("My Embedded Playlist", playlist.title)
        assertEquals("PlaylistAuthor", playlist.author)
        assertEquals(5, playlist.videoCount)
        assertEquals(2, playlist.videos.size)
        assertEquals("111", playlist.videos[0].id)
        assertEquals("Video 1", playlist.videos[0].title)
        assertEquals("01:00", playlist.videos[0].duration)
        assertEquals("90%", playlist.videos[0].likeRate)
        assertEquals("100", playlist.videos[0].viewCount)
        assertEquals("Author1", playlist.videos[0].author)
        assertEquals("2024-01-01", playlist.videos[0].publishTime)
        assertEquals("$baseUrl/watch?v=111", playlist.videos[0].videoUrl)
    }

    @Test
    fun `parse embedded playlist returns null when wrapper missing`() {
        val doc = Jsoup.parse("<html><body></body></html>", baseUrl)
        assertNull(parser.parse(doc, baseUrl))
    }

    @Test
    fun `parse embedded playlist returns null when title empty`() {
        val html = """
            <html><body>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1"></a></h4>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=111">
                                <div class="video-title"><a>Video 1</a></div>
                            </a>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertNull(parser.parse(doc, baseUrl))
    }

    @Test
    fun `parse embedded playlist returns null when no videos`() {
        val html = """
            <html><body>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1">Title</a></h4>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertNull(parser.parse(doc, baseUrl))
    }

    @Test
    fun `parse embedded playlist skips items missing playlist-video-card`() {
        val html = """
            <html><body>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1">Title</a></h4>
                        <div style="font-size: 12px;">
                            <a>Author</a>
                            <span>1 videos</span>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <!-- 没有 playlist-video-card 子元素 -->
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=111">
                                <div class="video-title"><a>V1</a></div>
                            </a>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlist = parser.parse(doc, baseUrl)!!
        assertEquals(1, playlist.videos.size)
        assertEquals("111", playlist.videos[0].id)
    }

    @Test
    fun `parse embedded playlist skips items with empty video url`() {
        val html = """
            <html><body>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1">Title</a></h4>
                        <div style="font-size: 12px;">
                            <a>Author</a>
                            <span>2 videos</span>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/other?v=1">
                                <div class="video-title"><a>Bad</a></div>
                            </a>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=222">
                                <div class="video-title"><a>Good</a></div>
                            </a>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlist = parser.parse(doc, baseUrl)!!
        // 第一个 item 的 a[href*="watch?v="] 找不到（href 是 /other?v=1），跳过
        assertEquals(1, playlist.videos.size)
        assertEquals("222", playlist.videos[0].id)
    }

    @Test
    fun `parse embedded playlist with videoCount text containing non-digits extracts digits only`() {
        val html = """
            <html><body>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1">Title</a></h4>
                        <div style="font-size: 12px;">
                            <a>Author</a>
                            <span>共 12 部影片</span>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=1">
                                <div class="video-title"><a>V1</a></div>
                            </a>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlist = parser.parse(doc, baseUrl)!!
        // "共 12 部影片" → 提取首个数字 12
        assertEquals(12, playlist.videoCount)
    }

    // ============ parseListPage ============

    @Test
    fun `parseListPage extracts playlist summaries`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <a class="video-link" href="/playlist/1"></a>
                    <img class="main-thumb" src="https://cdn.example.com/pl1.jpg"/>
                    <div class="title">Playlist One</div>
                    <div class="subtitle"><a>AuthorA</a></div>
                    <div class="subtitle-time">• 2024-01-01</div>
                    <div class="stats-container">
                        <div class="stat-item">10</div>
                    </div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/playlist/2"></a>
                    <img class="main-thumb" src="https://cdn.example.com/pl2.jpg"/>
                    <div class="title">Playlist Two</div>
                    <div class="subtitle"><a>AuthorB</a></div>
                    <div class="subtitle-time">• 2024-02-01</div>
                    <div class="stats-container">
                        <div class="stat-item">5</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val playlists = parser.parseListPage(html, baseUrl)
        assertEquals(2, playlists.size)
        assertEquals("Playlist One", playlists[0].title)
        assertEquals("$baseUrl/playlist/1", playlists[0].playlistUrl)
        assertEquals("https://cdn.example.com/pl1.jpg", playlists[0].thumbnailUrl)
        assertEquals("AuthorA", playlists[0].author)
        assertEquals("2024-01-01", playlists[0].publishTime)
        assertEquals("10", playlists[0].videoCount)
    }

    @Test
    fun `parseListPage skips items missing video-link`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <img class="main-thumb" src="https://cdn.example.com/pl1.jpg"/>
                    <div class="title">No Link</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/playlist/2"></a>
                    <div class="title">Has Link</div>
                </div>
            </body></html>
        """.trimIndent()
        val playlists = parser.parseListPage(html, baseUrl)
        assertEquals(1, playlists.size)
        assertEquals("Has Link", playlists[0].title)
    }

    @Test
    fun `parseListPage with empty page returns empty list`() {
        assertTrue(parser.parseListPage("<html><body></body></html>", baseUrl).isEmpty())
    }

    // ============ parseDetailPage ============

    @Test
    fun `parseDetailPage extracts full playlist detail`() {
        val html = """
            <html><body>
                <h1 class="playlist-title">Detailed Playlist</h1>
                <img class="playlist-main-thumbnail" src="https://cdn.example.com/cover.jpg"/>
                <div class="playlist-author-info">
                    <a>DetailAuthor</a>
                    <img class="author-avatar" src="https://cdn.example.com/avatar.jpg"/>
                </div>
                <div class="playlist-stats">15 videos 觀看次數：1,234 次</div>
                <div class="playlist-description">A great playlist description.</div>
                <div class="playlist-video-card">
                    <a href="/watch?v=1">
                        <img class="main-thumb" src="https://cdn.example.com/1.jpg"/>
                        <div class="duration">01:00</div>
                    </a>
                    <div class="stats-container">
                        <div class="stat-item">95%</div>
                        <div class="stat-item">500</div>
                    </div>
                    <div class="video-title"><a>First Video</a></div>
                    <div class="meta-author"><a>DetailAuthor</a></div>
                    <div class="meta-stats"><span>2024-01-15</span></div>
                </div>
                <div class="playlist-video-card">
                    <a href="/watch?v=2">
                        <img class="main-thumb" src="https://cdn.example.com/2.jpg"/>
                        <div class="duration">02:00</div>
                    </a>
                    <div class="stats-container">
                        <div class="stat-item">85%</div>
                        <div class="stat-item">300</div>
                    </div>
                    <div class="video-title"><a>Second Video</a></div>
                    <div class="meta-author"><a>DetailAuthor</a></div>
                    <div class="meta-stats"><span>2024-02-20</span></div>
                </div>
            </body></html>
        """.trimIndent()

        val detail = parser.parseDetailPage(html, baseUrl)!!
        assertEquals("Detailed Playlist", detail.title)
        assertEquals("https://cdn.example.com/cover.jpg", detail.coverUrl)
        assertEquals("DetailAuthor", detail.author)
        assertEquals("https://cdn.example.com/avatar.jpg", detail.authorAvatarUrl)
        assertEquals(15, detail.videoCount)
        assertEquals("1,234", detail.viewCount)
        assertEquals("A great playlist description.", detail.description)
        assertEquals(2, detail.videos.size)
        assertEquals("1", detail.videos[0].id)
        assertEquals("First Video", detail.videos[0].title)
        assertEquals("95%", detail.videos[0].likeRate)
        assertEquals("500", detail.videos[0].viewCount)
        assertEquals("2024-01-15", detail.videos[0].publishTime)
    }

    @Test
    fun `parseDetailPage with missing view count returns 0`() {
        val html = """
            <html><body>
                <h1 class="playlist-title">No Views</h1>
                <div class="playlist-stats">10 videos</div>
            </body></html>
        """.trimIndent()
        val detail = parser.parseDetailPage(html, baseUrl)!!
        assertEquals(10, detail.videoCount)
        assertEquals("0", detail.viewCount)
    }

    @Test
    fun `parseDetailPage with missing stats returns 0 videoCount`() {
        val html = """
            <html><body>
                <h1 class="playlist-title">No Stats</h1>
            </body></html>
        """.trimIndent()
        val detail = parser.parseDetailPage(html, baseUrl)!!
        assertEquals(0, detail.videoCount)
    }

    @Test
    fun `parseDetailPage with empty page still returns object`() {
        val detail = parser.parseDetailPage("<html><body></body></html>", baseUrl)
        assertNotNull(detail)
        assertEquals("", detail!!.title)
        assertTrue(detail.videos.isEmpty())
    }

    // ============ parseSectionPlaylists ============

    @Test
    fun `parseSectionPlaylists finds playlists under matching title`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title" href="/user/1/playlists">
                    <h3>播放清單</h3>
                </a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/100"></a>
                        <img class="main-thumb" src="https://cdn.example.com/pl100.jpg"/>
                        <div class="title">Section Playlist 1</div>
                        <div class="subtitle"><a>SectionAuthor</a></div>
                        <div class="subtitle-time">• 2024-05-01</div>
                        <div class="stats-container">
                            <div class="stat-item">7</div>
                        </div>
                    </div>
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/200"></a>
                        <img class="main-thumb" src="https://cdn.example.com/pl200.jpg"/>
                        <div class="title">Section Playlist 2</div>
                        <div class="subtitle"><a>SectionAuthor</a></div>
                        <div class="subtitle-time">• 2024-06-01</div>
                        <div class="stats-container">
                            <div class="stat-item">12</div>
                        </div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlists = parser.parseSectionPlaylists(doc, baseUrl, "播放清单")
        assertEquals(2, playlists.size)
        assertEquals("Section Playlist 1", playlists[0].title)
        assertEquals("$baseUrl/playlist/100", playlists[0].playlistUrl)
        assertEquals("7", playlists[0].videoCount)
        assertEquals("SectionAuthor", playlists[0].author)
        assertEquals("2024-05-01", playlists[0].publishTime)
    }

    @Test
    fun `parseSectionPlaylists matches traditional chinese section title`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3>播放清單</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/1"></a>
                        <div class="title">Traditional Title</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        // sectionTitle 为简体「播放清单」，但 h3 是繁体「播放清單」
        val playlists = parser.parseSectionPlaylists(doc, baseUrl, "播放清单")
        assertEquals(1, playlists.size)
        assertEquals("Traditional Title", playlists[0].title)
    }

    @Test
    fun `parseSectionPlaylists matches simplified chinese section title`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3>播放清单</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/1"></a>
                        <div class="title">Simplified Title</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlists = parser.parseSectionPlaylists(doc, baseUrl, "播放清单")
        assertEquals(1, playlists.size)
        assertEquals("Simplified Title", playlists[0].title)
    }

    @Test
    fun `parseSectionPlaylists with non-matching title returns empty`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3>影片</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/1"></a>
                        <div class="title">X</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        assertTrue(parser.parseSectionPlaylists(doc, baseUrl, "播放清单").isEmpty())
    }

    @Test
    fun `parseSectionPlaylists skips items missing video-link`() {
        val html = """
            <html><body>
                <a class="horizontal-row-title"><h3>播放清單</h3></a>
                <div class="home-rows-videos-wrapper">
                    <div class="video-item-container">
                        <!-- 无 video-link -->
                        <div class="title">No Link</div>
                    </div>
                    <div class="video-item-container">
                        <a class="video-link" href="/playlist/2"></a>
                        <div class="title">Has Link</div>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, baseUrl)
        val playlists = parser.parseSectionPlaylists(doc, baseUrl, "播放清单")
        assertEquals(1, playlists.size)
        assertEquals("Has Link", playlists[0].title)
    }

    @Test
    fun `parseSectionPlaylists with no horizontal-row-title returns empty`() {
        val doc = Jsoup.parse("<html><body></body></html>", baseUrl)
        assertTrue(parser.parseSectionPlaylists(doc, baseUrl, "播放清单").isEmpty())
    }
}
