package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicDirectoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicDirectoryRealHtmlTest {

    @Test
    fun parsesRealTagsPage() {
        val entries = ComicDirectoryParser().parse(
            Fixtures.html("comic-directory-tags.html"),
            "https://hanimeone.me",
            ComicDirectoryKind.TAG
        )

        assertEquals(917, entries.size)
        assertEquals("唯一女性", entries[0].name)
        assertEquals("23k", entries[0].count)
        assertEquals("https://hanimeone.me/tags/%E5%94%AF%E4%B8%80%E5%A5%B3%E6%80%A7", entries[0].url)
        assertTrue(entries.all { it.url.contains("/tags/") })
    }
}
