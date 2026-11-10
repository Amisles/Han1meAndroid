package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回复数文案解析。
 *
 * 只覆盖纯字符串规则：站点 HTML 的解析入口 parse() 依赖 org.json.JSONObject（data 模块
 * 已通过 testImplementation 提供真实实现），本测试只针对不依赖 HTML 解析的顶层函数。
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
