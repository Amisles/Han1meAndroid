package app.amisles.hanime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 从内置浏览器 Cookie 串里提取 `cf_clearance`。
 *
 * 只取这一段而不是整串：WebView 里同样可能有会话 Cookie，把它们塞进校验槽位等于
 * 让会话凭据跟着「校验域名」到处走。
 */
class CloudflareCookieTest {

    @Test
    fun picksClearanceAmongOtherCookies() {
        val raw = "laravel_session=abc; cf_clearance=xyz.789; user_lang=zh"
        assertEquals("cf_clearance=xyz.789", clearanceCookie(raw))
    }

    @Test
    fun picksClearanceAtStart() {
        assertEquals("cf_clearance=token", clearanceCookie("cf_clearance=token; a=b"))
    }

    @Test
    fun ignoresEmptyValue() {
        assertNull(clearanceCookie("cf_clearance=; a=b"))
    }

    @Test
    fun returnsNullWhenAbsent() {
        assertNull(clearanceCookie("laravel_session=abc; user_lang=zh"))
    }

    @Test
    fun handlesBlankInput() {
        assertNull(clearanceCookie(""))
    }

    @Test
    fun matchesPrefixOnlyOnce() {
        // cf_clearance_extra 不是目标 Cookie，不能被前缀命中
        assertNull(clearanceCookie("cf_clearance_extra=1; a=b"))
    }
}
