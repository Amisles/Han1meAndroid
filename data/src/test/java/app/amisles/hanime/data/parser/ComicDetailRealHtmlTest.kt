package app.amisles.hanime.data.parser

import app.amisles.hanime.domain.model.ComicChapter
import app.amisles.hanime.domain.model.ComicDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComicDetailRealHtmlTest {

    private val baseUrl = "https://hanimeone.me"

    private fun load(name: String): String? {
        val file = File("../docs/" + name)
        return if (file.exists()) file.readText() else null
    }

    @Test
    fun parsesRealComicDetailPage() {
        val html = load("漫画详情.html") ?: return
        val detail: ComicDetail = ComicDetailParser().parse(html, baseUrl, "166210")

        println("title=" + detail.title)
        println("original=" + detail.originalTitle)
        println("cover=" + detail.coverUrl)
        println("tags=" + detail.tags)
        println("artists=" + detail.artists + " languages=" + detail.languages + " categories=" + detail.categories)
        println("pageCount=" + detail.pageCount + " uploaded=" + detail.uploadedAt)
        println("thumbs=" + detail.pageThumbnails.size + " related=" + detail.relatedSections.map { it.title + ":" + it.items.size })

        assertEquals("166210", detail.id)
        assertTrue("标题应含作品名", detail.title.contains("Virginal Relna"))
        assertTrue("原文标题应含日文名", detail.originalTitle.contains("レルナ"))
        assertTrue("封面应指向 nhentai 图库", detail.coverUrl.contains("nhentai.net/galleries/4230136/cover"))
        assertEquals(listOf("巨乳", "群交", "全彩", "眼鏡娘", "強制", "泳裝", "項圈", "rough translation", "乳房擴張"), detail.tags)
        assertEquals(listOf("neo gentle"), detail.artists)
        assertEquals(listOf("翻譯", "中文"), detail.languages)
        assertEquals(listOf("漫畫"), detail.categories)
        assertEquals(572, detail.pageCount)
        assertEquals("2週前", detail.uploadedAt)
        assertEquals(572, detail.pageThumbnails.size)
        assertEquals(1, detail.pageThumbnails.first().number)
        assertTrue("缩略图应取 data-srcset 而非占位图", detail.pageThumbnails.first().thumbnailUrl.contains("t2.nhentai.net"))
        assertTrue("相关推荐应至少解析出一个分区", detail.relatedSections.isNotEmpty())
        assertTrue(
            "相关分区应含相关集数列表",
            detail.relatedSections.any { it.title.contains("相關集數") }
        )
    }

    @Test
    fun parsesRealComicReaderPage() {
        val html = load("浏览页面.html") ?: return
        val chapter: ComicChapter = ComicReaderParser().parse(html, baseUrl, "166210")

        println("pageCount=" + chapter.pageCount)
        println("first=" + chapter.pageUrl(1))
        println("second=" + chapter.pageUrl(2))
        println("last=" + chapter.pageUrl(572))

        assertEquals(572, chapter.pageCount)
        assertEquals(572, chapter.pages.size)
        assertEquals("https://i2.nhentai.net/galleries/4230136/1.webp", chapter.pageUrl(1))
        assertEquals("https://i2.nhentai.net/galleries/4230136/2.webp", chapter.pageUrl(2))
        assertEquals("https://i2.nhentai.net/galleries/4230136/572.webp", chapter.pageUrl(572))
        assertTrue("页码越界应返回 null", chapter.pageUrl(573) == null)
    }
}
