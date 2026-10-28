package app.amisles.hanime.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 订阅作者条解析。
 *
 * 关键点：页面会渲染移动端 / 桌面端两份同名卡，active 标记只出现在其中一份上，
 * 「去重」与「active 判定」的先后顺序会直接影响高亮是否正确。
 */
class SubscriptionsParserTest {

    private val parser = SubscriptionsParser(VideoListParser())
    private val baseUrl = "https://hanime1.me"

    private fun card(name: String, active: Boolean = false): String {
        val extra = if (active) " subscriptions-active-artist" else ""
        return """
            <div class="subscriptions-artist-card artist-option$extra">
                <div class="card-mobile-title search-artist-title">$name</div>
                <img src="https://cdn.example.com/avatar.jpg"/>
            </div>
        """.trimIndent()
    }

    @Test
    fun `active flag survives duplicate cards`() {
        // 先出现不带 active 的一份，后一份才带 —— 旧实现会在第一份处收录、第二份被去重跳过，
        // 导致 active 标记永远收不到
        val html = card("Author A") + card("Author A", active = true)
        val content = parser.parse(html, baseUrl)

        assertEquals(1, content.artists.size)
        assertTrue("后一份重复卡带 active 时高亮不能丢", content.artists[0].isActive)
    }

    @Test
    fun `active card followed by inactive duplicate stays active`() {
        val html = card("Author A", active = true) + card("Author A")
        val content = parser.parse(html, baseUrl)

        assertEquals(1, content.artists.size)
        assertTrue(content.artists[0].isActive)
    }

    @Test
    fun `distinct authors keep order and flags`() {
        val html = card("A") + card("B", active = true) + card("C")
        val content = parser.parse(html, baseUrl)

        assertEquals(listOf("A", "B", "C"), content.artists.map { it.name })
        assertEquals(listOf(false, true, false), content.artists.map { it.isActive })
        assertFalse(content.artists[0].isActive)
    }
}
