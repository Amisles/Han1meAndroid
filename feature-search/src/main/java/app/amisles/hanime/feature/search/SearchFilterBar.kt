package app.amisles.hanime.feature.search

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.LayoutModeToggle
import app.amisles.hanime.core.ui.model.Category
import app.amisles.hanime.core.ui.theme.LocalUiConfig

@Composable
internal fun SearchFilterBar(
    genreValue: String?,
    tagsValue: List<String>,
    dateValue: String,
    durationValue: String,
    selectedFilter: Category,
    selectedSort: SortOption?,
    sortMenuExpanded: Boolean,
    isGridMode: Boolean,
    onSortMenuExpandedChange: (Boolean) -> Unit,
    onSelectFilter: (Category) -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onClearFilters: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenDate: () -> Unit,
    onOpenDuration: () -> Unit,
    onToggleLayout: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 15.dp, end = 15.dp, bottom = 6.dp)
    ) {
        // 是否存在任何已生效的筛选条件（决定是否显示「清除筛选」入口）
        val hasActiveFilter = genreValue != null || tagsValue.isNotEmpty() ||
            dateValue.isNotEmpty() || durationValue.isNotEmpty()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 15.dp)
        ) {
            // 一键清除全部筛选：仅在确有筛选生效时出现
            if (hasActiveFilter) {
                item(key = "clear_filters_chip") {
                    val clearShape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
                    Box(
                        modifier = Modifier
                            .clip(clearShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = clearShape
                            )
                            .clickable {
                                onClearFilters()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.search_filter_clear_all),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 筛选入口组：点击打开底部选择面板，带下拉箭头
            item(key = "tag_filter_chip") {
                val count = tagsValue.size
                FilterEntryChip(
                    label = if (count > 0) {
                        stringResource(R.string.search_filter_tag_count, count)
                    } else {
                        stringResource(R.string.search_filter_tag)
                    },
                    selected = count > 0,
                    onClick = { onOpenTags() }
                )
            }
            item(key = "date_filter_chip") {
                FilterEntryChip(
                    label = if (dateValue.isNotEmpty()) {
                        dateChipLabel(dateValue)
                    } else {
                        stringResource(R.string.search_filter_date)
                    },
                    selected = dateValue.isNotEmpty(),
                    onClick = { onOpenDate() }
                )
            }
            item(key = "duration_filter_chip") {
                FilterEntryChip(
                    label = if (durationValue.isNotEmpty()) {
                        durationChipLabel(durationValue)
                    } else {
                        stringResource(R.string.search_filter_duration)
                    },
                    selected = durationValue.isNotEmpty(),
                    onClick = { onOpenDuration() }
                )
            }
            // 组间分隔线：分开「筛选入口」与「分类」单选
            item(key = "filter_group_divider") {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
            }
            items(items = filterTypes, key = { it.displayRes }) { filter ->
                val isSelected = selectedFilter.apiValue == filter.apiValue
                val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = shape
                        )
                        .clickable {
                            onSelectFilter(filter)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(filter.displayRes),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_sort),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 按钮与下拉菜单拼成一个整体矩形：展开时按钮去下圆角、菜单去上圆角，
            // 菜单紧贴按钮底部，且容器色、描边与按钮一致
            val sortRadius = LocalUiConfig.current.cardCornerRadius
            val sortWidth = 116.dp
            val closedShape = RoundedCornerShape(sortRadius)
            val buttonShape = if (sortMenuExpanded) {
                RoundedCornerShape(topStart = sortRadius, topEnd = sortRadius)
            } else {
                closedShape
            }
            val menuShape = RoundedCornerShape(bottomStart = sortRadius, bottomEnd = sortRadius)
            val arrowRotation by animateFloatAsState(
                targetValue = if (sortMenuExpanded) 180f else 0f,
                label = "sortArrow"
            )
            // 用 MutableTransitionState 驱动进出动画：收起时需等动画结束再移除 Popup
            val menuTransition = remember { MutableTransitionState(false) }
            menuTransition.targetState = sortMenuExpanded
            Box {
                Row(
                    modifier = Modifier
                        .width(sortWidth)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = buttonShape
                        )
                        .clip(buttonShape)
                        .clickable { onSortMenuExpandedChange(!sortMenuExpanded) }
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSort?.let { stringResource(it.labelRes) }
                            ?: stringResource(R.string.search_sort_default),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.search_sort),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = arrowRotation }
                    )
                }
                // 锚定在按钮下方，用垂直展开代替菜单默认的中心缩放，
                // 视觉上是从按钮底边向下拉出；退出同样朝向按钮收起
                SortMenuPopup(
                    transition = menuTransition,
                    width = sortWidth,
                    shape = menuShape,
                    selected = selectedSort,
                    onSelect = { option -> onSelectSort(option) },
                    onDismiss = { onSortMenuExpandedChange(false) }
                )
            }

            // 布局模式切换：右对齐，与排序下拉同一行；写入即视为用户已显式选择
            Spacer(modifier = Modifier.weight(1f))
            LayoutModeToggle(
                isGrid = isGridMode,
                onToggle = onToggleLayout
            )
        }
    }
}
