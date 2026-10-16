package app.amisles.hanime.core.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 容器与背景之间的边缘分离修饰符：两种主题统一使用描边。
 *
 * 曾按亮度分流（亮色阴影 / 深色描边），但阴影会溢出到 clip 之外、在浅色背景上形成灰雾，
 * 且相邻卡片的阴影互相叠加。改为统一描边后两模式表现一致，也省去每行一个阴影图层。
 *
 * 必须置于 [Modifier.background] 之后、[Modifier.clip] 之前：在 background 之前会被填充色盖住内半圈，
 * 在 clip 之后则描边外半圈被裁掉。
 */
@Composable
fun Modifier.surfaceEdge(shape: Shape, borderWidth: Dp = 1.dp): Modifier =
    this.border(borderWidth, MaterialTheme.colorScheme.outline, shape)
