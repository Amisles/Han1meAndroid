package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 漫画正文页地址推导。
 *
 * 站点只给「前缀 + 当前页扩展名」，其余页由前缀 + 页码 + 扩展名拼出；
 * 扩展名兜底一旦取错，整话图片会全部 404。
 */
class ComicReaderParserTest {

    private val parser = ComicReaderParser()
    private val baseUrl = "https://hanime1.me"

    private fun pageHtml(src: String, dataExtension: String? = null, pages: Int = 3): String {
        // 用 Char 字面量拼引号：避免在 Kotlin 字符串里再写转义
        val extAttr = if (dataExtension == null) "" else " data-extension=" + '"' + dataExtension + '"'
        return """
            <div class="comic-show-content-nav" data-pages="$pages"></div>
            <img id="current-page-image" src="$src"$extAttr/>
        """.trimIndent()
    }

    @Test
    fun `url without extension falls back to webp instead of host fragment`() {
        // 旧实现用「最后一个点之后」，会从 cdn.example.com 里截出 com/comic/123/1
        val chapter = parser.parse(pageHtml("https://cdn.example.com/comic/123/1"), baseUrl, "123")

        assertEquals(3, chapter.pageCount)
        assertEquals("https://cdn.example.com/comic/123/1.webp", chapter.pages[0])
        assertEquals("https://cdn.example.com/comic/123/3.webp", chapter.pages[2])
    }

    @Test
    fun `extension with query string is preserved`() {
        val chapter = parser.parse(
            pageHtml("https://cdn.example.com/comic/9/1.jpg?token=abc"),
            baseUrl,
            "9"
        )
        assertEquals("https://cdn.example.com/comic/9/1.jpg", chapter.pages[0])
    }

    @Test
    fun `data-extension wins over url`() {
        val chapter = parser.parse(
            pageHtml("https://cdn.example.com/comic/9/1.jpg", dataExtension = "png"),
            baseUrl,
            "9"
        )
        assertEquals("https://cdn.example.com/comic/9/1.png", chapter.pages[0])
    }
}
