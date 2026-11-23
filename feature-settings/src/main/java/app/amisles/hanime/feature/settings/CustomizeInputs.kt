package app.amisles.hanime.feature.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.UiConfig
import java.util.Locale

/** 与 UiConfig.sanitized() 中 fontScale 的夹取区间保持一致。 */
internal val FONT_SCALE_RANGE = 0.85f..1.4f

internal val SLIDER_BAR_TOP = 12.dp

internal val INPUT_BOX_WIDTH = 80.dp

internal val INPUT_BOX_HEIGHT = 34.dp

internal const val INPUT_MAX_INT_DIGITS = 1

internal const val INPUT_MAX_DECIMALS = 2

/**
 * 自绘滑杆。
 *
 * 不使用 Material3 Slider 的原因：
 *   1. 需要在轨道上叠加步进刻度与默认值基准，Slider 不提供该 slot；
 *   2. 轨道与滑块需按设计精确控制（4dp 轨道 / 16dp 方形滑块），默认样式无法对齐；
 *   3. 与项目既有"自绘控件以规避实验性 API"的取向一致。
 *
 * 手感相关的三条约束（每条都对应一次真实缺陷，改动前请先读懂）：
 *   1. **拖拽位置必须在控件内部连续累积**。外部 [value] 是量化后的值（字号 0.01、条数 1 档），
 *      若每次都拿 delta 从量化值重新推算，不足一档的位移会被反复丢弃：表现为拖动发涩，
 *      且档位越粗越严重 —— 条数档为 3–12（单档约 30dp），逐帧位移远小于一档，几乎拖不动。
 *   2. **拖动期间滑块不得走动画**。spring 过渡会让滑块始终落后手指，产生黏滞感；
 *      仅在拖动结束、应用预设等由外部驱动的位置变化时才用动画。
 *   3. **触摸区不等同于滑块**。滑块视觉高 26dp 太窄，故 drag 挂在整条 40dp 高的容器上。
 */
@Composable
internal fun CustomSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    ticks: Int = 0
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val valueFraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val defaultFraction = ((defaultValue - range.start) / span).coerceIn(0f, 1f)

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thumbWidth = 16.dp

    // 非 null 表示正在拖动，保存未量化的连续位置，用于保留不足一档的位移残量
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownFraction = dragFraction ?: valueFraction

    val targetOffset = with(density) { (shownFraction * trackWidthPx).toDp() }
    val thumbSpec: AnimationSpec<Dp> =
        if (dragFraction != null) snap() else spring(visibilityThreshold = 0.01.dp)
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = thumbSpec,
        label = "thumbOffset"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val latestValue = remember { mutableFloatStateOf(value) }
    val latestPreview = rememberUpdatedState(onPreview)
    val latestCommit = rememberUpdatedState(onCommit)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                state = rememberDraggableState { delta ->
                    if (trackWidthPx <= 0f) return@rememberDraggableState
                    val base = dragFraction ?: valueFraction
                    val next = (base + delta / trackWidthPx).coerceIn(0f, 1f)
                    dragFraction = next
                    val raw = range.start + next * span
                    latestValue.floatValue = raw
                    latestPreview.value(raw)
                },
                onDragStarted = { dragFraction = valueFraction },
                onDragStopped = {
                    dragFraction = null
                    latestCommit.value(latestValue.floatValue)
                }
            )
    ) {
        // 轨道已内缩半个滑块，滑块中心走完整个轨道跨度；此内缩同时是下方各叠加层的坐标基准，
        // 故填充与刻线都放在这一层内，与滑块共用同一映射。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = thumbWidth / 2),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    .onSizeChanged { trackWidthPx = it.width.toFloat() }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(shownFraction)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (ticks > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(ticks) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            if (defaultFraction > 0.001f && defaultFraction < 0.999f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(defaultFraction)
                        .height(40.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = 2.dp, height = 12.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(width = thumbWidth, height = 26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onBackground)
        )
    }
}

/* ───────────────────────── 档位选择行 ───────────────────────── */

/**
 * 数值手动输入行，[unit] 作为只读后缀显示在输入框内。
 *
 * 输入过程中会经过"尚不构成合法值"的中间态（空串、"0."）。**此时不提交**：
 * 若对每次按键都做区间夹取再回写，用户敲 "0." 会被立刻改写成 "0.85"，导致永远输入不了 0.9 这类值。
 * 因此仅在解析结果落入 [range] 时实时提交以驱动预览，越界时以警示色提示，失焦时再归一到实际生效值，
 * 保证输入框显示与已存储的值不会长期不一致。
 */
@Composable
internal fun DecimalInputRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    hint: String,
    isDefault: Boolean,
    onReset: () -> Unit,
    onCommit: (Float) -> Unit
) {
    var text by remember { mutableStateOf(formatDecimal(value)) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 仅在外部驱动（应用预设 / 恢复默认）导致数值变化时同步；数值含义一致则不覆盖，避免打断正在输入的文本
    LaunchedEffect(value) {
        if (text.toFloatOrNull() != value) text = formatDecimal(value)
    }

    val parsed = text.toFloatOrNull()
    val outOfRange = parsed != null && parsed !in range

    Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .width(INPUT_BOX_WIDTH)
                    .height(INPUT_BOX_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        val filtered = filterDecimalInput(raw)
                        text = filtered
                        filtered.toFloatOrNull()?.takeIf { it in range }?.let(onCommit)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (focused && !state.isFocused) text = formatDecimal(value)
                            focused = state.isFocused
                        }
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isDefault) {
                Spacer(modifier = Modifier.width(2.dp))
                IconAction(
                    icon = Icons.Default.RestartAlt,
                    enabled = true,
                    labelRes = R.string.customize_reset_item,
                    onClick = onReset,
                    boxSize = 30.dp,
                    iconSize = 17.dp
                )
            }
        }
        Text(
            text = hint,
            fontSize = 10.sp,
            color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

internal fun formatDecimal(value: Float): String = String.format(Locale.US, "%.2f", value)

/**
 * 过滤为「最多 [INPUT_MAX_INT_DIGITS] 位整数 + 最多 [INPUT_MAX_DECIMALS] 位小数」的字符串。
 * 逐字符重建而非正则替换：需要同时约束整数位、小数位与小数点个数，并自然丢弃粘贴进来的其他字符。
 */
internal fun filterDecimalInput(raw: String): String {
    val sb = StringBuilder()
    var dotSeen = false
    var intDigits = 0
    var decimals = 0
    for (ch in raw) {
        when {
            ch == '.' && !dotSeen -> {
                sb.append(ch); dotSeen = true
            }

            ch.isDigit() && !dotSeen -> if (intDigits < INPUT_MAX_INT_DIGITS) {
                sb.append(ch); intDigits++
            }

            ch.isDigit() -> if (decimals < INPUT_MAX_DECIMALS) {
                sb.append(ch); decimals++
            }
        }
    }
    return sb.toString()
}
