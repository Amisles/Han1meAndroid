package app.amisles.hanime.ui.theme

import app.amisles.hanime.domain.remote.VideoAntiHotlink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 防盗链 Referer 必须跟随站点设置变化。
 *
 * 适配层曾把 antiHotlinkReferer 写成 val，只在 object 初始化时求值一次：
 * 用户在设置页把站点改成另一个镜像后，Referer 仍是旧域名，直链请求被 CDN 判为盗链。
 * val 会编译出一个存储字段，get() 形式只有 getter，据此固定实现形态。
 */
class AntiHotlinkRefererTest {

    @Test
    fun `referer is not cached in a backing field`() {
        val cached = runCatching {
            PreferencesAppPreferences::class.java.getDeclaredField("antiHotlinkReferer")
        }.getOrNull()

        assertNull("antiHotlinkReferer 被缓存成字段，改站点后不会生效", cached)
    }

    @Test
    fun `referer always ends with a single slash`() {
        assertEquals("https://hanime1.me/", VideoAntiHotlink.refererOf("https://hanime1.me"))
        assertEquals("https://hanime1.me/", VideoAntiHotlink.refererOf("https://hanime1.me/"))
        assertEquals("https://hanime2.one/", VideoAntiHotlink.refererOf("https://hanime2.one"))
    }

    @Test
    fun `header name is referer`() {
        assertEquals("Referer", VideoAntiHotlink.HEADER_NAME)
    }
}
