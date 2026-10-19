package app.amisles.hanime.feature.detail.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放器单指手势的判定与换算测试。
 *
 * 这些边界原本内联在 pointerInput 的隐式状态里，只能靠手动点击验证；抽成纯类后
 * 用 JUnit 固定下来，避免后续调整阈值或方向优先级时引入无声回归。
 */
class PlayerGestureMathTest {

    private val math = PlayerGestureMath()

    // ── 阈值 ────────────────────────────────────────────────────────────────

    @Test
    fun `movement below slop does not start drag`() {
        // 单次 4px、累计仍不足 8px
        assertNull(math.onMove(x = 4f, y = 0f, lastX = 0f, lastY = 0f, downX = 100f, playerWidth = 1000f))
    }

    @Test
    fun `horizontal movement past slop starts seek`() {
        assertEquals(
            DragMode.SEEK,
            math.onMove(x = 10f, y = 1f, lastX = 0f, lastY = 0f, downX = 100f, playerWidth = 1000f)
        )
    }

    // ── 方向优先级 ──────────────────────────────────────────────────────────

    @Test
    fun `horizontal dominates when dx larger than dy`() {
        assertEquals(
            DragMode.SEEK,
            math.onMove(x = 30f, y = 20f, lastX = 0f, lastY = 0f, downX = 100f, playerWidth = 1000f)
        )
    }

    @Test
    fun `vertical dominates when dy larger than dx`() {
        assertEquals(
            DragMode.BRIGHTNESS,
            math.onMove(x = 10f, y = 40f, lastX = 0f, lastY = 0f, downX = 100f, playerWidth = 1000f)
        )
    }

    // ── 左右半屏 ────────────────────────────────────────────────────────────

    @Test
    fun `vertical drag on left half adjusts brightness`() {
        assertEquals(
            DragMode.BRIGHTNESS,
            math.onMove(x = 100f, y = 50f, lastX = 100f, lastY = 0f, downX = 400f, playerWidth = 1000f)
        )
    }

    @Test
    fun `vertical drag on right half adjusts volume`() {
        assertEquals(
            DragMode.VOLUME,
            math.onMove(x = 700f, y = 50f, lastX = 700f, lastY = 0f, downX = 800f, playerWidth = 1000f)
        )
    }

    @Test
    fun `invalid width falls back to volume`() {
        assertEquals(
            DragMode.VOLUME,
            math.onMove(x = 10f, y = 50f, lastX = 10f, lastY = 0f, downX = 10f, playerWidth = 0f)
        )
    }

    @Test
    fun `reset clears accumulated movement`() {
        // 先累计一次接近阈值的位移
        assertNull(math.onMove(x = 7f, y = 0f, lastX = 0f, lastY = 0f, downX = 0f, playerWidth = 1000f))
        math.reset()
        // 再移动 7px：若未清零则会累积到 14px 并判定为拖动
        assertNull(math.onMove(x = 7f, y = 0f, lastX = 7f, lastY = 0f, downX = 0f, playerWidth = 1000f))
    }

    // ── 进度换算 ────────────────────────────────────────────────────────────

    @Test
    fun `seek maps half width to half duration`() {
        // 宽 1000px、时长 100s，向右拖 500px 应前进 50s
        assertEquals(
            60_000L,
            math.seekTarget(startPosition = 10_000L, startX = 0f, currentX = 500f, playerWidth = 1000f, duration = 100_000L)
        )
    }

    @Test
    fun `seek clamps to zero when dragging left past start`() {
        assertEquals(
            0L,
            math.seekTarget(startPosition = 1_000L, startX = 500f, currentX = 0f, playerWidth = 1000f, duration = 100_000L)
        )
    }

    @Test
    fun `seek clamps to duration when dragging right past end`() {
        assertEquals(
            100_000L,
            math.seekTarget(startPosition = 99_000L, startX = 0f, currentX = 1000f, playerWidth = 1000f, duration = 100_000L)
        )
    }

    @Test
    fun `seek keeps position when duration unknown`() {
        assertEquals(
            5_000L,
            math.seekTarget(startPosition = 5_000L, startX = 0f, currentX = 500f, playerWidth = 1000f, duration = 0L)
        )
    }

    @Test
    fun `seek keeps position when width invalid`() {
        assertEquals(
            5_000L,
            math.seekTarget(startPosition = 5_000L, startX = 0f, currentX = 500f, playerWidth = 0f, duration = 100_000L)
        )
    }

    // ── 纵向增量 ────────────────────────────────────────────────────────────

    @Test
    fun `upward drag yields positive delta`() {
        // 手指上移 100px，高度 1000px → +0.1
        assertEquals(0.1f, math.verticalDelta(lastY = 200f, y = 100f, playerHeight = 1000f), 0.0001f)
    }

    @Test
    fun `downward drag yields negative delta`() {
        assertEquals(-0.1f, math.verticalDelta(lastY = 100f, y = 200f, playerHeight = 1000f), 0.0001f)
    }

    @Test
    fun `invalid height yields zero delta`() {
        assertEquals(0f, math.verticalDelta(lastY = 100f, y = 200f, playerHeight = 0f), 0.0001f)
    }
}
