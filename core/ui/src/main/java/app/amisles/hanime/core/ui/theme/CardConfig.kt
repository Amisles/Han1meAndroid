package app.amisles.hanime.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 卡片容器外观。AUTO 表示沿用各卡型的原有默认（列表有容器、网格与横滑无容器）。 */
enum class CardStyle { AUTO, FILLED, OUTLINED, ELEVATED, PLAIN }

/** 缩略图圆角模式：四角全圆，或只圆上两角（海报贴底）。 */
enum class CornerMode { ALL, TOP }

@Immutable
data class CardConfig(
    val style: CardStyle = CardStyle.AUTO,
    val titleLines: Int = 2,
    val showAuthor: Boolean = true,
    val showPublishTime: Boolean = true,
    val showBadges: Boolean = true,
    val cornerMode: CornerMode = CornerMode.ALL,
) {
    fun sanitized(): CardConfig =
        copy(titleLines = titleLines.coerceIn(MIN_TITLE_LINES, MAX_TITLE_LINES))

    companion object {
        const val MIN_TITLE_LINES = 1
        const val MAX_TITLE_LINES = 3
        val Default = CardConfig()
    }
}

@Immutable
data class CardGeometry(
    val style: CardStyle,
    val shape: RoundedCornerShape,
    val inset: Dp,
    val thumbShape: RoundedCornerShape
)

fun cardGeometry(cfg: UiConfig): CardGeometry {
    val style = if (cfg.card.style == CardStyle.AUTO) CardStyle.PLAIN else cfg.card.style
    val inset = if (style == CardStyle.PLAIN) 0.dp else 6.dp
    return CardGeometry(
        style = style,
        shape = RoundedCornerShape(cfg.cardCornerRadius),
        inset = inset,
        thumbShape = thumbnailCornerShape(
            (cfg.cardCornerRadius.value - inset.value).coerceAtLeast(0f).dp,
            cfg.card.cornerMode
        )
    )
}

/** 卡片容器修饰符；须置于 clickable 之前、内容 padding 之前。 */
@Composable
fun Modifier.cardSurface(style: CardStyle, shape: RoundedCornerShape): Modifier = when (style) {
    CardStyle.FILLED, CardStyle.AUTO -> background(MaterialTheme.colorScheme.surface, shape)
    CardStyle.OUTLINED -> background(MaterialTheme.colorScheme.surface, shape)
        .border(1.dp, MaterialTheme.colorScheme.outline, shape)

    CardStyle.ELEVATED -> shadow(2.dp, shape).background(MaterialTheme.colorScheme.surface, shape)
    CardStyle.PLAIN -> this
}

fun thumbnailCornerShape(radius: Dp, mode: CornerMode): RoundedCornerShape = when (mode) {
    CornerMode.ALL -> RoundedCornerShape(radius)
    CornerMode.TOP -> RoundedCornerShape(topStart = radius, topEnd = radius)
}
