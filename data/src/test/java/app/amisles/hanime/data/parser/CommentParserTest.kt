package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回复数文案解析。
 *
 * 只覆盖纯字符串规则：CommentParser.parse() 依赖 android.util.JSONObject，
 * 在 JVM 单测里不可用（未启用 returnDefaultValues / Robolectric）。
 */
class CommentParserTest {

    @Test
    fun `reply count with thousands separator`() {
        // 站点会给上千的回复数加千分位；旧实现只取第一段数字，得到 1
        assertEquals(1234, parseReplyCountFromText("查看 1,234 則回覆"))
    }

    @Test
    fun `reply count without separator`() {
        assertEquals(3, parseReplyCountFromText("查看 3 則回覆"))
    }

    @Test
    fun `reply count with multiple separators`() {
        assertEquals(1234567, parseReplyCountFromText("查看 1,234,567 則回覆"))
    }

    @Test
    fun `reply count with no digits is zero`() {
        assertEquals(0, parseReplyCountFromText("查看回覆"))
    }
}
