package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComicDirectoryResultParserTest {

    private lateinit var parser: ComicDirectoryResultParser
    private val baseUrl = "https://hanimeone.me"

    @Before
    fun setUp() {
        parser = ComicDirectoryResultParser()
    }

    private fun card(id: String, title: String, cover: String) =
        "<div class=\"comic-rows-videos-div\"><a href=\"https://hanimeone.me/comic/" + id + "\">" +
            "<img class=\"lazy\" src=\"https://vdownload.hembed.com/image/icon/comic-placeholder.jpg\" " +
            "data-srcset=\"" + cover + "\"><div class=\"comic-rows-videos-title\">" + title + "</div></a></div>"

    private fun page(vararg body: String) =
        "<html><body>" + body.joinToString("") + "</body></html>"

    private val pager =
        "<div class=\"search-pagination hidden-xs\"><ul class=\"pagination\">" +
            "<li class=\"page-item active\"><span class=\"page-link\">2</span></li>" +
            "<li class=\"page-item\"><a class=\"page-link\" href=\"?page=3\">3</a></li>" +
            "<li class=\"page-item\"><a class=\"page-link\" rel=\"next\" href=\"?page=3\">&rsaquo;</a></li>" +
            "</ul></div>"

    @Test
    fun itemsParsedWithCoverFromDataSrcset() {
        val html = page(
            "<div class=\"comic-rows-wrapper\">" +
                card("141014", "Comic A", "https://t2.nhentai.net/galleries/1/cover.webp") +
                card("104689", "Comic B", "https://t2.nhentai.net/galleries/2/cover.jpg") +
                "</div>"
        )
        val result = parser.parse(html, baseUrl)
        assertEquals(2, result.items.size)
        assertEquals("141014", result.items[0].id)
        assertEquals("Comic B", result.items[1].title)
        assertEquals("https://t2.nhentai.net/galleries/1/cover.webp", result.items[0].coverUrl)
        assertFalse(result.items[0].coverUrl.contains("placeholder"))
    }

    @Test
    fun paginationReadsCurrentAndTotal() {
        val result = parser.parse(
            page("<div class=\"comic-rows-wrapper\">" + card("1", "A", "https://c/1.jpg") + "</div>", pager),
            baseUrl
        )
        assertEquals(2, result.currentPage)
        assertEquals(3, result.totalPages)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun missingPaginationDefaultsToSinglePage() {
        val result = parser.parse(
            page("<div class=\"comic-rows-wrapper\">" + card("1", "A", "https://c/1.jpg") + "</div>"),
            baseUrl
        )
        assertEquals(1, result.currentPage)
        assertEquals(1, result.totalPages)
        assertFalse(result.hasNextPage)
    }

    @Test
    fun duplicatesCollapsed() {
        val html = page(
            "<div class=\"comic-rows-wrapper\">" +
                card("7", "A", "https://c/7.jpg") +
                card("7", "A", "https://c/7.jpg") +
                "</div>"
        )
        assertEquals(1, parser.parse(html, baseUrl).items.size)
    }

    @Test
    fun sortTabsAreNotMistakenForItems() {
        val html = page(
            "<h4 class=\"comics-search-tag-tabs\">" +
                "<a href=\"https://hanimeone.me/tags/x\"><div class=\"no-select active\">最新</div></a>" +
                "<a href=\"https://hanimeone.me/tags/x/popular-week\"><div class=\"no-select\">本週</div></a>" +
                "</h4>",
            "<div class=\"comic-rows-wrapper\">" + card("9", "A", "https://c/9.jpg") + "</div>"
        )
        assertEquals(1, parser.parse(html, baseUrl).items.size)
    }

    @Test
    fun emptyResultHasNoItems() {
        assertTrue(parser.parse(page(""), baseUrl).isEmpty)
    }
}
