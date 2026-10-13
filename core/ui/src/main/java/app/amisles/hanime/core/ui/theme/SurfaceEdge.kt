package app.amisles.hanime.core.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 容器与背景之间的边缘分离修饰符：深色主题用 1dp 描边，亮色主题保留阴影。
 *
 * 必须置于 [Modifier.background] 之后、[Modifier.clip] 之前：在 background 之前会被填充色盖住内半圈，
 * 在 clip 之后则描边外半圈被裁掉。
 */
@Composable
fun Modifier.surfaceEdge(shape: Shape, elevation: Dp = 2.dp): Modifier {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        this.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        val shadowColor = Color.Black.copy(alpha = 0.18f)
        this.shadow(elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
    }
}
