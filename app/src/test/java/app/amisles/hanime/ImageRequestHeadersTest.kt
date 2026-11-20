package app.amisles.hanime

import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.data.remote.VideoAntiHotlinkProvider
import app.amisles.hanime.domain.remote.VideoAntiHotlink
import app.amisles.hanime.domain.repository.SiteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Request

/**
 * 图片 CDN 按 User-Agent 判定浏览器：请求不带浏览器 UA 时整片缩略图 403 空白
 * （Coil 默认的 okhttp/x.y.z 会被直接拒掉）。这里锁住两个头，避免以后又被漏掉。
 */
class ImageRequestHeadersTest {

    private val client = ImageHttpClient(
        VideoAntiHotlinkProvider(
            object : SiteSettings {
                override val baseUrl: String = "https://hanime1.me"
                override val baseUrlFlow: StateFlow<String> = MutableStateFlow(baseUrl)
                override fun setBaseUrl(url: String): Boolean = true
                override val isLoginSupported: Boolean = true
                override val loginSupportedFlow: StateFlow<Boolean> = MutableStateFlow(true)
                override val subscriptionReminder: Boolean = false
                override val subscriptionReminderFlow: StateFlow<Boolean> = MutableStateFlow(false)
                override fun setSubscriptionReminder(enabled: Boolean) = Unit
                override val subscriptionSeenVideoIds: Set<String> = emptySet()
                override fun setSubscriptionSeenVideoIds(ids: Set<String>) = Unit
            }
        )
    )

    private fun request(): Request = Request.Builder()
        .url("https://simg-65.example.cc/27,abc/image/thumbnail/408811l.jpg?secure=xyz==")
        .build()

    @Test
    fun `image request carries browser user agent`() {
        val header = client.withImageHeaders(request()).header("User-Agent")
        assertEquals(BrowserUserAgent.VALUE, header)
        assertTrue("不能再用 OkHttp 默认 UA", header!!.startsWith("Mozilla/5.0"))
    }

    @Test
    fun `image request carries anti hotlink referer`() {
        val header = client.withImageHeaders(request()).header(VideoAntiHotlink.HEADER_NAME)
        assertTrue("Referer 必须存在，否则防盗链 CDN 返回 403", header != null && header.isNotBlank())
        assertTrue("Referer 需以 / 结尾，与视频直链保持一致", header!!.endsWith("/"))
    }

    @Test
    fun `image request keeps original url and headers`() {
        val original = request().newBuilder().header("Accept", "image/*").build()
        val patched = client.withImageHeaders(original)
        assertEquals(original.url, patched.url)
        assertEquals("image/*", patched.header("Accept"))
    }
}
