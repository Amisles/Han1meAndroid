package app.amisles.hanime.feature.home

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分区累积与空响应判定的回归测试。
 *
 * 三条不变量：
 * 1. 同名但链接不同的分区共存（H-03：旧实现按标题作 Map 键，后者覆盖前者）；
 * 2. 同一分区重放时原位替换，不产生重复区块；
 * 3. 刷新成功但一个分区都没拿到（H-04），必须进入错误态而不是留下空白页。
 */
class HomeSectionMergeTest {

    private fun video(id: String) = HanimeVideo(
        id = id,
        title = "title-$id",
        thumbnailUrl = "",
        duration = "",
        likeRate = "",
        viewCount = "",
        author = "",
        publishTime = "",
        videoUrl = "https://hanime.tv/videos/hentai/$id"
    )

    private fun section(title: String, moreUrl: String, vararg ids: String) =
        HomeSection(title = title, moreUrl = moreUrl, videos = ids.map(::video))

    // ---- H-03 ----

    @Test
    fun `same title with different urls keeps both sections`() {
        val target = mutableListOf<HomeSection>()

        mergeSection(target, section("最新上传", "/sort/uploaded", "a", "b"))
        mergeSection(target, section("最新上传", "/sort/uploaded-hot", "c"))

        assertEquals(2, target.size)
        assertEquals(listOf("a", "b"), target[0].videos.map { it.id })
        assertEquals(listOf("c"), target[1].videos.map { it.id })
    }

    @Test
    fun `replayed section replaces in place without reordering`() {
        val target = mutableListOf<HomeSection>()
        mergeSection(target, section("最新上市", "/sort/latest", "a"))
        mergeSection(target, section("本日排行", "/sort/daily", "b"))

        mergeSection(target, section("最新上市", "/sort/latest", "a", "c"))

        assertEquals(2, target.size)
        assertEquals("最新上市", target[0].title)
        assertEquals(listOf("a", "c"), target[0].videos.map { it.id })
        assertEquals("本日排行", target[1].title)
    }

    @Test
    fun `three sections sharing one title are all retained`() {
        val target = mutableListOf<HomeSection>()
        listOf("/sort/a", "/sort/b", "/sort/c").forEachIndexed { index, url ->
            mergeSection(target, section("排行", url, "v$index"))
        }

        assertEquals(3, target.size)
        assertEquals(listOf("v0", "v1", "v2"), target.map { it.videos.single().id })
    }

    @Test
    fun `sections are compared by title and url together`() {
        val base = section("最新上市", "/sort/latest", "a")

        assertTrue(sameSection(base, base.copy(videos = emptyList())))
        assertFalse(sameSection(base, base.copy(moreUrl = "/sort/other")))
        assertFalse(sameSection(base, base.copy(title = "最新上传")))
    }

    // ---- H-04 ----

    @Test
    fun `successful response without sections counts as empty`() {
        assertTrue(isHomeResponseEmpty(streamFailed = false, error = null, receivedSectionCount = 0))
    }

    @Test
    fun `failed response is not treated as empty`() {
        assertFalse(isHomeResponseEmpty(streamFailed = true, error = null, receivedSectionCount = 0))
        assertFalse(
            isHomeResponseEmpty(
                streamFailed = false,
                error = "加载首页失败",
                receivedSectionCount = 0
            )
        )
    }

    @Test
    fun `response with sections is not empty`() {
        assertFalse(isHomeResponseEmpty(streamFailed = false, error = null, receivedSectionCount = 1))
    }
}
