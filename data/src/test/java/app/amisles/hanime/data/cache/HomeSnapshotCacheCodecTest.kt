package app.amisles.hanime.data.cache

import app.amisles.hanime.domain.model.HanimeBanner
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSnapshotCacheCodecTest {

    private val video = HanimeVideo(
        id = "42",
        title = "标题",
        thumbnailUrl = "https://cdn.example.com/42.jpg",
        duration = "12:34",
        likeRate = "95%",
        viewCount = "1.2万",
        author = "作者",
        publishTime = "2026-11-12",
        videoUrl = "https://hanime1.me/watch?v=42",
        isPortraitCover = true
    )

    private val banner = HanimeBanner(
        title = "横幅",
        author = "作者",
        viewCount = "3.4万",
        publishTime = "2026-11-11",
        tags = listOf("里番", "原创"),
        videoUrl = "https://hanime1.me/watch?v=1",
        imageUrl = "https://cdn.example.com/1.jpg"
    )

    private val sections = listOf(
        HomeSection(title = "最新上传", moreUrl = "https://hanime1.me/latest", videos = listOf(video))
    )

    @Test
    fun encodeAndDecodeKeepEveryField() {
        val raw = HomeSnapshotCacheCodec.encode("https://hanime1.me", banner, sections)

        val snapshot = HomeSnapshotCacheCodec.decode(raw)

        assertEquals("https://hanime1.me", snapshot?.siteUrl)
        assertEquals(banner, snapshot?.banner)
        assertEquals(sections, snapshot?.sections)
    }

    @Test
    fun encodeAndDecodeKeepNullBanner() {
        val raw = HomeSnapshotCacheCodec.encode("https://hanime1.me", null, sections)

        val snapshot = HomeSnapshotCacheCodec.decode(raw)

        assertNull(snapshot?.banner)
        assertEquals(sections, snapshot?.sections)
    }

    @Test
    fun decodeRejectsMalformedPayload() {
        assertNull(HomeSnapshotCacheCodec.decode(""))
        assertNull(HomeSnapshotCacheCodec.decode("not json"))
    }

    @Test
    fun decodeTreatsMissingMembersAsEmpty() {
        val snapshot = HomeSnapshotCacheCodec.decode("{}")

        assertEquals("", snapshot?.siteUrl)
        assertNull(snapshot?.banner)
        assertEquals(emptyList<HomeSection>(), snapshot?.sections)
    }
}
