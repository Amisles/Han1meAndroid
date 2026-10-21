package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.domain.model.VideoSource

/** 播放器倍速 / 清晰度浮层的共用参数：浮层只读状态、只调回调，不持有任何自身状态。 */
internal data class PlayerMenuUi(
    /** 播放器容器在根坐标下的位置，用于把按钮根坐标换算成浮层局部坐标。 */
    val playerPos: Offset,
    /** 当前倍速档位；与播放器实际倍速一致（长按加速期间为设置的长按加速倍率）。 */
    val effectiveSpeed: Float,
    val playbackSpeeds: List<Float>,
    val onSpeedSelected: (Float) -> Unit,
    val onControlTap: () -> Unit,
)

internal data class QualityMenuUi(
    val playerPos: Offset,
    val sources: List<VideoSource>,
    val currentSourceUrl: String,
    val onSourceSelected: (VideoSource) -> Unit,
    val onControlTap: () -> Unit,
)

/** 弹层遮罩：点击其它区域收起浮层；自身拦截点击避免穿透。 */
@Composable
internal fun PlayerMenuScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
    )
}

/** 倍速浮层：锚定在倍速按钮上方，垂直滚动；当前档位用主题色高亮。 */
@Composable
internal fun SpeedMenuPopup(
    anchor: Rect,
    ui: PlayerMenuUi,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val menuWidthPx = with(density) { 48.dp.toPx() }
    val menuHeightPx = with(density) { 120.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val localX = anchor.center.x - ui.playerPos.x
    val localY = anchor.top - ui.playerPos.y
    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    (localX - menuWidthPx / 2f).toInt(),
                    (localY - menuHeightPx - gapPx).toInt()
                )
            }
            .width(48.dp)
            .height(120.dp)
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .verticalScroll(scrollState)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 拦截点击，避免穿透到遮罩关闭 */ }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ui.playbackSpeeds.forEach { speed ->
            Text(
                text = "${speed}x",
                color = if (speed == ui.effectiveSpeed) HanimePrimary else Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        ui.onControlTap()
                        ui.onSpeedSelected(speed)
                    },
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 清晰度浮层：锚定在清晰度按钮上方；当前源用主题色高亮。 */
@Composable
internal fun QualityMenuPopup(
    anchor: Rect,
    ui: QualityMenuUi,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val menuWidthPx = with(density) { 56.dp.toPx() }
    val menuHeightPx = with(density) { 120.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val localX = anchor.center.x - ui.playerPos.x
    val localY = anchor.top - ui.playerPos.y
    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    (localX - menuWidthPx / 2f).toInt(),
                    (localY - menuHeightPx - gapPx).toInt()
                )
            }
            .width(56.dp)
            .height(120.dp)
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .verticalScroll(scrollState)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* 拦截点击，避免穿透到遮罩关闭 */ }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ui.sources.forEach { source ->
            val isSelected = source.url == ui.currentSourceUrl
            Text(
                text = source.resolution,
                color = if (isSelected) HanimePrimary else Color.White,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        ui.onControlTap()
                        ui.onSourceSelected(source)
                    },
                textAlign = TextAlign.Center
            )
        }
    }
}
