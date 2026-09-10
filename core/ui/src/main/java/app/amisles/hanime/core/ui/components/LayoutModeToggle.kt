package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.R

/**
 * 列表 / 网格布局切换：两段式图标，选中段用主题色底标示当前模式。
 *
 * 之所以放在 core:ui 而不是搜索页，是因为 `material-icons-extended` 只在本模块声明了依赖，
 * 搜索页无需为两个图标额外引入图标库；同时这里只暴露布尔状态，不依赖任何业务枚举，
 * 保证它是个纯粹的展示控件。
 */
@Composable
fun LayoutModeToggle(
    isGrid: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LayoutModeToggleSegment(
            selected = !isGrid,
            icon = Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(R.string.search_layout_list),
            onClick = { onToggle(false) }
        )
        LayoutModeToggleSegment(
            selected = isGrid,
            icon = Icons.Default.GridView,
            contentDescription = stringResource(R.string.search_layout_grid),
            onClick = { onToggle(true) }
        )
    }
}

/**
 * 单个切换段。高度与同页的排序下拉对齐（约 34dp）；
 * 触摸目标小于 48dp 是有意取舍 —— 与排序下拉保持同一视觉节奏。
 */
@Composable
private fun LayoutModeToggleSegment(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )
    }
}
