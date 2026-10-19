package app.amisles.hanime.feature.detail.components

/**
 * 单指手势的拖动模式。
 *
 * 三者在同一套单指滑动里互斥：先按首个位移的方向与落点判定模式，之后整段手势沿该模式处理，
 * 中途不再切换（否则斜向滑动会在进度与音量之间反复横跳）。
 */
internal enum class DragMode {
    /** 横向滑动：相对起点位移换算为播放位置。 */
    SEEK,

    /** 纵向滑动且落点在左半屏：调节亮度。 */
    BRIGHTNESS,

    /** 纵向滑动且落点在右半屏：调节音量。 */
    VOLUME,
}

/**
 * 单指手势拖动模式的判定与位移换算。
 *
 * 抽成纯类是为了可测：原实现这些判断内联在 `pointerInput` 的隐式状态里，
 * 「8px 触摸阈值」「横竖谁优先」「左右半屏分界」这些边界只能靠手动点击验证。
 *
 * 本类不持有任何 Compose 状态，也不触碰播放器，因此可直接用 JUnit 覆盖。
 *
 * @param slopPx 触摸抖动阈值：累计位移超过它才认为用户开始拖动。
 */
internal class PlayerGestureMath(private val slopPx: Float = DEFAULT_SLOP_PX) {

    private var accDx = 0f
    private var accDy = 0f

    /** 手势刚开始：累计位移清零。 */
    fun reset() {
        accDx = 0f
        accDy = 0f
    }

    /**
     * 累计一次移动并判断是否已越过阈值。未越过时返回 null，越过时返回本段手势的拖动模式。
     *
     * 方向优先：|dx| > |dy| 一律视为横向（进度），否则按落点 x 与 [playerWidth] 的中线决定亮度/音量。
     * 这与原实现一致：斜向滑动以分量更大的一侧为准。
     *
     * @param x 当前触点 x（播放器局部坐标）
     * @param downX 手势起点的 x，用于判定左右半屏
     * @param playerWidth 播放器宽度；<= 0 时无法判定左右，按音量处理（与原行为一致）
     */
    fun onMove(x: Float, y: Float, lastX: Float, lastY: Float, downX: Float, playerWidth: Float): DragMode? {
        accDx += x - lastX
        accDy += y - lastY
        if (absF(accDx) <= slopPx && absF(accDy) <= slopPx) return null
        return if (absF(accDx) > absF(accDy)) {
            DragMode.SEEK
        } else if (playerWidth > 0f && downX < playerWidth / 2f) {
            DragMode.BRIGHTNESS
        } else {
            DragMode.VOLUME
        }
    }

    /**
     * 横向拖动换算的目标播放位置（毫秒）。
     *
     * 拖动距离按「播放器宽度 = 完整时长」线性换算，并钳在 [0, duration] 内。
     * duration <= 0 或 playerWidth <= 0 时无法换算，原样返回 [startPosition]（不动）。
     */
    fun seekTarget(startPosition: Long, startX: Float, currentX: Float, playerWidth: Float, duration: Long): Long {
        if (duration <= 0L || playerWidth <= 0f) return startPosition
        val timeDelta = ((currentX - startX) / playerWidth * duration.toFloat()).toLong()
        return (startPosition + timeDelta).coerceIn(0L, duration)
    }

    /**
     * 纵向拖动换算的增量（-1..1 的区间比例）。
     *
     * 向上为增（dy 为负 → 结果为正），除以播放器高度做归一化；高度无效时返回 0。
     */
    fun verticalDelta(lastY: Float, y: Float, playerHeight: Float): Float {
        if (playerHeight <= 0f) return 0f
        return -(y - lastY) / playerHeight
    }

    companion object {
        /** 触摸抖动阈值：8px 与原实现保持一致。 */
        const val DEFAULT_SLOP_PX = 8f
    }
}

private fun absF(v: Float): Float = if (v < 0f) -v else v
