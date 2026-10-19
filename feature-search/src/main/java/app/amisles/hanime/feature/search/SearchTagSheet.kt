package app.amisles.hanime.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import androidx.compose.runtime.key

/** 标签格文本最多两行；所有格子统一为「两行行高 + 上下内边距」的固定高度，保证网格齐整。 */
internal const val TAG_CELL_MAX_LINES = 2

/** 标签格文本行高（也用于推算格子固定高度）。 */
internal val TAG_CELL_LINE_HEIGHT = 15.sp

/** 标签格上下内边距合计（8dp × 2）。 */
internal val TAG_CELL_VERTICAL_PADDING = 16.dp

/**
 * 标签选择底部面板（自绘，不依赖 ModalBottomSheet）。仅维护一份本地选择态，
 * [onApply] 时才把最终结果回传给 ViewModel；关闭不会自动发起请求，由调用方统一提交。
 */
@Composable
internal fun TagFilterSheet(
    initialTags: Set<String>,
    initialBroad: Boolean,
    onDismiss: () -> Unit,
    onApply: (Set<String>, Boolean) -> Unit
) {
    val selected = remember { mutableStateOf(initialTags) }
    val broad = remember { mutableStateOf(initialBroad) }
    val sizeInfo = currentWindowSizeInfo()
    val chipCols = if (sizeInfo.widthClass == WindowWidthSizeClass.Compact) 3 else 5
    // 格子固定高度从行高（sp）换算成 dp，随系统字体缩放同步放大：任何语言 / 字号下都等高且不裁切
    val tagCellHeight = with(LocalDensity.current) {
        (TAG_CELL_LINE_HEIGHT * TAG_CELL_MAX_LINES).toDp() + TAG_CELL_VERTICAL_PADDING
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 消费背景点击，避免误触遮罩关闭面板 */ }
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_tag_sheet_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 广泛匹配开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_tag_broad_match),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = broad.value,
                    onCheckedChange = { broad.value = it }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // 标签网格（可滚动）：类目标题横跨整行
            LazyVerticalGrid(
                columns = GridCells.Fixed(chipCols),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                SearchTagCatalog.groups.forEach { group ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(group.categoryRes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )
                    }
                    items(items = group.tags, key = { it.value }) { tag ->
                        val isSelected = selected.value.contains(tag.value)
                        Box(
                            modifier = Modifier
                                .height(tagCellHeight)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selected.value = if (isSelected) {
                                        selected.value - tag.value
                                    } else {
                                        selected.value + tag.value
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(tag.labelRes),
                                fontSize = 12.sp,
                                lineHeight = TAG_CELL_LINE_HEIGHT,
                                maxLines = TAG_CELL_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 底部操作栏：清除（本地） / 应用（回传）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_tag_clear),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selected.value = emptySet() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onApply(selected.value, broad.value) }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (selected.value.isEmpty()) {
                            stringResource(R.string.search_tag_apply)
                        } else {
                            stringResource(R.string.search_tag_apply_count, selected.value.size)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
