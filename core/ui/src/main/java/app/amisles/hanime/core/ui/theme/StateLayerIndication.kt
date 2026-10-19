package app.amisles.hanime.core.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * 点击反馈的状态层：按压 / 悬停 / 聚焦时在内容之上叠一层半透明色。
 *
 * 取代全局禁用的涟漪。用 [IndicationNodeFactory] 而非已标记为 ERROR 级废弃的
 * `Indication.rememberUpdatedInstance`；[color] 由主题在组合期注入，故深浅色各自取到正确的
 * `onSurface`，且换主题时 equals 变化会重建节点。
 */
class StateLayerIndication(
    private val color: Color,
    private val pressedAlpha: Float = 0.10f,
    private val hoveredAlpha: Float = 0.08f
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        StateLayerNode(interactionSource, color, pressedAlpha, hoveredAlpha)

    override fun hashCode(): Int =
        (color.hashCode() * 31 + pressedAlpha.hashCode()) * 31 + hoveredAlpha.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StateLayerIndication) return false
        return color == other.color &&
            pressedAlpha == other.pressedAlpha &&
            hoveredAlpha == other.hoveredAlpha
    }

    private class StateLayerNode(
        private val interactionSource: InteractionSource,
        private val color: Color,
        private val pressedAlpha: Float,
        private val hoveredAlpha: Float
    ) : Modifier.Node(), DrawModifierNode {

        private var pressed = false
        private var hovered = false
        private var focused = false

        override fun onAttach() {
            coroutineScope.launch {
                var pressCount = 0
                var hoverCount = 0
                var focusCount = 0
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressCount++
                        is PressInteraction.Release -> pressCount--
                        is PressInteraction.Cancel -> pressCount--
                        is HoverInteraction.Enter -> hoverCount++
                        is HoverInteraction.Exit -> hoverCount--
                        is FocusInteraction.Focus -> focusCount++
                        is FocusInteraction.Unfocus -> focusCount--
                    }
                    val nextPressed = pressCount > 0
                    val nextHovered = hoverCount > 0
                    val nextFocused = focusCount > 0
                    val changed = pressed != nextPressed ||
                        hovered != nextHovered ||
                        focused != nextFocused
                    pressed = nextPressed
                    hovered = nextHovered
                    focused = nextFocused
                    if (changed) invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val alpha = when {
                pressed -> pressedAlpha
                hovered || focused -> hoveredAlpha
                else -> 0f
            }
            if (alpha > 0f) drawRect(color = color.copy(alpha = alpha), size = size)
        }
    }
}
