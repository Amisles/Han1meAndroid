package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 登录用户 ID 的识别：只认账户专属入口，作者链接与未登录占位都不能被当成用户 ID。 */
class AccountIdentityParserTest {

    private val parser = AccountIdentityParser()

    @Test
    fun readsUserIdFromAccountEditLink() {
        val html = """
            <div class="dropdown">
                <a href="https://hanime1.me/user/366912">Amisles</a>
                <a href="https://hanime1.me/user/366912/edit">編輯個人檔案</a>
            </div>
        """.trimIndent()

        assertEquals("366912", parser.parseCurrentUserId(html))
    }

    @Test
    fun ignoresAuthorLinks() {
        val html = """
            <a href="https://hanime1.me/user/123456">作者 A</a>
            <a href="https://hanime1.me/user/654321/uploaded">作者 B</a>
        """.trimIndent()

        assertNull(parser.parseCurrentUserId(html))
    }

    @Test
    fun fallsBackToMetaTag() {
        val html = """<meta name="user-id" content="777">"""

        assertEquals("777", parser.parseCurrentUserId(html))
    }

    @Test
    fun fallsBackToHiddenInput() {
        val html = """<form><input type="hidden" name="comment-user-id" value="888"></form>"""

        assertEquals("888", parser.parseCurrentUserId(html))
    }

    @Test
    fun rejectsPlaceholderAndMalformedIds() {
        assertNull(parser.parseCurrentUserId("""<meta name="user-id" content="0">"""))
        assertNull(parser.parseCurrentUserId("""<meta name="user-id" content="abc">"""))
        assertNull(parser.parseCurrentUserId(""))
    }
}
