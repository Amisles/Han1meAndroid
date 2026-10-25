package app.amisles.hanime

import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「查看更多」跳转目标的回归测试。
 *
 * 旧实现只用分区标题反查排序选项，标题对不上便退化成 keyword=<标题> 的关键词搜索，
 * 向站点发出的是一次按标题文本的搜索，结果与分区内容无关（H-02）；
 * 而解析器早已拿到的 moreUrl 从未被读取（H-05）。
 */
class HomeViewMoreRoutingTest {

    private val base = "https://hanime.tv"

    private fun section(title: String, moreUrl: String) = HomeSection(
        title = title,
        moreUrl = moreUrl,
        videos = listOf(
            HanimeVideo(
                id = "1",
                title = "t",
                thumbnailUrl = "",
                duration = "",
                likeRate = "",
                viewCount = "",
                author = "",
                publishTime = "",
                videoUrl = "$base/videos/hentai/1"
            )
        )
    )

    @Test
    fun `more url drives sort value instead of title lookup`() {
        val s = section("站点自定义标题", "$base/sort/weekly-top")

        assertEquals("weekly-top", sortApiValueOf(s))
    }

    @Test
    fun `more url wins over a matching known title`() {
        val s = section("本日排行", "$base/sort/monthly")

        assertEquals("monthly", sortApiValueOf(s))
    }

    @Test
    fun `percent encoded sort value is decoded`() {
        val s = section("觀看次數", "$base/sort/%E8%A7%80%E7%9C%8B%E6%AC%A1%E6%95%B8")

        assertEquals("觀看次數", sortApiValueOf(s))
    }

    @Test
    fun `query string and fragment are stripped from sort value`() {
        val s = section("本日排行", "$base/sort/daily?page=2#top")

        assertEquals("daily", sortApiValueOf(s))
    }

    @Test
    fun `known title is still used when more url is absent`() {
        val s = section("最新上市", "")

        assertEquals("最新上市", sortApiValueOf(s))
    }

    @Test
    fun `unknown section without more url has no route`() {
        assertNull(sortApiValueOf(section("热门推荐", "")))
        assertNull(moreUrlPageRoute(""))
    }

    @Test
    fun `non sort more url maps to a real page instead of keyword search`() {
        val tag = moreUrlPageRoute("$base/tag/abc")
        val author = moreUrlPageRoute("$base/author/12345")
        val playlist = moreUrlPageRoute("$base/playlist/999")

        assertTrue(tag!!.startsWith("search?keyword="))
        assertTrue(author!!.startsWith("author?authorPageUrl="))
        assertTrue(playlist!!.startsWith("playlistDetail?url="))
    }

    @Test
    fun `sort more url maps to the full list page`() {
        val route = moreUrlPageRoute("$base/sort/weekly-top")

        assertTrue(route!!.startsWith("videoListPage?url="))
    }

    @Test
    fun `every routed page carries the original url`() {
        val sort = moreUrlPageRoute("https://hanime.tv/sort/weekly-top")!!
        val author = moreUrlPageRoute("https://hanime.tv/author/42")!!

        assertTrue(sort.contains("url="))
        assertTrue(author.contains("authorPageUrl="))
        assertTrue(sort.substringAfter("url=").isNotEmpty())
        assertTrue(author.substringAfter("authorPageUrl=").isNotEmpty())
    }

    @Test
    fun `unrecognised path yields no route rather than a keyword search`() {
        assertNull(moreUrlPageRoute("$base/unknown-section/x"))
        assertNull(moreUrlPageRoute("$base/"))
    }

    @Test
    fun `genre more url maps to a genre search instead of the raw list page`() {
        val s = section("里番", "$base/genre/%E8%A3%8F%E7%95%AA")

        assertNull("排序值不应命中分类分区", sortApiValueOf(s))
        assertEquals("裏番", genreApiValueOf(s))
    }

    @Test
    fun `genre section title matches even when more url is a genre path`() {
        assertEquals("泡麵番", genreApiValueOf(section("泡面番", "$base/genre/%E6%B3%A1%E9%BA%B5%E7%95%AA")))
        assertEquals("2D動畫", genreApiValueOf(section("2D动画", "$base/genre/2D%E5%8B%95%E7%95%AB")))
    }

    @Test
    fun `genre title is used when more url is absent`() {
        assertEquals("裏番", genreApiValueOf(section("里番", "")))
        assertEquals("3DCG", genreApiValueOf(section("3DCG", "")))
    }

    @Test
    fun `accepts singular and plural genre paths`() {
        assertEquals("裏番", moreUrlGenreToken("$base/genre/%E8%A3%8F%E7%95%AA"))
        assertEquals("裏番", moreUrlGenreToken("$base/genres/%E8%A3%8F%E7%95%AA"))
    }

    @Test
    fun `genre token strips query fragment and trailing slash`() {
        assertEquals("裏番", moreUrlGenreToken("$base/genre/%E8%A3%8F%E7%95%AA/?page=2#top"))
    }

    @Test
    fun `sort section is not mistaken for a genre section`() {
        val s = section("最新上市", "$base/sort/latest")

        assertEquals("latest", sortApiValueOf(s))
        assertNull("排序分区不应命中分类", genreApiValueOf(s))
    }

    @Test
    fun `unknown genre path yields no genre route`() {
        assertNull(genreApiValueOf(section("未知分区", "$base/genre/unknown-genre")))
        assertNull(moreUrlGenreToken("$base/sort/latest"))
        assertNull(moreUrlGenreToken(""))
    }

    @Test
    fun `section with neither sort nor genre falls back to the raw page`() {
        val s = section("站点自定义分区", "$base/sort/upcoming")

        assertEquals("upcoming", sortApiValueOf(s))
        assertNull(genreApiValueOf(s))
        assertTrue(moreUrlPageRoute(s.moreUrl)!!.startsWith("videoListPage?url="))
    }
}
