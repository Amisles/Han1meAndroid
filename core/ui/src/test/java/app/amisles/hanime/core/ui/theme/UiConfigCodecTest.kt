package app.amisles.hanime.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面配置的字符串编解码测试。
 *
 * 这两组函数负责把「分区布局 / 分区顺序」落到 SharedPreferences 并读回，
 * 属于纯字符串处理，与 JSON 导入导出（依赖 org.json，仅在设备上可用）分离。
 * 损坏或半截的数据必须被安全丢弃，而不是抛异常导致设置页崩溃。
 */
class UiConfigCodecTest {

    @Test
    fun `layouts round trip`() {
        val layouts = mapOf("最新上市" to HomeSectionLayout.GRID, "本日排行" to HomeSectionLayout.CAROUSEL)
        val decoded = UiConfigCodec.decodeLayouts(UiConfigCodec.encodeLayouts(layouts))
        assertEquals(layouts, decoded)
    }

    @Test
    fun `empty layouts encode to empty string and decode back to empty map`() {
        assertEquals("", UiConfigCodec.encodeLayouts(emptyMap()))
        assertTrue(UiConfigCodec.decodeLayouts("").isEmpty())
    }

    @Test
    fun `decodeLayouts skips malformed entries instead of throwing`() {
        // 缺 '='、未知枚举值、空标题 都必须被跳过
        val raw = listOf("正常=GRID", "坏条目", "未知=Xyz", "=LIST").joinToString("\u0001")
        val decoded = UiConfigCodec.decodeLayouts(raw)
        assertEquals(mapOf("正常" to HomeSectionLayout.GRID), decoded)
    }

    @Test
    fun `decodeLayouts keeps title containing equals sign`() {
        // 标题里可能出现 '='，解析必须按最后一个 '=' 切分
        val raw = "a=b=GRID"
        assertEquals(mapOf("a=b" to HomeSectionLayout.GRID), UiConfigCodec.decodeLayouts(raw))
    }

    @Test
    fun `order round trip preserves sequence`() {
        val order = listOf("最新上市", "本日排行", "本月排行")
        val decoded = UiConfigCodec.decodeOrder(UiConfigCodec.encodeOrder(order))
        assertEquals(order, decoded)
    }

    @Test
    fun `empty order encodes to empty string and decodes back to empty list`() {
        assertEquals("", UiConfigCodec.encodeOrder(emptyList()))
        assertTrue(UiConfigCodec.decodeOrder("").isEmpty())
    }

    @Test
    fun `decodeOrder drops empty segments`() {
        val raw = listOf("A", "", "B", "").joinToString("\u0001")
        assertEquals(listOf("A", "B"), UiConfigCodec.decodeOrder(raw))
    }
}
