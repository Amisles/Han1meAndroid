package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPlaylistPageRealHtmlTest {

    private val parser = PlaylistParser()
    private val baseUrl = "https://hanime1.me"

    @Test
    fun `user playlist page yields the playlists with video counts`() {
        val page = Fixtures.html("playlist-list.html")

        val playlists = parser.parseListPage(page, baseUrl)

        assertEquals(listOf("test2", "test"), playlists.map { it.title })
        assertTrue(playlists.all { it.playlistUrl.contains("/playlist?list=") })
        assertEquals(listOf("2 部影片", "0 部影片"), playlists.map { it.videoCount })
        assertTrue(playlists.all { it.author == "ferime" })
    }

    @Test
    fun `playlist detail page yields header and videos`() {
        val page = Fixtures.html("playlist-detail.html")

        val detail = parser.parseDetailPage(page, baseUrl)!!

        assertEquals("test2", detail.title)
        assertEquals("ferime", detail.author)
        assertEquals(2, detail.videoCount)
        assertEquals("1223231313", detail.description)
        assertEquals(2, detail.videos.size)
        assertTrue(detail.videos.all { it.id.isNotEmpty() })
        assertEquals("409009", detail.videos.first().id)
    }
}
