package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchPageParserTest {

    private lateinit var parser: SearchPageParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = SearchPageParser(VideoListParser())
    }

    private fun videoContainer(id: String, path: String = "/watch?v=$id"): String = """
        <div class="video-item-container">
            <a class="video-link" href="$path"></a>
            <img class="main-thumb" src="https://cdn.example.com/$id.jpg"/>
            <div class="title">Video $id</div>
        </div>
    """.trimIndent()

    @Test
    fun `parse returns only videos with watch URLs`() {
        val html = """
            <html><body>
                ${videoContainer("1")}
                ${videoContainer("2", "/author/123")}
                ${videoContainer("3")}
                ${videoContainer("4", "/playlist/99")}
            </body></html>
        """.trimIndent()

        val videos = parser.parse(html, baseUrl)
        assertEquals(2, videos.size)
        assertEquals("1", videos[0].id)
        assertEquals("3", videos[1].id)
    }

    @Test
    fun `parse with empty page returns empty list`() {
        val videos = parser.parse("<html><body></body></html>", baseUrl)
        assertTrue(videos.isEmpty())
    }

    @Test
    fun `parse filters out non-watch paths like author and playlist`() {
        val html = """
            <html><body>
                <div class="video-item-container">
                    <a class="video-link" href="/author/555"></a>
                    <div class="title">Author Page</div>
                </div>
                <div class="video-item-container">
                    <a class="video-link" href="/watch?v=666"></a>
                    <div class="title">Watch Page</div>
                </div>
            </body></html>
        """.trimIndent()
        val videos = parser.parse(html, baseUrl)
        assertEquals(1, videos.size)
        assertEquals("666", videos[0].id)
    }

    @Test
    fun `parseWithPagination returns videos and pagination info`() {
        val html = """
            <html><body>
                ${videoContainer("1")}
                ${videoContainer("2")}
                <ul class="pagination">
                    <li class="page-item"><a class="page-link" rel="prev" href="?page=1">«</a></li>
                    <li class="page-item"><a class="page-link" href="?page=1">1</a></li>
                    <li class="page-item active"><span class="page-link">2</span></li>
                    <li class="page-item"><a class="page-link" href="?page=3">3</a></li>
                    <li class="page-item"><a class="page-link" rel="next" href="?page=3">»</a></li>
                </ul>
            </body></html>
        """.trimIndent()

        val result = parser.parseWithPagination(html, baseUrl)
        assertEquals(2, result.videos.size)
        assertEquals(2, result.currentPage)
        assertEquals(3, result.totalPages)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `parseWithPagination without next rel infers hasNextPage from currentPage vs totalPages`() {
        val html = """
            <html><body>
                ${videoContainer("1")}
                <ul class="pagination">
                    <li class="page-item"><a class="page-link" href="?page=1">1</a></li>
                    <li class="page-item active"><span class="page-link">1</span></li>
                    <li class="page-item"><a class="page-link" href="?page=2">2</a></li>
                    <li class="page-item"><a class="page-link" href="?page=3">3</a></li>
                </ul>
            </body></html>
        """.trimIndent()

        val result = parser.parseWithPagination(html, baseUrl)
        assertEquals(1, result.currentPage)
        assertEquals(3, result.totalPages)
        // 无 rel="next" 但 currentPage < totalPages，应推断为 true
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `parsePagination with empty pagination returns defaults`() {
        val html = "<html><body></body></html>"
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val (currentPage, totalPages, hasNextPage) = parser.parsePagination(doc)
        assertEquals(1, currentPage)
        assertEquals(1, totalPages)
        assertFalse(hasNextPage)
    }

    @Test
    fun `parsePagination on last page returns hasNextPage false`() {
        val html = """
            <html><body>
                <ul class="pagination">
                    <li class="page-item"><a class="page-link" href="?page=1">1</a></li>
                    <li class="page-item"><a class="page-link" href="?page=2">2</a></li>
                    <li class="page-item active"><span class="page-link">2</span></li>
                </ul>
            </body></html>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val (currentPage, totalPages, hasNextPage) = parser.parsePagination(doc)
        assertEquals(2, currentPage)
        assertEquals(2, totalPages)
        assertFalse(hasNextPage)
    }

    @Test
    fun `parsePagination extracts max page from skip-page-input when larger`() {
        val html = """
            <html><body>
                <ul class="pagination">
                    <li class="page-item active"><span class="page-link">1</span></li>
                    <li class="page-item"><a class="page-link" href="?page=2">2</a></li>
                </ul>
                <input id="skip-page-input" oninput="validateNumberInput(this, 1, 50)"/>
            </body></html>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val (currentPage, totalPages, _) = parser.parsePagination(doc)
        assertEquals(1, currentPage)
        // skip-page-input 中的 50 大于分页链接中的最大值 2
        assertEquals(50, totalPages)
    }

    @Test
    fun `parsePagination keeps larger of pagination links and skip input`() {
        val html = """
            <html><body>
                <ul class="pagination">
                    <li class="page-item active"><span class="page-link">1</span></li>
                    <li class="page-item"><a class="page-link" href="?page=100">100</a></li>
                </ul>
                <input id="skip-page-input" oninput="validateNumberInput(this, 1, 50)"/>
            </body></html>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val (_, totalPages, _) = parser.parsePagination(doc)
        // 分页链接中的 100 大于 skip-input 的 50
        assertEquals(100, totalPages)
    }

    @Test
    fun `parsePagination ignores non-numeric page link text`() {
        val html = """
            <html><body>
                <ul class="pagination">
                    <li class="page-item active"><span class="page-link">1</span></li>
                    <li class="page-item"><a class="page-link" href="?page=2">«</a></li>
                    <li class="page-item"><a class="page-link" href="?page=3">»</a></li>
                </ul>
            </body></html>
        """.trimIndent()
        val doc = org.jsoup.Jsoup.parse(html, baseUrl)
        val (currentPage, totalPages, _) = parser.parsePagination(doc)
        assertEquals(1, currentPage)
        // « 和 » 无法转数字，totalPages 保持默认 1
        assertEquals(1, totalPages)
    }
}
