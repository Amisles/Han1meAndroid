package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import app.amisles.hanime.core.ui.R
import androidx.compose.ui.input.pointer.pointerInput
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerTapAction

internal fun Modifier.playerEditDashedBorder(
    color: Color = Color.White.copy(alpha = 0.55f),
    cornerRadius: Dp = 5.dp,
    strokeWidth: Dp = 1.dp
): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(4.dp.toPx(), 3.dp.toPx())
        )
    )
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}

@Composable
internal fun PlayerEditBadge(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(if (checked) HanimePrimary else Color.Black.copy(alpha = 0.55f))
            .then(
                Modifier.drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = if (checked) 0.9f else 0.55f),
                        radius = size.minDimension / 2f - 0.5.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(9.dp)
            )
        }
    }
}

/**
 * 编辑态控件外壳：虚线选框 + 右上角勾选角标，并盖一层透明点击层。
 *
 * 点击层必须压在控件上方：控件自身的 onClick 会先于外层拿到事件，不挡住就会出现
 * 「点一下既执行了播放/暂停，又切换了显隐」。
 */
@Composable
internal fun PlayerEditFrame(
    editing: Boolean,
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!editing) {
        // modifier 里带着调用方的对齐 / 权重，非编辑态也必须套上，否则控件会掉到父容器的左上角
        Box(modifier = modifier) { content() }
        return
    }
    Box(
        modifier = modifier
            .padding(1.dp)
            .playerEditDashedBorder()
            .padding(2.dp)
    ) {
        content()
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = label
                ) { onToggle() }
        )
        PlayerEditBadge(
            checked = checked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp)
        )
    }
}

@Composable
internal fun PlayerLayoutEditTitle(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(R.string.player_layout_title),
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun PlayerLayoutEditPanel(
    layout: PlayerLayout,
    onToggle: (PlayerControlId) -> Unit,
    onCycleTapAction: (PlayerTapZoneSlot) -> Unit,
    onLeftZonePercentChange: (Int) -> Unit,
    onRightZonePercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 660.dp)
            .heightIn(max = 210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FEATURE_TILES.chunked(FEATURE_TILE_COLUMNS).forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 0.dp else 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { tile ->
                    PlayerEditTile(
                        icon = tile.icon,
                        label = stringResource(tile.labelRes),
                        checked = layout.isShown(tile.id)
                    ) { onToggle(tile.id) }
                }
            }
        }

        TapZoneBar(
            layout = layout,
            onCycle = onCycleTapAction,
            onLeftPercentChange = onLeftZonePercentChange,
            onRightPercentChange = onRightZonePercentChange,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = stringResource(R.string.player_layout_zone_hint),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp)
        )

        Text(
            text = stringResource(R.string.player_layout_edit_hint),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
internal fun PlayerLayoutEditBar(
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerEditAction(
            text = stringResource(R.string.player_layout_reset),
            primary = false,
            onClick = onReset
        )
        PlayerEditAction(
            text = stringResource(R.string.player_layout_cancel),
            primary = false,
            onClick = onCancel
        )
        PlayerEditAction(
            text = stringResource(R.string.player_layout_done),
            primary = true,
            onClick = onFinish
        )
    }
}

@Composable
private fun PlayerEditAction(text: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (primary) Color.White else Color.White.copy(alpha = 0.85f),
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) HanimePrimary else Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

@Composable
private fun PlayerEditTile(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .width(96.dp)
                .height(44.dp)
                .playerEditDashedBorder()
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        PlayerEditBadge(
            checked = checked,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
        )
    }
}

/**
 * 点按区域的可视化编辑条：三段宽度就是播放器上真实的点击范围，两条虚线分界可直接拖动。
 *
 * 用示意图代替「左右区域宽度 33%」这类数字：百分比本身不解释自己在播放器上对应哪一段，
 * 拖一次分界线就知道边界落在画面哪里。
 */
@Composable
private fun TapZoneBar(
    layout: PlayerLayout,
    onCycle: (PlayerTapZoneSlot) -> Unit,
    onLeftPercentChange: (Int) -> Unit,
    onRightPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var barWidth by remember { mutableStateOf(0f) }
    val centerPercent = (100 - layout.leftZonePercent - layout.rightZonePercent).coerceAtLeast(1)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .onSizeChanged { barWidth = it.width.toFloat() }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            TapZoneSegment(
                title = stringResource(R.string.player_layout_tap_left),
                action = layout.tapLeft,
                weight = layout.leftZonePercent.toFloat(),
                modifier = Modifier.fillMaxHeight()
            ) { onCycle(PlayerTapZoneSlot.LEFT) }
            TapZoneSegment(
                title = stringResource(R.string.player_layout_tap_center),
                action = layout.tapCenter,
                weight = centerPercent.toFloat(),
                modifier = Modifier.fillMaxHeight()
            ) { onCycle(PlayerTapZoneSlot.CENTER) }
            TapZoneSegment(
                title = stringResource(R.string.player_layout_tap_right),
                action = layout.tapRight,
                weight = layout.rightZonePercent.toFloat(),
                modifier = Modifier.fillMaxHeight()
            ) { onCycle(PlayerTapZoneSlot.RIGHT) }
        }
        if (barWidth > 0f) {
            val minSide = PlayerLayout.MIN_SIDE_ZONE_PERCENT.toFloat()
            val maxSides = 100f - PlayerLayout.MIN_CENTER_ZONE_PERCENT
            ZoneHandle(
                barWidth = barWidth,
                boundaryPercent = layout.leftZonePercent.toFloat(),
                minPercent = minSide,
                maxPercent = (maxSides - layout.rightZonePercent.toFloat()).coerceAtLeast(minSide),
                onBoundaryChange = { onLeftPercentChange(it.roundToInt()) }
            )
            ZoneHandle(
                barWidth = barWidth,
                boundaryPercent = (100 - layout.rightZonePercent).toFloat(),
                minPercent = (layout.leftZonePercent + PlayerLayout.MIN_CENTER_ZONE_PERCENT)
                    .toFloat()
                    .coerceAtMost(100f - minSide),
                maxPercent = 100f - minSide,
                onBoundaryChange = { onRightPercentChange((100f - it).roundToInt()) }
            )
        }
    }
}

@Composable
private fun RowScope.TapZoneSegment(
    title: String,
    action: PlayerTapAction,
    weight: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .weight(weight.coerceAtLeast(1f))
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (action == PlayerTapAction.NONE) {
                    Color.White.copy(alpha = 0.06f)
                } else {
                    HanimePrimary.copy(alpha = 0.16f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(tapActionLabelRes(action)),
            color = if (action == PlayerTapAction.NONE) {
                Color.White.copy(alpha = 0.5f)
            } else {
                Color.White
            },
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun BoxScope.ZoneHandle(
    barWidth: Float,
    boundaryPercent: Float,
    minPercent: Float,
    maxPercent: Float,
    onBoundaryChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    val handleWidthPx = with(density) { HANDLE_WIDTH.toPx() }
    var dragBase by remember { mutableStateOf<Float?>(null) }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (barWidth * boundaryPercent / 100f - handleWidthPx / 2f).roundToInt(),
                    0
                )
            }
            .width(HANDLE_WIDTH)
            .fillMaxHeight()
            .pointerInput(barWidth, minPercent, maxPercent) {
                detectHorizontalDragGestures(
                    onDragStart = { dragBase = boundaryPercent },
                    onDragEnd = { dragBase = null },
                    onDragCancel = { dragBase = null },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (barWidth > 0f) {
                            val base = dragBase ?: boundaryPercent
                            val next = (base + dragAmount / barWidth * 100f)
                                .coerceIn(minPercent, maxPercent)
                            dragBase = next
                            onBoundaryChange(next)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(HanimePrimary)
        )
    }
}

private val HANDLE_WIDTH = 26.dp

private const val FEATURE_TILE_COLUMNS = 4

private class FeatureTile(
    val id: PlayerControlId,
    val icon: ImageVector,
    val labelRes: Int
)

private val FEATURE_TILES = listOf(
    FeatureTile(
        PlayerControlId.CENTER_PLAY,
        Icons.Outlined.PlayCircleOutline,
        R.string.player_layout_show_center_play
    ),
    FeatureTile(
        PlayerControlId.RESET_ZOOM,
        Icons.Outlined.ZoomOutMap,
        R.string.player_layout_show_reset_zoom
    ),
    FeatureTile(PlayerControlId.TIME_LABELS, Icons.Outlined.Schedule, R.string.player_layout_show_time),
    FeatureTile(PlayerControlId.SEEK_PREVIEW, Icons.Outlined.Image, R.string.player_layout_show_seek_preview),
    FeatureTile(PlayerControlId.MINI_PROGRESS, Icons.Outlined.LinearScale, R.string.player_layout_show_mini_progress),
    FeatureTile(PlayerControlId.SLEEP_TIMER, Icons.Outlined.Bedtime, R.string.player_layout_show_sleep_timer),
    FeatureTile(PlayerControlId.PIP, Icons.Outlined.PictureInPictureAlt, R.string.player_layout_show_pip),
    FeatureTile(PlayerControlId.RELATED_LIST, Icons.Outlined.PlaylistPlay, R.string.player_layout_show_related_list)
)

private fun PlayerTapZoneSlot.titleRes(): Int = when (this) {
    PlayerTapZoneSlot.LEFT -> R.string.player_layout_tap_left
    PlayerTapZoneSlot.CENTER -> R.string.player_layout_tap_center
    PlayerTapZoneSlot.RIGHT -> R.string.player_layout_tap_right
}

private fun tapActionLabelRes(action: PlayerTapAction): Int = when (action) {
    PlayerTapAction.REWIND -> R.string.player_layout_action_rewind
    PlayerTapAction.PLAY_PAUSE -> R.string.player_layout_action_play
    PlayerTapAction.FORWARD -> R.string.player_layout_action_forward
    PlayerTapAction.NONE -> R.string.player_layout_action_none
}
