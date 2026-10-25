package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComicPageRealHtmlTest {

    @Test
    fun parsesRealComicsPage() {
        val file = File("../docs/comics.html")
        if (!file.exists()) return
        val html = file.readText()
        val page = ComicPageParser().parse(html, "https://hanimeone.me")
        println("sections=" + page.sections.size + " current=" + page.currentPage + " total=" + page.totalPages)
        page.sections.forEach { s -> println("  [" + s.title + "] " + s.items.size) }
        assertTrue(page.sections.isNotEmpty())
        assertTrue(page.sections.all { it.items.isNotEmpty() })
        assertTrue(page.sections.sumOf { it.items.size } >= 30)
        assertTrue(page.sections.all { s -> s.items.all { it.id.isNotEmpty() && it.coverUrl.isNotBlank() } })
        assertEquals(1, page.currentPage)
        assertTrue(page.totalPages > 1)
    }
}
