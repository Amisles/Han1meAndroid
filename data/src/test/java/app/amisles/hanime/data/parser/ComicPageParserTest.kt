package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComicPageParserTest {

    private lateinit var parser: ComicPageParser
    private val baseUrl = "https://hanimeone.me"

    @Before
    fun setUp() {
        parser = ComicPageParser()
    }

    private fun card(id: String, title: String, cover: String) =
        """
        |<div class="comic-rows-videos-div">
        |  <a href="https://hanimeone.me/comic/$id">
        |    <img class="lazy" src="https://vdownload.hembed.com/image/icon/comic-placeholder.jpg"
        |         data-srcset="$cover">
        |    <div class="comic-rows-videos-title">$title</div>
        |  </a>
        |</div>
        """.trimMargin()

    private fun page(vararg body: String) =
        "<html><body>" + body.joinToString("") + "</body></html>"

    @Test
    fun sectionTitleAndItemsParsed() {
        val html = page(
            """
            |<div class="comics-related-wrapper comic-rows-wrapper">
            |  <h3><span class="material-icons">local_fire_department</span>發燒漫畫</h3>
            |  ${card("167436", "Comic A", "https://t2.nhentai.net/galleries/1/cover.webp")}
            |  ${card("167430", "Comic B", "https://t2.nhentai.net/galleries/2/cover.webp")}
            |</div>
            """.trimMargin()
        )
        val result = parser.parse(html, baseUrl)
        assertEquals(1, result.sections.size)
        assertEquals(2, result.sections[0].items.size)
        assertEquals("167436", result.sections[0].items[0].id)
    }

    @Test
    fun coverComesFromDataSrcsetNotPlaceholder() {
        val html = page(
            """
            |<div class="comic-rows-wrapper">
            |  <h3>最新上傳</h3>
            |  ${card("1", "T", "https://t2.nhentai.net/galleries/9/cover.jpg")}
            |</div>
            """.trimMargin()
        )
        val item = parser.parse(html, baseUrl).sections[0].items[0]
        assertEquals("https://t2.nhentai.net/galleries/9/cover.jpg", item.coverUrl)
        assertFalse(item.coverUrl.contains("placeholder"))
    }

    @Test
    fun duplicatesRemovedWithinSection() {
        val html = page(
            """
            |<div class="comic-rows-wrapper">
            |  <h3>S</h3>
            |  ${card("9", "T1", "https://c/1.jpg")}
            |  ${card("9", "T2", "https://c/2.jpg")}
            |</div>
            """.trimMargin()
        )
        assertEquals(1, parser.parse(html, baseUrl).sections[0].items.size)
    }

    @Test
    fun emptySectionDropped() {
        val html = page(
            """
            |<div class="comic-rows-wrapper"><h3>空</h3></div>
            |<div class="comic-rows-wrapper">
            |  <h3>有</h3>
            |  ${card("2", "T", "https://c/2.jpg")}
            |</div>
            """.trimMargin()
        )
        val result = parser.parse(html, baseUrl)
        assertEquals(1, result.sections.size)
        assertEquals("有", result.sections[0].title)
    }

    @Test
    fun paginationReadsCurrentAndTotal() {
        val html = page(
            """
            |<div class="comic-rows-wrapper">
            |  <h3>S</h3>
            |  ${card("3", "T", "https://c/3.jpg")}
            |</div>
            |<div class="search-pagination"><ul class="pagination">
            |  <li class="page-item active"><span class="page-link">2</span></li>
            |  <li class="page-item"><a class="page-link" href="?page=5">5</a></li>
            |  <li class="page-item"><a class="page-link" rel="next" href="?page=3">&rsaquo;</a></li>
            |</ul></div>
            """.trimMargin()
        )
        val result = parser.parse(html, baseUrl)
        assertEquals(2, result.currentPage)
        assertEquals(5, result.totalPages)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun missingPaginationDefaultsToSinglePage() {
        val html = page(
            """
            |<div class="comic-rows-wrapper">
            |  <h3>S</h3>
            |  ${card("4", "T", "https://c/4.jpg")}
            |</div>
            """.trimMargin()
        )
        val result = parser.parse(html, baseUrl)
        assertEquals(1, result.currentPage)
        assertEquals(1, result.totalPages)
        assertFalse(result.hasNextPage)
    }

    @Test
    fun cardWithoutTitleIgnored() {
        val html = page(
            """
            |<div class="comic-rows-wrapper">
            |  <h3>S</h3>
            |  <div class="comic-rows-videos-div">
            |    <a href="https://hanimeone.me/comic/7"><div class="comic-rows-videos-title"></div></a>
            |  </div>
            |  ${card("8", "OK", "https://c/8.jpg")}
            |</div>
            """.trimMargin()
        )
        val result = parser.parse(html, baseUrl)
        assertEquals(1, result.sections[0].items.size)
        assertEquals("8", result.sections[0].items[0].id)
    }
}
