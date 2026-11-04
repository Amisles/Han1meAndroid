package app.amisles.hanime.data.parser

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSaveResponseParserTest {

    private val parser = PlaylistSaveResponseParser(SaveOptionsParser())

    private fun saveResponse(icon: String, token: String = "token_abc") = """
        {
          "saveBtn": "<div id=\"video-save-btn\"><div class=\"single-icon no-select\"><i class=\"material-icons-outlined\">$icon<\/i>已储存<\/div><\/div>",
          "saveBtnMore": "<i id=\"video-share-btn\" class=\"material-icons\">$icon<\/i><span>已储存<\/span>",
          "csrf_token": "$token"
        }
    """.trimIndent()

    private val createdResponse = """
        {
          "checkbox": "<div class=\"form-check playlist-checkbox-wrapper\" style=\"\">\n  <label class=\"playlist-checkbox-container\" style=\"position: relative;\">\n    <span style=\"...\">test2<\/span>\n    <input id=\"1055655\" class=\"playlist-checkbox\" type=\"checkbox\" checked>\n    <span class=\"playlist-checkmark\"><\/span>\n    <img style=\"...\" src=\"https:\/\/cdn.example.com\/image\/icon\/save_playlist_public.png?secure=x\">\n  <\/label>\n<\/div>",
          "saveBtn": "<div><i class=\"material-icons-outlined\">playlist_add_check<\/i>已储存<\/div>",
          "csrf_token": "token_new"
        }
    """.trimIndent()

    @Test
    fun `saved response is detected from the check icon`() {
        val result = parser.parseSave(saveResponse("playlist_add_check"))
        assertTrue(result.saved)
        assertEquals("token_abc", result.csrfToken)
    }

    @Test
    fun `unsaved response is detected from the plain icon`() {
        assertFalse(parser.parseSave(saveResponse("playlist_add")).saved)
    }

    @Test
    fun `create response exposes the new playlist id and title`() {
        val created = parser.parseCreatedPlaylist(createdResponse)
        assertEquals("1055655", created.remoteId)
        assertEquals("test2", created.title)
        assertEquals("token_new", created.csrfToken)
    }

    @Test(expected = IOException::class)
    fun `create response without checkbox throws`() {
        parser.parseCreatedPlaylist("""{"csrf_token":"t"}""")
    }

    @Test(expected = IOException::class)
    fun `invalid json throws`() {
        parser.parseSave("not json")
    }
}