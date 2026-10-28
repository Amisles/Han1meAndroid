package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicDirectoryKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComicDirectoryRealHtmlTest {

    @Test
    fun parsesRealTagsPage() {
        val file = File("../docs/漫画标签.html")
        if (!file.exists()) return
        val entries = ComicDirectoryParser().parse(file.readText(), "https://hanimeone.me", ComicDirectoryKind.TAG)
        println("tags=" + entries.size)
        assertTrue(entries.size >= 500)
        assertTrue(entries.all { it.url.contains("/tags/") })
        assertTrue(entries.all { it.name.isNotBlank() })
        assertTrue(entries.count { it.count.isNotBlank() } >= entries.size / 2)
    }
}
