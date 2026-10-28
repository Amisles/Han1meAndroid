package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicDirectoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicDirectoryParserTest {

    private val parser = ComicDirectoryParser()
    private val baseUrl = "https://hanimeone.me"

    private fun chip(href: String, name: String, count: String? = null): String {
        val badge = if (count == null) "" else "<span style=\"color: grey;\">" + count + "</span>"
        return "<a class=\"hover-lighter\" href=\"" + href + "\"><div class=\"no-select\">" +
            "<span style=\"padding-right: 7px;\">" + name + "</span>" + badge + "</div></a>"
    }

    private fun page(vararg body: String) =
        "<html><body><div class=\"content-padding\">" + body.joinToString("") + "</div></body></html>"

    @Test
    fun tagChipsCarryNameCountAndUrl() {
        val html = page(
            chip("https://hanimeone.me/tags/%E7%A0%B4%E8%99%95", "破處", "4.2k"),
            chip("https://hanimeone.me/tags/NTR", "NTR", "918")
        )
        val entries = parser.parse(html, baseUrl, ComicDirectoryKind.TAG)
        assertEquals(2, entries.size)
        assertEquals("破處", entries[0].name)
        assertEquals("4.2k", entries[0].count)
        assertEquals("https://hanimeone.me/tags/%E7%A0%B4%E8%99%95", entries[0].url)
        assertEquals("918", entries[1].count)
    }

    @Test
    fun missingCountBecomesEmpty() {
        val html = page(chip("https://hanimeone.me/tags/solo", "solo"))
        val entry = parser.parse(html, baseUrl, ComicDirectoryKind.TAG).single()
        assertEquals("", entry.count)
    }

    @Test
    fun nonNumericSecondSpanIgnored() {
        val html = page(chip("https://hanimeone.me/tags/odd", "odd", "new"))
        assertEquals("", parser.parse(html, baseUrl, ComicDirectoryKind.TAG).single().count)
    }

    @Test
    fun navigationLinkNotTreatedAsEntry() {
        val html = page(
            "<a class=\"comic-nav-item\" href=\"https://hanimeone.me/comics/tags\">標籤</a>",
            chip("https://hanimeone.me/tags/a", "A", "1")
        )
        val entries = parser.parse(html, baseUrl, ComicDirectoryKind.TAG)
        assertEquals(1, entries.size)
        assertEquals("A", entries[0].name)
    }

    @Test
    fun artistKindIgnoresTagChips() {
        val html = page(
            chip("https://hanimeone.me/tags/a", "A", "1"),
            chip("https://hanimeone.me/artists/kuroinu%20juu", "kuroinu juu", "1.2k")
        )
        val entries = parser.parse(html, baseUrl, ComicDirectoryKind.ARTIST)
        assertEquals(1, entries.size)
        assertEquals("kuroinu juu", entries[0].name)
        assertTrue(entries[0].url.endsWith("/artists/kuroinu%20juu"))
    }

    @Test
    fun duplicateUrlsCollapsed() {
        val html = page(
            chip("https://hanimeone.me/tags/a", "A", "1"),
            chip("https://hanimeone.me/tags/a", "A", "1")
        )
        assertEquals(1, parser.parse(html, baseUrl, ComicDirectoryKind.TAG).size)
    }

    @Test
    fun anchorWithoutSpansFallsBackToText() {
        val html = page("<a href=\"https://hanimeone.me/tags/plain\">plain</a>")
        val entry = parser.parse(html, baseUrl, ComicDirectoryKind.TAG).single()
        assertEquals("plain", entry.name)
        assertEquals("", entry.count)
    }
}
