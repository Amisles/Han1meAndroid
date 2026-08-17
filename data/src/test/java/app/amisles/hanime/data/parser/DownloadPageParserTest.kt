package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadPageParserTest {

    private lateinit var parser: DownloadPageParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = DownloadPageParser()
    }

    @Test
    fun `parse with download-table extracts all qualities`() {
        val html = """
            <html><body>
                <table class="download-table">
                    <thead><tr><th>#</th><th>Quality</th><th>Type</th><th>Size</th><th>Action</th></tr></thead>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>下載 (480p)</td>
                            <td>mp4</td>
                            <td>120MB</td>
                            <td><a data-url="https://dl.example.com/480">DL</a></td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>下載 (720p)</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                        <tr>
                            <td>3</td>
                            <td>下載 (1080p)</td>
                            <td>mp4</td>
                            <td>1.2GB</td>
                            <td><a data-url="https://dl.example.com/1080">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()

        val qualities = parser.parse(html, baseUrl)
        assertEquals(3, qualities.size)
        assertEquals("下載 (480p)", qualities[0].quality)
        assertEquals("480p", qualities[0].resolution)
        assertEquals("mp4", qualities[0].fileType)
        assertEquals("120MB", qualities[0].fileSize)
        assertEquals("https://dl.example.com/480", qualities[0].downloadUrl)

        assertEquals("1080p", qualities[2].resolution)
        assertEquals("1.2GB", qualities[2].fileSize)
    }

    @Test
    fun `parse with empty page returns empty list`() {
        val qualities = parser.parse("<html><body></body></html>", baseUrl)
        assertTrue(qualities.isEmpty())
    }

    @Test
    fun `parse with table missing download-table class falls back via text content`() {
        val html = """
            <html><body>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>下載 (720p)</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        // 没有 download-table 类，但表格文本包含「下載」，应被识别
        val qualities = parser.parse(html, baseUrl)
        assertEquals(1, qualities.size)
        assertEquals("720p", qualities[0].resolution)
    }

    @Test
    fun `parse with table containing 畫質 text is also recognized as fallback`() {
        val html = """
            <html><body>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>畫質 (1080p)</td>
                            <td>mp4</td>
                            <td>1.5GB</td>
                            <td><a data-url="https://dl.example.com/1080">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        val qualities = parser.parse(html, baseUrl)
        assertEquals(1, qualities.size)
        assertEquals("1080p", qualities[0].resolution)
    }

    @Test
    fun `parse skips rows with fewer than 4 cells`() {
        val html = """
            <html><body>
                <table class="download-table">
                    <tbody>
                        <tr>
                            <td>only one cell</td>
                        </tr>
                        <tr>
                            <td>1</td>
                            <td>下載 (720p)</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        val qualities = parser.parse(html, baseUrl)
        assertEquals(1, qualities.size)
        assertEquals("720p", qualities[0].resolution)
    }

    @Test
    fun `parse skips rows missing data-url attribute`() {
        val html = """
            <html><body>
                <table class="download-table">
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>下載 (480p)</td>
                            <td>mp4</td>
                            <td>120MB</td>
                            <td><a href="https://dl.example.com/no-data-url">DL</a></td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>下載 (720p)</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        val qualities = parser.parse(html, baseUrl)
        // 第一行没有 data-url，被跳过
        assertEquals(1, qualities.size)
        assertEquals("https://dl.example.com/720", qualities[0].downloadUrl)
    }

    @Test
    fun `parse infers resolution from quality text when parenthesized form missing`() {
        val html = """
            <html><body>
                <table class="download-table">
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>720p High Quality</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>1080p FHD</td>
                            <td>mp4</td>
                            <td>1.2GB</td>
                            <td><a data-url="https://dl.example.com/1080">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        val qualities = parser.parse(html, baseUrl)
        assertEquals(2, qualities.size)
        assertEquals("720p", qualities[0].resolution)
        assertEquals("1080p", qualities[1].resolution)
    }

    @Test
    fun `parse skips rows with empty data-url attribute`() {
        val html = """
            <html><body>
                <table class="download-table">
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>下載 (480p)</td>
                            <td>mp4</td>
                            <td>120MB</td>
                            <td><a data-url="">DL</a></td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>下載 (720p)</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        val qualities = parser.parse(html, baseUrl)
        // 空 data-url 视为无效直链，与缺失 data-url 一样跳过（避免将空 URL 传入
        // Request.url("") 产生异常或幽灵任务）；仅保留带有效直链的行
        assertEquals(1, qualities.size)
        assertEquals("https://dl.example.com/720", qualities[0].downloadUrl)
    }

    @Test
    fun `parse with no thead still parses tbody rows`() {
        val html = """
            <html><body>
                <table class="download-table">
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>下載 (480p)</td>
                            <td>mp4</td>
                            <td>120MB</td>
                            <td><a data-url="https://dl.example.com/480">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        val qualities = parser.parse(html, baseUrl)
        assertEquals(1, qualities.size)
    }

    @Test
    fun `parse with multiple alternative tables picks first valid one`() {
        val html = """
            <html><body>
                <table>
                    <tbody>
                        <tr><td>下載</td></tr>
                    </tbody>
                </table>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>下載 (720p)</td>
                            <td>mp4</td>
                            <td>250MB</td>
                            <td><a data-url="https://dl.example.com/720">DL</a></td>
                        </tr>
                    </tbody>
                </table>
            </body></html>
        """.trimIndent()
        // 第一个表格包含「下載」文本但无有效行；继续找下一个
        val qualities = parser.parse(html, baseUrl)
        // 首个表无有效行（cell<4）被跳过，取下一个含 qualities 的表
        assertEquals(1, qualities.size)
        assertEquals("720p", qualities[0].resolution)
    }
}
