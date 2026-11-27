package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.SubtitleLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VideoSubtitleRealHtmlTest {

    private lateinit var parser: WatchPageParser
    private val baseUrl = "https://hanime1.me"

    @Before
    fun setUp() {
        parser = WatchPageParser(VideoListParser(), PlaylistParser())
    }

    private fun parse(name: String) = parser.parse(Fixtures.html(name), baseUrl)

    @Test
    fun `simplified page carries simplified hardsub sources`() {
        val detail = parse("video-detail-zhs.html")
        assertNotNull(detail)
        assertEquals(SubtitleLang.SIMPLIFIED, detail!!.subtitleLang)
        assertEquals(3, detail.videoSources.size)
        assertTrue(detail.videoSources.all { it.url.contains("-sc-") })
    }

    @Test
    fun `traditional page carries the other signed sources`() {
        val detail = parse("video-detail-zht.html")
        assertNotNull(detail)
        assertEquals(SubtitleLang.TRADITIONAL, detail!!.subtitleLang)
        assertEquals(3, detail.videoSources.size)
        assertFalse(detail.videoSources.any { it.url.contains("-sc-") })
    }

    @Test
    fun `single variant page offers no switch`() {
        val detail = parse("video-detail-hentai.html")
        assertEquals("", detail!!.subtitleLang)
    }
}
