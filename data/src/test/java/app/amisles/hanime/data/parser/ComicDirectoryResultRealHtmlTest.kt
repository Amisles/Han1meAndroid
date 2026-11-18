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
        println("tag items=" + result.items.size + " page=" + result.currentPage + "/" + result.totalPages)
        assertTrue(result.items.size >= 30)
        assertEquals(1, result.currentPage)
        assertTrue(result.totalPages > 1)
        assertTrue(result.hasNextPage)
        assertTrue(result.items.all { it.id.isNotEmpty() && it.title.isNotBlank() && it.coverUrl.isNotBlank() })
    }

    @Test
    fun parsesRealArtistResultPage() {
        val result = parser.parse(Fixtures.html("comic-artist-result.html"), baseUrl)
        println("artist items=" + result.items.size + " page=" + result.currentPage + "/" + result.totalPages)
        assertTrue(result.items.size >= 30)
        assertTrue(result.hasNextPage)
    }
}
