package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUtilsTest {

    @Test
    fun `extractVideoId with valid URL returns numeric id`() {
        assertEquals("12345", ParserUtils.extractVideoId("https://hanime1.me/watch?v=12345"))
    }

    @Test
    fun `extractVideoId with relative URL returns numeric id`() {
        assertEquals("98765", ParserUtils.extractVideoId("/watch?v=98765"))
    }

    @Test
    fun `extractVideoId with URL containing other digits returns only v param`() {
        // 路径中的 5 不应被误识别
        assertEquals("42", ParserUtils.extractVideoId("https://hanime1.me/watch?v=42&ref=5"))
    }

    @Test
    fun `extractVideoId with missing v param returns empty`() {
        assertEquals("", ParserUtils.extractVideoId("https://hanime1.me/watch?id=12345"))
    }

    @Test
    fun `extractVideoId with non-numeric v returns empty`() {
        assertEquals("", ParserUtils.extractVideoId("https://hanime1.me/watch?v=abc"))
    }

    @Test
    fun `cleanLikeRate removes thumb-up emoji`() {
        assertEquals("95%", ParserUtils.cleanLikeRate("👍95%"))
    }

    @Test
    fun `cleanLikeRate removes thumb-up text variants`() {
        assertEquals("80%", ParserUtils.cleanLikeRate("thumb-up80%"))
        assertEquals("80%", ParserUtils.cleanLikeRate("thumb_up80%"))
        assertEquals("80%", ParserUtils.cleanLikeRate("thumb up80%"))
    }

    @Test
    fun `cleanLikeRate with already clean text returns same`() {
        assertEquals("95%", ParserUtils.cleanLikeRate("95%"))
    }

    @Test
    fun `convertToSimplified converts traditional section titles`() {
        assertEquals("最新上传", ParserUtils.convertToSimplified("最新上傳"))
        assertEquals("里番", ParserUtils.convertToSimplified("裏番"))
        assertEquals("泡面番", ParserUtils.convertToSimplified("泡麵番"))
        assertEquals("2D动画", ParserUtils.convertToSimplified("2D動畫"))
        assertEquals("H动漫", ParserUtils.convertToSimplified("H動漫"))
        assertEquals("播放清单", ParserUtils.convertToSimplified("播放清單"))
    }

    @Test
    fun `convertToSimplified keeps already-simplified text`() {
        assertEquals("最新上市", ParserUtils.convertToSimplified("最新上市"))
        assertEquals("MMD", ParserUtils.convertToSimplified("MMD"))
    }

    @Test
    fun `generatePlaceholderThumbnail embeds video id`() {
        val url = ParserUtils.generatePlaceholderThumbnail("12345")
        assertTrue("URL should contain video id", url.contains("12345"))
        assertTrue(
            "URL should point to hembed thumbnail",
            url.contains("vdownload.hembed.com/image/thumbnail/")
        )
        assertTrue("URL should end with l.jpg", url.endsWith("12345l.jpg"))
    }

    @Test
    fun `traditionalToSimplified map contains key conversion pairs`() {
        // 确认映射表未意外丢失条目
        assertEquals("他们在看", ParserUtils.traditionalToSimplified["他們在看"])
        assertEquals("2.5D", ParserUtils.traditionalToSimplified["2.5D"])
        assertEquals("Cosplay", ParserUtils.traditionalToSimplified["Cosplay"])
        assertEquals("AI生成", ParserUtils.traditionalToSimplified["AI生成"])
    }
}
