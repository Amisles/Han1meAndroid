package app.amisles.hanime.feature.detail.comment

import app.amisles.hanime.domain.model.Comment
import app.amisles.hanime.domain.model.Reply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentOptimisticTest {

    private fun comment(
        id: String,
        likeCount: Int = 0,
        likeStatus: Int = 0,
        replyCount: Int = 0
    ) = Comment(
        id = id,
        username = "u",
        avatarUrl = "",
        time = "刚刚",
        content = "c",
        likeCount = likeCount,
        replyCount = replyCount,
        likeStatus = likeStatus
    )

    private fun reply(id: String) = Reply(
        id = id,
        username = "u",
        avatarUrl = "",
        time = "刚刚",
        content = "r",
        likeCount = 0,
        replyTo = null
    )

    @Test
    fun `like toggles on and increments count`() {
        val liked = CommentOptimistic.toggleLike(comment("1", likeCount = 3, likeStatus = 0))
        assertEquals(1, liked.likeStatus)
        assertEquals(4, liked.likeCount)
    }

    @Test
    fun `like toggles off and decrements count`() {
        val unliked = CommentOptimistic.toggleLike(comment("1", likeCount = 3, likeStatus = 1))
        assertEquals(0, unliked.likeStatus)
        assertEquals(2, unliked.likeCount)
    }

    @Test
    fun `like count never goes negative`() {
        val unliked = CommentOptimistic.toggleLike(comment("1", likeCount = 0, likeStatus = 1))
        assertEquals(0, unliked.likeStatus)
        assertEquals(0, unliked.likeCount)
    }

    @Test
    fun `reply count adjusts only the target comment`() {
        val list = listOf(comment("a", replyCount = 1), comment("b", replyCount = 5))
        val incremented = CommentOptimistic.adjustReplyCount(list, "a", 1)
        assertEquals(2, incremented.first { it.id == "a" }.replyCount)
        assertEquals(5, incremented.first { it.id == "b" }.replyCount)

        val decremented = CommentOptimistic.adjustReplyCount(incremented, "a", -1)
        assertEquals(1, decremented.first { it.id == "a" }.replyCount)
    }

    @Test
    fun `reply count never goes negative`() {
        val list = listOf(comment("a", replyCount = 0))
        assertEquals(0, CommentOptimistic.adjustReplyCount(list, "a", -1).first().replyCount)
    }

    @Test
    fun `append reply keeps existing entries`() {
        val cache = mapOf("a" to listOf(reply("r1")))
        val appended = CommentOptimistic.appendReply(cache, "a", reply("local"))
        assertEquals(listOf("r1", "local"), appended.getValue("a").map { it.id })
    }

    @Test
    fun `replace reply swaps temporary for server reply`() {
        val cache = mapOf("a" to listOf(reply("r1"), reply("local")))
        val replaced = CommentOptimistic.replaceReply(cache, "a", "local", reply("server"))
        assertEquals(listOf("r1", "server"), replaced.getValue("a").map { it.id })
    }

    @Test
    fun `remove reply drops only the temporary entry`() {
        val cache = mapOf("a" to listOf(reply("r1"), reply("local")))
        val removed = CommentOptimistic.removeReply(cache, "a", "local")
        assertEquals(listOf("r1"), removed.getValue("a").map { it.id })
        assertTrue(removed.containsKey("a"))
    }

    @Test
    fun `remove reply ignores unknown comment`() {
        val removed = CommentOptimistic.removeReply(emptyMap(), "missing", "local")
        assertNull(removed["missing"])
    }
}
