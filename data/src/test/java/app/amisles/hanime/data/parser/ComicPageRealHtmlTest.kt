package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPageRealHtmlTest {

    @Test
    fun parsesRealComicsPage() {
        val page = ComicPageParser().parse(Fixtures.html("comic-list.html"), "https://hanimeone.me")

        assertEquals(listOf("發燒漫畫", "最新上傳"), page.sections.map { it.title })
        assertEquals(6, page.sections[0].items.size)
        assertEquals(30, page.sections[1].items.size)
        assertEquals("167436", page.sections[0].items.first().id)
        assertEquals("167430", page.sections[1].items.first().id)
        assertTrue(
            page.sections[0].items.first().coverUrl.startsWith("https://t2.nhentai.net/galleries/4243095/")
        )
        assertEquals(1, page.currentPage)
        assertEquals(5283, page.totalPages)
    }
}
