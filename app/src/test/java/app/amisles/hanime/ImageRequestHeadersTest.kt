package app.amisles.hanime

import app.amisles.hanime.core.common.util.BrowserUserAgent
import app.amisles.hanime.data.remote.VideoAntiHotlink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.Request

/**
 * 图片 CDN 按 User-Agent 判定浏览器：请求不带浏览器 UA 时整片缩略图 403 空白
 * （Coil 默认的 okhttp/x.y.z 会被直接拒掉）。这里锁住两个头，避免以后又被漏掉。
 */
class ImageRequestHeadersTest {

    private fun request(): Request = Request.Builder()
        .url("https://simg-65.example.cc/27,abc/image/thumbnail/408811l.jpg?secure=xyz==")
        .build()

    @Test
    fun `image request carries browser user agent`() {
        val header = ImageHttpClient.withImageHeaders(request()).header("User-Agent")
        assertEquals(BrowserUserAgent.VALUE, header)
        assertTrue("不能再用 OkHttp 默认 UA", header!!.startsWith("Mozilla/5.0"))
    }

    @Test
    fun `image request carries anti hotlink referer`() {
        val header = ImageHttpClient.withImageHeaders(request()).header(VideoAntiHotlink.REFERER_HEADER)
        assertTrue("Referer 必须存在，否则防盗链 CDN 返回 403", header != null && header.isNotBlank())
        assertTrue("Referer 需以 / 结尾，与视频直链保持一致", header!!.endsWith("/"))
    }

    @Test
    fun `image request keeps original url and headers`() {
        val original = request().newBuilder().header("Accept", "image/*").build()
        val patched = ImageHttpClient.withImageHeaders(original)
        assertEquals(original.url, patched.url)
        assertEquals("image/*", patched.header("Accept"))
    }
}
