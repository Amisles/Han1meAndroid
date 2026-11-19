package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicDirectoryResultRealHtmlTest {

    private val parser = ComicDirectoryResultParser()
    private val baseUrl = "https://hanimeone.me"

    @Test
    fun parsesRealTagResultPage() {
        val result = parser.parse(Fixtures.html("comic-tag-result.html"), baseUrl)

        assertEquals(30, result.items.size)
        assertEquals("141014", result.items[0].id)
        assertEquals(
            "https://t2.nhentai.net/galleries/3406989/cover.webp",
            result.items[0].coverUrl
        )
        assertEquals(1, result.currentPage)
        assertEquals(22, result.totalPages)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun parsesRealArtistResultPage() {
        val result = parser.parse(Fixtures.html("comic-artist-result.html"), baseUrl)

        assertEquals(30, result.items.size)
        assertEquals("145366", result.items[0].id)
        assertEquals(6, result.totalPages)
        assertTrue(result.hasNextPage)
    }
}
