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

    private val math = PlayerGestureMath(slopPx = 8f)

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

    // ── 跨手势残留 ──────────────────────────────────────────────────────────

    @Test
    fun `stale horizontal movement does not force next vertical drag into seek`() {
        assertEquals(
            DragMode.SEEK,
            math.onMove(x = 300f, y = 0f, lastX = 0f, lastY = 0f, downX = 100f, playerWidth = 1000f)
        )

        math.reset()

        assertEquals(
            DragMode.BRIGHTNESS,
            math.onMove(x = 100f, y = 60f, lastX = 100f, lastY = 0f, downX = 400f, playerWidth = 1000f)
        )
    }

    @Test
    fun `stale vertical movement does not force next horizontal drag into volume`() {
        assertEquals(
            DragMode.VOLUME,
            math.onMove(x = 800f, y = 400f, lastX = 800f, lastY = 0f, downX = 800f, playerWidth = 1000f)
        )

        math.reset()

        assertEquals(
            DragMode.SEEK,
            math.onMove(x = 200f, y = 100f, lastX = 0f, lastY = 100f, downX = 100f, playerWidth = 1000f)
        )
    }

    // ── 垂直方向抖动 ────────────────────────────────────────────────────────

    @Test
    fun `perpendicular jitter does not postpone drag start`() {
        // 竖向位移 5px 已越过 8px 阈值的一半但未过阈值，横向抖动 6px；
        // 两个方向都没到阈值时不应开始拖动，无论哪种比较方式
        assertNull(math.onMove(x = 806f, y = 5f, lastX = 800f, lastY = 0f, downX = 800f, playerWidth = 1000f))

        // 再竖向移动 4px：竖向累计 9px 已过阈值，横向仍为 6px，必须判定为纵向
        assertEquals(
            DragMode.VOLUME,
            math.onMove(x = 806f, y = 9f, lastX = 806f, lastY = 5f, downX = 800f, playerWidth = 1000f)
        )
    }

    @Test
    fun `horizontal drag is not delayed by vertical jitter`() {
        assertNull(math.onMove(x = 6f, y = 5f, lastX = 0f, lastY = 0f, downX = 100f, playerWidth = 1000f))

        assertEquals(
            DragMode.SEEK,
            math.onMove(x = 12f, y = 5f, lastX = 6f, lastY = 5f, downX = 100f, playerWidth = 1000f)
        )
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

    // ── 长按加速调速 ────────────────────────────────────────────────────────

    @Test
    fun `dragging up raises boost speed`() {
        // 高度 1000px、起手 2x，上滑 200px（= 整屏 20%）→ 2 + 0.2 * (3 - 1) = 2.4
        assertEquals(
            2.4f,
            math.boostSpeed(baseSpeed = 2f, downY = 500f, currentY = 300f, playerHeight = 1000f),
            0.0001f
        )
    }

    @Test
    fun `dragging down lowers boost speed`() {
        assertEquals(
            1.6f,
            math.boostSpeed(baseSpeed = 2f, downY = 300f, currentY = 500f, playerHeight = 1000f),
            0.0001f
        )
    }

    @Test
    fun `boost speed clamps at the configured bounds`() {
        // 上滑整屏远超上限，钳到 3x；下滑整屏低于下限，钳到 1x
        assertEquals(
            PlayerGestureMath.MAX_BOOST_SPEED,
            math.boostSpeed(baseSpeed = 2f, downY = 1000f, currentY = 0f, playerHeight = 1000f),
            0.0001f
        )
        assertEquals(
            PlayerGestureMath.MIN_BOOST_SPEED,
            math.boostSpeed(baseSpeed = 2f, downY = 0f, currentY = 1000f, playerHeight = 1000f),
            0.0001f
        )
    }

    @Test
    fun `boost speed stays inside bounds for out-of-range preference`() {
        // 偏好被写坏（0.5x / 5x）且高度未知时也不能把倍率放到区间外
        assertEquals(
            PlayerGestureMath.MIN_BOOST_SPEED,
            math.boostSpeed(baseSpeed = 0.5f, downY = 500f, currentY = 500f, playerHeight = 0f),
            0.0001f
        )
        assertEquals(
            PlayerGestureMath.MAX_BOOST_SPEED,
            math.boostSpeed(baseSpeed = 5f, downY = 500f, currentY = 500f, playerHeight = 0f),
            0.0001f
        )
    }

    @Test
    fun `boost speed keeps base speed when height unknown`() {
        assertEquals(
            2f,
            math.boostSpeed(baseSpeed = 2f, downY = 100f, currentY = 400f, playerHeight = 0f),
            0.0001f
        )
    }

    @Test
    fun `boost speed is quantized to one decimal`() {
        // 上滑 30px = 2.06x，量化后应是 2.1x，而不是 2.06 这种读数
        assertEquals(
            2.1f,
            math.boostSpeed(baseSpeed = 2f, downY = 500f, currentY = 470f, playerHeight = 1000f),
            0.0001f
        )
        // 上滑 3px = 2.006x，量化回 2x：微小抖动不会在提示条上来回跳
        assertEquals(
            2f,
            math.boostSpeed(baseSpeed = 2f, downY = 500f, currentY = 497f, playerHeight = 1000f),
            0.0001f
        )
    }

    @Test
    fun `boost speed returns to base when finger moves back`() {
        // 以按下点为基准做绝对换算：滑上去再滑回原处必须精确回到起手倍率，不累积漂移
        val up = math.boostSpeed(baseSpeed = 2f, downY = 500f, currentY = 350f, playerHeight = 1000f)
        assertEquals(2.3f, up, 0.0001f)
        assertEquals(
            2f,
            math.boostSpeed(baseSpeed = 2f, downY = 500f, currentY = 500f, playerHeight = 1000f),
            0.0001f
        )
    }
}
