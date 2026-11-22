package app.amisles.hanime.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.amisles.hanime.feature.profile.ProfileScreen
import app.amisles.hanime.navigation.NAV_TRANSITION_DURATION_MS

internal const val HOME_SWIPE_OPEN_DP = 96f
internal const val HOME_SWIPE_VELOCITY_DP_PER_S = 600f
internal const val HOME_SWIPE_SLOP_DP = 8f

/** 「我的」抽屉：左侧滑出并覆盖整个 Scaffold；关闭动画从松手位置继续。 */
@Composable
internal fun ProfileDrawerHost(
    open: Boolean,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var closeSkip by remember { mutableFloatStateOf(0f) }
    val rawProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = NAV_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "profileDrawer"
    )
    // 关闭续接必须是乘法：松手当帧 rawProgress 仍为 1，progress = rawProgress * (1 - skip)
    // 恰好等于松手位置，随 rawProgress 归零平滑收尾。
    val progress = rawProgress * (1f - closeSkip)
    LaunchedEffect(open, rawProgress) {
        if (!open && rawProgress == 0f) closeSkip = 0f
    }
    val showOverlay = open || progress > 0.001f
    BackHandler(enabled = showOverlay, onBack = onClose)

    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) composed = true
    }
    if (!composed) return

    if (showOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(color = Color.Black, alpha = 0.5f * progress) }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
        )
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.8f)
            .graphicsLayer {
                translationX = -(1f - progress) * size.width + dragOffset
            }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                val closeThreshold = with(density) { 120.dp.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragOffset = 0f
                        closeSkip = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        dragOffset = (dragOffset + dragAmount).coerceAtMost(0f)
                        change.consume()
                    },
                    onDragEnd = {
                        val dragged = -dragOffset
                        if (dragged > closeThreshold) {
                            closeSkip = (dragged / size.width).coerceAtMost(1f)
                            dragOffset = 0f
                            onClose()
                        } else {
                            dragOffset = 0f
                            closeSkip = 0f
                        }
                    },
                    onDragCancel = {
                        dragOffset = 0f
                        closeSkip = 0f
                    }
                )
            }
    ) {
        ProfileScreen(onNavigate = onNavigate)
    }
}

/** 首页手势：单指向右滑超过阈值唤出抽屉；竖滑与反向滑放行给列表。 */
internal fun Modifier.swipeToOpenDrawer(
    touchSlopPx: Float,
    openThresholdPx: Float,
    velocityThresholdPx: Float,
    onOpen: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(touchSlopPx, openThresholdPx, velocityThresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var distanceX = 0f
            var lastX = down.position.x
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var triggered = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (change.isConsumed) return@awaitEachGesture
                val deltaX = change.position.x - lastX
                lastX = change.position.x
                distanceX += deltaX
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!triggered) {
                    if (distanceX > touchSlopPx &&
                        kotlin.math.abs(change.position.y - down.position.y) < distanceX
                    ) {
                        triggered = true
                        onOpen()
                    }
                    if (distanceX < -touchSlopPx) return@awaitEachGesture
                }
                if (triggered) change.consume()
            }
            if (triggered) return@awaitEachGesture
            if (distanceX < openThresholdPx &&
                tracker.calculateVelocity().x >= velocityThresholdPx
            ) {
                onOpen()
            }
        }
    }
)
