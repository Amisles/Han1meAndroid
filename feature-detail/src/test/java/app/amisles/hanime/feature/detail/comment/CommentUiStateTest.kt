package app.amisles.hanime.feature.detail.comment

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentUiStateTest {

    @Test
    fun `reply prefill is empty without a target`() {
        assertEquals("", CommentUiState().replyPrefill)
    }

    @Test
    fun `reply prefill mentions the replied user`() {
        val state = CommentUiState(replyTarget = ReplyTarget("c1", "alice"))

        assertEquals("@alice ", state.replyPrefill)
    }

    @Test
    fun `replying to a comment keeps an empty prefill`() {
        val state = CommentUiState(replyTarget = ReplyTarget("c1"))

        assertEquals("", state.replyPrefill)
    }
}
