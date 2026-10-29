package app.amisles.hanime.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Shape
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll

/** 搜索结果「加载中」页脚：列表与网格共用，网格侧由调用方指定 span 横跨整行。 */
@Composable
internal fun SearchLoadMoreIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** 搜索结果「加载更多失败」页脚：点一下重试同一页，不清空已有结果。 */
@Composable
internal fun SearchLoadMoreRetry(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.search_load_more_failed),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetry)
            .padding(20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** 搜索结果「没有更多了」页脚：列表与网格共用。 */
@Composable
internal fun SearchNoMoreHint() {
    Text(
        text = stringResource(R.string.search_no_more),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

/** 筛选入口 chip（标签 / 日期 / 时长共用）：文字右侧固定带下拉箭头，与点一下就选中的「分类」chip 区分。 */
@Composable
internal fun FilterEntryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .clickable { onClick() }
            .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 搜索历史：与详情页标签同款的矩形贴片，横向流式排列、超宽自动换行。
 *
 * 每个贴片右侧内嵌删除按钮；两者各自 `clickable`，内层删除按钮优先消费点击，
 * 因此点 × 不会连带触发关键词搜索。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchHistoryChips(
    history: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        history.forEach { keyword ->
            val chipShape = RoundedCornerShape(4.dp)
            Row(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .background(MaterialTheme.colorScheme.surface, chipShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, chipShape)
                    .clip(chipShape)
                    .clickable { onSearch(keyword) }
                    .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = keyword,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(keyword) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 排序下拉：锚定在按钮底边、自上而下垂直展开，收起时反向缩回，视觉上像从按钮里拉出。
 *
 * 独立成顶层函数而非内联在筛选栏的 Row 内：`AnimatedVisibility` 有 `RowScope` 扩展重载，
 * 内联时会优先解析到该重载，而 `Popup` 的 content 不是 `RowScope`，编译器会拒绝隐式接收者。
 */
@Composable
internal fun SortMenuPopup(
    transition: MutableTransitionState<Boolean>,
    width: Dp,
    shape: Shape,
    selected: SortOption?,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    if (transition.isIdle && !transition.currentState) return

    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                    return IntOffset(
                        x = anchorBounds.left.coerceIn(0, maxX),
                        y = anchorBounds.bottom
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = transition,
            enter = expandVertically(
                animationSpec = tween(180),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(
                animationSpec = tween(140),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(90))
        ) {
            Column(
                modifier = Modifier
                    .width(width)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .clip(shape)
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sortOptions.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            fontSize = 13.sp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 年 / 月下拉按钮（自绘，与筛选栏排序下拉同款视觉；官网年份较多，故限定最大高度并可滚动）。 */
@Composable
internal fun DateDropdown(
    value: String,
    options: List<String>,
    /** 把官网原值渲染成本地化的展示文案（年 / 月各一套）。 */
    display: @Composable (String) -> String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = display(value),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == value
                DropdownMenuItem(
                    text = {
                        Text(
                            text = display(option),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
