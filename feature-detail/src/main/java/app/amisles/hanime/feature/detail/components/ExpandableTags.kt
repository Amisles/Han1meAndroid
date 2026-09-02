package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 可展开的多行标签布局。
 *
 * 标签按流式排列，默认最多显示 [maxLines] 行；超过时在最下方居中显示一个展开/收起按钮，
 * 点击后展示全部标签。收起后恢复为最多 [maxLines] 行。
 */
@Composable
internal fun ExpandableTags(
    tags: List<String>,
    maxLines: Int = 2,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Layout(
        content = {
            tags.forEach { tag ->
                val cleanedTag = remember(tag) {
                    tag
                        .trimStart('#')
                        .trim()
                        .replace(Regex("[\\(（][\\d+]+[\\)）]$"), "")
                        .trim()
                }
                Text(
                    text = tag,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .clickable { onTagClick(cleanedTag) }
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val hSpacingPx = 8.dp.roundToPx()
        val vSpacingPx = 8.dp.roundToPx()

        val tagMeasurables = measurables.dropLast(1)
        val toggleMeasurable = measurables.last()

        val tagPlaceables = tagMeasurables.map { it.measure(constraints) }
        val togglePlaceable = toggleMeasurable.measure(constraints)

        data class TagRow(
            val placeables: List<Placeable>,
            val width: Int,
            val height: Int
        )

        // 完整流式排布（不含 toggle）
        val allRows = mutableListOf<TagRow>()
        var currentRow = mutableListOf<Placeable>()
        var currentWidth = 0
        var currentHeight = 0

        tagPlaceables.forEach { placeable ->
            if (currentRow.isNotEmpty() && currentWidth + hSpacingPx + placeable.width > constraints.maxWidth) {
                allRows.add(TagRow(currentRow, currentWidth, currentHeight))
                currentRow = mutableListOf()
                currentWidth = 0
                currentHeight = 0
            }
            currentRow.add(placeable)
            currentWidth += if (currentRow.size == 1) placeable.width else hSpacingPx + placeable.width
            if (placeable.height > currentHeight) currentHeight = placeable.height
        }
        if (currentRow.isNotEmpty()) {
            allRows.add(TagRow(currentRow, currentWidth, currentHeight))
        }

        val needsToggle = allRows.size > maxLines

        // 在指定行末尾尝试放入 toggle：放不下则从行尾移除标签，直至能放下或行为空
        fun appendToggleToRow(row: TagRow): TagRow {
            val rowTags = row.placeables.toMutableList()
            var rowWidth = row.width
            while (rowTags.isNotEmpty() && rowWidth + hSpacingPx + togglePlaceable.width > constraints.maxWidth) {
                rowTags.removeAt(rowTags.lastIndex)
                rowWidth = if (rowTags.isEmpty()) {
                    0
                } else {
                    rowTags.sumOf { it.width } + (rowTags.size - 1) * hSpacingPx
                }
            }
            val newWidth = if (rowTags.isEmpty()) {
                togglePlaceable.width
            } else {
                rowWidth + hSpacingPx + togglePlaceable.width
            }
            val newHeight = if (rowTags.isEmpty() || row.height > togglePlaceable.height) {
                if (rowTags.isEmpty()) togglePlaceable.height else row.height
            } else {
                togglePlaceable.height
            }
            return TagRow(rowTags + togglePlaceable, newWidth, newHeight)
        }

        val displayRows: MutableList<TagRow> = when {
            !needsToggle -> allRows
            !expanded -> {
                // 折叠：仅显示前两行，toggle 置于第 maxLines 行（第二行）末尾
                val firstRows = allRows.take(maxLines).toMutableList()
                firstRows[firstRows.lastIndex] = appendToggleToRow(firstRows.last())
                firstRows
            }
            else -> {
                // 展开：显示全部行，toggle 置于最后一行末尾；放不下则单独成行
                val last = allRows.last()
                if (last.width + hSpacingPx + togglePlaceable.width <= constraints.maxWidth) {
                    val newRows = allRows.toMutableList()
                    newRows[newRows.lastIndex] = TagRow(
                        last.placeables + togglePlaceable,
                        last.width + hSpacingPx + togglePlaceable.width,
                        if (last.height > togglePlaceable.height) last.height else togglePlaceable.height
                    )
                    newRows
                } else {
                    val newRows = allRows.toMutableList()
                    newRows.add(TagRow(listOf(togglePlaceable), togglePlaceable.width, togglePlaceable.height))
                    newRows
                }
            }
        }

        val contentHeight = if (displayRows.isEmpty()) {
            0
        } else {
            displayRows.sumOf { it.height } + (displayRows.size - 1) * vSpacingPx
        }
        val totalHeight = contentHeight

        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            displayRows.forEach { row ->
                var x = 0
                row.placeables.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hSpacingPx
                }
                y += row.height + vSpacingPx
            }
        }
    }
}
