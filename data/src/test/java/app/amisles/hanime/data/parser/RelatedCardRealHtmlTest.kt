package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.jsoup.Jsoup
import java.io.File

class RelatedCardRealHtmlTest {

    private val parser = VideoListParser()
    private val baseUrl = "https://www.hanime2.one"

    private fun parse(name: String) =
        parser.parseVideoList(Jsoup.parse(File("../docs/$name.html").readText(), baseUrl), baseUrl)

    @Test
    fun `non-hentai related cards are landscape`() {
        val file = File("../docs/非里番.html")
        assumeTrue("fixture missing", file.exists())
        val videos = parse("非里番")

        assertTrue(videos.isNotEmpty())
        assertTrue("非里番详情页不应出现竖版卡片", videos.none { it.isPortraitCover })
    }

    @Test
    fun `hentai related cards are portrait`() {
        val file = File("../docs/里番.html")
        assumeTrue("fixture missing", file.exists())
        val videos = parse("里番")

        val portrait = videos.filter { it.isPortraitCover }
        assertEquals("里番相关推荐共 59 条", 59, portrait.size)
        assertTrue(portrait.all { it.thumbnailUrl.contains("/image/cover/") })
        assertFalse(videos.none { it.isPortraitCover })
    }

    @Test
    fun `hentai related cards exclude playlist cards`() {
        val file = File("../docs/里番.html")
        assumeTrue("fixture missing", file.exists())
        val videos = parse("里番")
        assertTrue("播放集合卡片不应进入相关推荐", videos.none { it.id == "408309" })
    }
}
