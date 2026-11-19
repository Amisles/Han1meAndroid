package app.amisles.hanime.feature.detail.comment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentVideoSelectionTest {

    @Test
    fun sameVideoSkipsTheReset() {
        assertTrue(shouldSkipCommentReset(currentVideoId = "video-1", nextVideoId = "video-1"))
    }

    @Test
    fun differentVideoResets() {
        assertFalse(shouldSkipCommentReset(currentVideoId = "video-1", nextVideoId = "video-2"))
    }

    @Test
    fun leavingThePageWithEmptyIdResets() {
        assertFalse(shouldSkipCommentReset(currentVideoId = "video-1", nextVideoId = ""))
    }

    @Test
    fun emptyStateStillResets() {
        assertFalse(shouldSkipCommentReset(currentVideoId = "", nextVideoId = ""))
    }
}
