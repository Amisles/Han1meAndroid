package app.amisles.hanime.feature.home

import app.amisles.hanime.core.ui.theme.UiConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 骨架屏结构计算的回归测试。
 *
 * 旧骨架屏固定 3 个分区 × 5 行紧凑列表，不随「网格 / 横滑」布局与「每区条数」变化，
 * 加载结束内容结构突变（H-08）。这里锁住行数换算与缩略图宽度与真实列表一致。
 */
class HomeSkeletonLayoutTest {

    @Test
    fun `row count rounds up to cover every item`() {
        assertEquals(1, skeletonRowCount(2, 2))
        assertEquals(2, skeletonRowCount(3, 2))
        assertEquals(2, skeletonRowCount(6, 3))
        assertEquals(3, skeletonRowCount(7, 3))
    }

    @Test
    fun `row count follows visible count instead of a fixed five`() {
        assertEquals(1, skeletonRowCount(6, 6))
        assertEquals(6, skeletonRowCount(12, 2))
    }

    @Test
    fun `row count never divides by zero`() {
        assertEquals(5, skeletonRowCount(5, 0))
    }

    @Test
    fun `compact width keeps configured thumbnail width`() {
        val cfg = UiConfig.Default

        assertEquals(
            cfg.listThumbnailWidth,
            rowThumbnailWidth(cfg, widthDp = 400, contentMaxWidth = androidx.compose.ui.unit.Dp.Unspecified, columns = 2, isCompactWidth = true)
        )
    }

    @Test
    fun `wide layout shrinks thumbnail below configured width`() {
        val cfg = UiConfig.Default

        val width = rowThumbnailWidth(
            cfg = cfg,
            widthDp = 840,
            contentMaxWidth = androidx.compose.ui.unit.Dp(840f),
            columns = 3,
            isCompactWidth = false
        )

        assertEquals(true, width.value < cfg.listThumbnailWidth.value)
        assertEquals(true, width.value >= 56f)
    }
}
