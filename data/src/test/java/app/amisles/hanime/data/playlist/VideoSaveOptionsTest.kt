package app.amisles.hanime.data.playlist

import app.amisles.hanime.domain.model.PlaylistSaveOption
import app.amisles.hanime.domain.model.PlaylistTargets
import app.amisles.hanime.domain.model.VideoSaveOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSaveOptionsTest {

    private val options = VideoSaveOptions(
        videoId = "409004",
        csrfToken = "token",
        options = listOf(
            PlaylistSaveOption(PlaylistTargets.WATCH_LATER, "稍后观看", true, true),
            PlaylistSaveOption("1055606", "test", false, true),
            PlaylistSaveOption("1055655", "test2", false, false)
        )
    )

    @Test
    fun `saved targets only contain checked options`() {
        assertEquals(setOf(PlaylistTargets.WATCH_LATER, "1055606"), options.savedTargets)
    }

    @Test
    fun `favorite options drop the private watch later entry`() {
        assertEquals(listOf("1055606", "1055655"), options.favoriteOptions.map { it.target })
    }

    @Test
    fun `watch later state follows its checkbox`() {
        assertTrue(options.watchLaterSaved)
        val unchecked = options.copy(
            options = options.options.map {
                if (PlaylistTargets.isWatchLater(it.target)) it.copy(selected = false) else it
            }
        )
        assertFalse(unchecked.watchLaterSaved)
    }

    @Test
    fun `empty options fall back to nothing saved`() {
        val empty = VideoSaveOptions(videoId = "1")
        assertEquals(emptySet<String>(), empty.savedTargets)
        assertTrue(empty.favoriteOptions.isEmpty())
        assertFalse(empty.watchLaterSaved)
    }
}
