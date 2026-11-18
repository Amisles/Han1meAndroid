package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup

class RelatedCardRealHtmlTest {

    private val parser = VideoListParser()
    private val baseUrl = "https://www.hanime2.one"

    private fun parse(name: String) =
        parser.parseVideoList(Jsoup.parse(Fixtures.html(name), baseUrl), baseUrl)

    @Test
    fun `non-hentai related cards are landscape`() {
        val videos = parse("video-detail-nonhentai.html")

        assertTrue(videos.isNotEmpty())
        assertTrue("非里番详情页不应出现竖版卡片", videos.none { it.isPortraitCover })
    }

    @Test
    fun `hentai related cards are portrait`() {
        val videos = parse("video-detail-hentai.html")

        val portrait = videos.filter { it.isPortraitCover }
        assertEquals("里番相关推荐共 59 条", 59, portrait.size)
        assertTrue(portrait.all { it.thumbnailUrl.contains("/image/cover/") })
        assertFalse(videos.none { it.isPortraitCover })
    }

    @Test
    fun `hentai related cards exclude playlist cards`() {
        val videos = parse("video-detail-hentai.html")
        assertTrue("播放集合卡片不应进入相关推荐", videos.none { it.id == "408309" })
    }
}
