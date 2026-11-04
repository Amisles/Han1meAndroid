package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.PlaylistTargets
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveOptionsParserTest {

    private val parser = SaveOptionsParser()

    private val pageWithModal = """
        <html><head><meta name="csrf-token" content="meta_token"></head><body>
        <div id="playlistModal" class="modal">
          <form id="video-save-form" action="https://hanime1.me/save">
            <input type="hidden" name="_token" value="form_token">
            <input id="playlist-video-id" name="playlist-video-id" type="hidden" value="409004">
            <div id="create-playlist-btn" class="form-check playlist-checkbox-wrapper">
              <label class="playlist-checkbox-container"><span>新增播放清单</span></label>
            </div>
            <span id="playlist-save-checkbox">
              <div class="form-check playlist-checkbox-wrapper">
                <label class="playlist-checkbox-container">
                  <span>稍后观看</span>
                  <input id="save" class="playlist-checkbox" type="checkbox" checked>
                  <img src="https://cdn.example.com/icon/save_playlist_private.png?secure=x">
                </label>
              </div>
            </span>
            <div class="form-check playlist-checkbox-wrapper">
              <label class="playlist-checkbox-container">
                <span>test</span>
                <input id="1055606" class="playlist-checkbox" type="checkbox" checked>
                <img src="https://cdn.example.com/icon/save_playlist_public.png?secure=y">
              </label>
            </div>
            <div class="form-check playlist-checkbox-wrapper">
              <label class="playlist-checkbox-container">
                <span>test2</span>
                <input id="1055655" class="playlist-checkbox" type="checkbox">
              </label>
            </div>
          </form>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parse reads video id and csrf token from the form`() {
        val options = parser.parse(Jsoup.parse(pageWithModal, "https://hanime1.me"))
        assertEquals("409004", options.videoId)
        assertEquals("form_token", options.csrfToken)
    }

    @Test
    fun `parse keeps every playlist checkbox in page order`() {
        val options = parser.parse(Jsoup.parse(pageWithModal, "https://hanime1.me"))
        assertEquals(listOf("save", "1055606", "1055655"), options.options.map { it.target })
        assertEquals(listOf("稍后观看", "test", "test2"), options.options.map { it.title })
        assertEquals(setOf("save", "1055606"), options.savedTargets)
    }

    @Test
    fun `parse marks private playlists by icon`() {
        val options = parser.parse(Jsoup.parse(pageWithModal, "https://hanime1.me"))
        assertTrue(options.options.first { PlaylistTargets.isWatchLater(it.target) }.isPrivate)
        val publicPlaylist = options.options.first { it.target == "1055606" }
        assertFalse(publicPlaylist.isPrivate)
        assertFalse(options.options.first { it.target == "1055655" }.selected)
    }

    @Test
    fun `parse falls back to page csrf token when form lacks one`() {
        val html = pageWithModal.replace("<input type=\"hidden\" name=\"_token\" value=\"form_token\">", "")
        val options = parser.parse(Jsoup.parse(html, "https://hanime1.me"), "meta_token")
        assertEquals("meta_token", options.csrfToken)
    }

    @Test
    fun `parse without the modal yields empty options`() {
        val options = parser.parse(Jsoup.parse("<html><body></body></html>"))
        assertTrue(options.options.isEmpty())
        assertEquals("", options.videoId)
        assertFalse(options.watchLaterSaved)
    }
}