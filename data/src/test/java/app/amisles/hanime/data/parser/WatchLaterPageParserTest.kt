package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLaterPageParserTest {

    private val parser = WatchLaterPageParser(VideoListParser())
    private val baseUrl = "https://hanime1.me"

    private fun card(id: String, title: String, duration: String = "12:34") = """
        <div class="video-item-container">
          <a class="video-link" href="/watch?v=$id">
            <img class="main-thumb" src="https://cdn.example.com/$id.jpg">
            <div class="duration">$duration</div>
            <div class="title">$title</div>
          </a>
          <div class="subtitle"><a href="/artist/1">作者甲</a><span class="subtitle-time">• 1 天前</span></div>
        </div>
    """.trimIndent()

    @Test
    fun `parses standard video cards`() {
        val videos = parser.parse(card("12345", "已保存的影片一"), baseUrl)

        assertEquals(1, videos.size)
        val video = videos.first()
        assertEquals("12345", video.id)
        assertEquals("已保存的影片一", video.title)
        assertEquals("12:34", video.duration)
        assertEquals("作者甲", video.author)
        assertEquals("https://hanime1.me/watch?v=12345", video.videoUrl)
        assertEquals("https://cdn.example.com/12345.jpg", video.thumbnailUrl)
    }

    @Test
    fun `keeps page order and drops duplicates`() {
        val html = card("222", "第二个") + card("111", "第一个") + card("222", "第二个")

        val videos = parser.parse(html, baseUrl)

        assertEquals(listOf("222", "111"), videos.map { it.id })
    }

    @Test
    fun `falls back to watch links when card markup is unknown`() {
        val html = """
            <div class="saves-list">
              <a class="save-entry" href="/watch?v=777">
                <img src="https://cdn.example.com/777.jpg">
                <div class="title">兜底标题</div>
                <span class="duration">05:00</span>
              </a>
              <a class="save-entry" href="https://hanime1.me/watch?v=888">
                <img src="https://cdn.example.com/888.jpg" alt="无标题兜底">
              </a>
            </div>
        """.trimIndent()

        val videos = parser.parse(html, baseUrl)

        assertEquals(listOf("777", "888"), videos.map { it.id })
        assertEquals("兜底标题", videos[0].title)
        assertEquals("05:00", videos[0].duration)
        assertEquals("无标题兜底", videos[1].title)
    }

    @Test
    fun `parses portrait cards`() {
        val html = """
            <div class="home-rows-videos-div">
              <a href="/watch?v=9090"><img src="https://cdn.example.com/9090.jpg"></a>
              <div class="home-rows-videos-title">竖版影片</div>
            </div>
        """.trimIndent()

        val videos = parser.parse(html, baseUrl)

        assertEquals(1, videos.size)
        assertEquals("9090", videos.first().id)
        assertEquals("竖版影片", videos.first().title)
        assertTrue(videos.first().isPortraitCover)
    }

    @Test
    fun `returns empty list when page has no videos`() {
        val videos = parser.parse("<div class=\"saves-empty\">还没有稍后观看的影片</div>", baseUrl)

        assertTrue(videos.isEmpty())
    }
}
