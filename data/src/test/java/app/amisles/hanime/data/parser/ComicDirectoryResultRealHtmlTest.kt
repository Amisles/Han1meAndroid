package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComicDirectoryResultRealHtmlTest {

    private val parser = ComicDirectoryResultParser()
    private val baseUrl = "https://hanimeone.me"

    @Test
    fun parsesRealTagResultPage() {
        val file = File("../docs/漫画标签搜索.html")
        if (!file.exists()) return
        val result = parser.parse(file.readText(), baseUrl)
        println("tag items=" + result.items.size + " page=" + result.currentPage + "/" + result.totalPages)
        assertTrue(result.items.size >= 30)
        assertEquals(1, result.currentPage)
        assertTrue(result.totalPages > 1)
        assertTrue(result.hasNextPage)
        assertTrue(result.items.all { it.id.isNotEmpty() && it.title.isNotBlank() && it.coverUrl.isNotBlank() })
    }

    @Test
    fun parsesRealArtistResultPage() {
        val file = File("../docs/漫画作者搜索.html")
        if (!file.exists()) return
        val result = parser.parse(file.readText(), baseUrl)
        println("artist items=" + result.items.size + " page=" + result.currentPage + "/" + result.totalPages)
        assertTrue(result.items.size >= 30)
        assertTrue(result.hasNextPage)
    }
}
