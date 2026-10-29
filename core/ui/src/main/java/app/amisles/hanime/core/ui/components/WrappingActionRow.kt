package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 会按可用宽度换行的操作行：按钮保持自身宽度，放不下时整体折到下一行。
 * 本地化后的长文案不会再被压缩成竖排文字，也不会被挤出屏幕。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrappingActionRow(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    itemSpacing: Dp = 8.dp,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing, horizontalAlignment),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content
    )
}
