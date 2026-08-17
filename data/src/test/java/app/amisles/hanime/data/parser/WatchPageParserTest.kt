package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WatchPageParserTest {

    private lateinit var parser: WatchPageParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = WatchPageParser(VideoListParser(), PlaylistParser())
    }

    @Test
    fun `parse extracts full video detail with sources and metadata`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v/720p.mp4" poster="https://cdn.example.com/poster.jpg">
                    <source src="https://cdn.example.com/v/480p.mp4" size="480" type="video/mp4"/>
                    <source src="https://cdn.example.com/v/720p.mp4" size="720" type="video/mp4"/>
                    <source src="https://cdn.example.com/v/1080p.mp4" size="1080" type="video/mp4"/>
                </video>
                <h3 id="shareBtn-title">Test Video Title</h3>
                <div class="video-tags-wrapper">
                    <div class="single-video-tag"><a>Tag1</a></div>
                    <div class="single-video-tag"><a>Tag2</a></div>
                    <div class="single-video-tag"><a>Tag3</a></div>
                </div>
                <a id="video-artist-name">TestAuthor</a>
                <a href="/user/123">Author Page</a>
                <div class="video-caption-text">This is the description.</div>
                <meta name="csrf-token" content="token_abc_123"/>
                <input name="comment-count" value="42"/>
                Release: 2024/01/15 Size: 1.5GB
            </body></html>
        """.trimIndent()

        val detail = parser.parse(html, baseUrl)!!

        assertEquals("Test Video Title", detail.title)
        assertEquals("https://cdn.example.com/poster.jpg", detail.posterUrl)
        assertEquals("https://cdn.example.com/v/720p.mp4", detail.defaultSourceUrl)
        assertEquals(listOf("Tag1", "Tag2", "Tag3"), detail.tags)
        assertEquals("2024/01/15", detail.releaseDate)
        assertEquals("1.5GB", detail.fileSize)
        assertEquals("TestAuthor", detail.author)
        assertEquals("$baseUrl/user/123", detail.authorPageUrl)
        assertEquals("This is the description.", detail.description)
        assertEquals("token_abc_123", detail.csrfToken)
        assertEquals(42, detail.commentCount)
        // 源按 size 降序
        assertEquals(3, detail.videoSources.size)
        assertEquals(1080, detail.videoSources[0].size)
        assertEquals(720, detail.videoSources[1].size)
        assertEquals(480, detail.videoSources[2].size)
        assertEquals("1080p", detail.videoSources[0].resolution)
    }

    @Test
    fun `parse with bracketed title strips bracket prefix`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">[Artist] Real Title Here</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("Real Title Here", detail.title)
    }

    @Test
    fun `parse with bracket but no closing bracket keeps original`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">[unclosed title</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("[unclosed title", detail.title)
    }

    @Test
    fun `parse with bracket at end keeps original`() {
        // closingBracket == rawTitle.length - 1 means ']' is last char, no substring
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">[only bracket]</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("[only bracket]", detail.title)
    }

    @Test
    fun `parse falls back to h3 single-video-title when shareBtn-title missing`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 class="single-video-title">Fallback Title</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("Fallback Title", detail.title)
    }

    @Test
    fun `parse falls back to first h3 when specific title selectors missing`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3>Generic H3 Title</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("Generic H3 Title", detail.title)
    }

    @Test
    fun `parse deduplicates tags and filters long ones`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                <div class="video-tags-wrapper">
                    <div class="single-video-tag"><a>Duplicate</a></div>
                    <div class="single-video-tag"><a>Duplicate</a></div>
                    <div class="single-video-tag"><a>Short</a></div>
                    <div class="single-video-tag"><a>${"x".repeat(35)}</a></div>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // 重复的被去重，长度 35 的被过滤
        assertEquals(listOf("Duplicate", "Short"), detail.tags)
    }

    @Test
    fun `parse skips empty tag text`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                <div class="video-tags-wrapper">
                    <div class="single-video-tag"><a></a></div>
                    <div class="single-video-tag"><a>Real</a></div>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals(listOf("Real"), detail.tags)
    }

    @Test
    fun `parse excludes add remove action buttons from tags`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                <div class="video-tags-wrapper video-details-wrapper">
                    <div class="single-video-tag"><a href="/search?query=崩壞：星穹鐵道"><span>#</span>&nbsp;崩壞：星穹鐵道</a></div>
                    <div class="single-video-tag"><a href="/search?query=緋英"><span>#</span>&nbsp;緋英</a></div>
                    <div class="single-video-tag" data-toggle="modal" data-target="#signUpModal"><a><span class="material-icons">add</span></a></div>
                    <div class="single-video-tag" data-toggle="modal" data-target="#signUpModal"><a><span class="material-icons">remove</span></a></div>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // add / remove 是官网「添加/移除标签」功能按钮，必须从标签中剔除
        assertEquals(listOf("# 崩壞：星穹鐵道", "# 緋英"), detail.tags)
    }

    @Test
    fun `parse with no video sources returns empty list and empty default source`() {
        val html = """
            <html><body>
                <h3 id="shareBtn-title">No Video Tag</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("", detail.defaultSourceUrl)
        assertTrue(detail.videoSources.isEmpty())
    }

    @Test
    fun `parse with no default src falls back to 720p source`() {
        val html = """
            <html><body>
                <video id="player" poster="https://cdn.example.com/poster.jpg">
                    <source src="https://cdn.example.com/v/480p.mp4" size="480" type="video/mp4"/>
                    <source src="https://cdn.example.com/v/720p.mp4" size="720" type="video/mp4"/>
                    <source src="https://cdn.example.com/v/1080p.mp4" size="1080" type="video/mp4"/>
                </video>
                <h3 id="shareBtn-title">No Default Src</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // 默认源缺失时，优先选择 720p
        assertEquals("https://cdn.example.com/v/720p.mp4", detail.defaultSourceUrl)
    }

    @Test
    fun `parse with no default src and no 720p falls back to first source`() {
        val html = """
            <html><body>
                <video id="player">
                    <source src="https://cdn.example.com/v/480p.mp4" size="480" type="video/mp4"/>
                    <source src="https://cdn.example.com/v/1080p.mp4" size="1080" type="video/mp4"/>
                </video>
                <h3 id="shareBtn-title">No 720p</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // 没有 720p 时，使用排序后的第一个（即 1080p）
        assertEquals("https://cdn.example.com/v/1080p.mp4", detail.defaultSourceUrl)
    }

    @Test
    fun `parse extracts related videos via VideoListParser`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Main</h3>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=111"></a>
                    <img class="main-thumb" src="https://cdn.example.com/111.jpg"/>
                    <div class="title">Related 1</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=222"></a>
                    <img class="main-thumb" src="https://cdn.example.com/222.jpg"/>
                    <div class="title">Related 2</div>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals(2, detail.relatedVideos.size)
        assertEquals("111", detail.relatedVideos[0].id)
        assertEquals("222", detail.relatedVideos[1].id)
    }

    @Test
    fun `parse filters related videos that are part of playlist`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Main</h3>
                <div class="video-playlist-wrapper">
                    <div id="playlist-top-block">
                        <h4><a href="/playlist/1">My Playlist</a></h4>
                        <div style="font-size: 12px;">
                            <a>PlaylistAuthor</a>
                            <span>3 videos</span>
                        </div>
                    </div>
                    <div class="playlist-hover-wrap">
                        <div class="playlist-video-card">
                            <a href="/watch?v=111">
                                <img class="main-thumb" src="https://cdn.example.com/111.jpg"/>
                                <div class="duration">01:00</div>
                                <div class="video-title"><a>Playlist Video 1</a></div>
                            </a>
                        </div>
                    </div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=111"></a>
                    <img class="main-thumb" src="https://cdn.example.com/111.jpg"/>
                    <div class="title">Related 1</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=222"></a>
                    <img class="main-thumb" src="https://cdn.example.com/222.jpg"/>
                    <div class="title">Related 2</div>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // 播放列表包含 v=111，所以推荐视频中 v=111 应被过滤
        assertEquals(1, detail.relatedVideos.size)
        assertEquals("222", detail.relatedVideos[0].id)
        assertNotNull(detail.playlist)
        assertEquals("My Playlist", detail.playlist!!.title)
    }

    @Test
    fun `parse with malformed html does not throw and returns detail with empty fields`() {
        // Jsoup 容错解析不完整 HTML，parser 外层 try-catch 兜底
        val html = "<html><body><unclosed>"
        val detail = parser.parse(html, baseUrl)
        assertNotNull(detail)
        assertEquals("", detail!!.title)
    }

    @Test
    fun `parse extracts comment count from regex fallback when input missing`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                <input type="hidden" name="other" value="100"/>
                <script>var data = {comment-count" value="99"};</script>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // regex: comment-count"\s+value="(\d+)"
        assertEquals(99, detail.commentCount)
    }

    @Test
    fun `parse with no comment count returns 0`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals(0, detail.commentCount)
    }

    @Test
    fun `parse extracts author avatar from inline-style wrapper`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                <div style="position: relative; display: inline-block;">
                    <img style="position: absolute; top: 0; left: 0;" src="https://cdn.example.com/avatar.jpg"/>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("https://cdn.example.com/avatar.jpg", detail.authorAvatarUrl)
    }

    @Test
    fun `parse extracts author avatar from video-user-avatar fallback`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                <div style="position: relative; display: inline-block;">
                    <img id="video-user-avatar" src="https://cdn.example.com/avatar2.jpg"/>
                </div>
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("https://cdn.example.com/avatar2.jpg", detail.authorAvatarUrl)
    }

    @Test
    fun `parse with multiple date-like strings picks first`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                Published 2024/03/15, updated 2024/04/20
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // Regex.find 返回第一个匹配
        assertEquals("2024/03/15", detail.releaseDate)
    }

    @Test
    fun `parse with mixed file size units picks first match`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                Size: 1.5GB extra 500MB
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        assertEquals("1.5GB", detail.fileSize)
    }

    @Test
    fun `parse with lowercase file size unit still matches`() {
        val html = """
            <html><body>
                <video id="player" src="https://cdn.example.com/v.mp4"/>
                <h3 id="shareBtn-title">Title</h3>
                Size: 800mb
            </body></html>
        """.trimIndent()
        val detail = parser.parse(html, baseUrl)!!
        // RegexOption.IGNORE_CASE，但 regex 的 value 保持原样（800mb）
        assertEquals("800mb", detail.fileSize)
    }
}
