package app.amisles.hanime.feature.search

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import java.util.Calendar
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.Check

/** 官网 date 参数中代表「不限定」的原值，同时用作年 / 月下拉首项的哨兵值。 */
internal const val DATE_ALL = "全部"

/** 发布日期快捷选项：[dataValue] 为官网原值（直接作为 date 参数），[labelRes] 仅用于展示。 */
internal data class DateOption(val dataValue: String, @StringRes val labelRes: Int)

internal val dateQuickOptions = listOf(
    DateOption("", R.string.search_filter_all),
    DateOption("過去 24 小時", R.string.search_date_past_24h),
    DateOption("過去 2 天", R.string.search_date_past_2d),
    DateOption("過去 1 週", R.string.search_date_past_1w),
    DateOption("過去 1 個月", R.string.search_date_past_1m),
    DateOption("過去 3 個月", R.string.search_date_past_3m),
    DateOption("過去 1 年", R.string.search_date_past_1y)
)

/**
 * 年份下拉：首项「全部」表示不限定年份。取值必须是官网原值（如 `2026 年`）——既要用于回显比较，
 * 也会直接拼进 date 参数，故不参与本地化；展示由 [yearDisplay] 按各语言格式串渲染。
 */
internal val dateYearOptions: List<String> =
    listOf(DATE_ALL) + (Calendar.getInstance().get(Calendar.YEAR) downTo 1990).map { "$it 年" }

/** 月份下拉：首项「全部」表示不限定月份。取值同为官网原值（`3 月`），展示由 [monthDisplay] 负责。 */
internal val dateMonthOptions: List<String> = listOf(DATE_ALL) + (1..12).map { "$it 月" }

/**
 * 筛选 chip 展示文案：命中快捷项取本地化 label；年 / 月自定义值按本地化格式重组，
 * 避免把官网原值的繁体措辞带进其它语言的界面。
 */
@Composable
internal fun dateChipLabel(value: String): String {
    dateQuickOptions.firstOrNull { it.dataValue == value }?.let { return stringResource(it.labelRes) }
    val year = dateYearOf(value)
    val month = dateMonthOf(value)
    val parts = buildList {
        if (year != DATE_ALL) add(yearDisplay(year))
        if (month != DATE_ALL) add(monthDisplay(month))
    }
    return if (parts.isEmpty()) stringResource(R.string.search_filter_all) else parts.joinToString(" ")
}

/**
 * 由「年 / 月」下拉值合成官网 date 参数：仅年 → `YYYY 年`，仅月 → `M 月`，两者皆「全部」→ 空串。
 * 拼接格式依据官网下拉选项文案推断，尚未与官网 JS 逐字核对，实测不一致时以官网请求为准。
 */
internal fun buildDateValue(year: String, month: String): String {
    val y = if (year == DATE_ALL) "" else year
    val m = if (month == DATE_ALL) "" else month
    return listOf(y, m).filter { it.isNotEmpty() }.joinToString(" ")
}

internal val DATE_YEAR_REGEX = Regex("^(\\d{4}) 年")
internal val DATE_MONTH_REGEX = Regex("(\\d{1,2}) 月")

/** 从已有 date 值反解年份下拉项（无年份 → 全部），用于再次打开面板时回显。 */
internal fun dateYearOf(value: String): String =
    DATE_YEAR_REGEX.find(value)?.let { it.groupValues[1] + " 年" } ?: DATE_ALL

/** 从已有 date 值反解月份下拉项（无月份 → 全部）。 */
internal fun dateMonthOf(value: String): String =
    DATE_MONTH_REGEX.find(value)?.let { it.groupValues[1] + " 月" } ?: DATE_ALL

/** 年份下拉展示文案：官网原值只取数字部分，由各语言的 `search_date_year_format` 决定是否补「年」。 */
@Composable
internal fun yearDisplay(value: String): String =
    if (value == DATE_ALL) {
        stringResource(R.string.search_filter_all)
    } else {
        stringResource(R.string.search_date_year_format, value.removeSuffix(" 年").trim())
    }

/** 月份下拉展示文案：官网原值（`3 月`）按序号取本地化月份名；官网 date 参数仍用原值。 */
@Composable
internal fun monthDisplay(value: String): String {
    if (value == DATE_ALL) return stringResource(R.string.search_filter_all)
    val index = value.removeSuffix(" 月").trim().toIntOrNull() ?: return value
    return stringArrayResource(R.array.search_date_month_names).getOrNull(index - 1) ?: value
}

/**
 * 发布日期选择底部面板（自绘，风格与 [TagFilterSheet] 一致）。快捷项与「年 / 月」互斥：
 * 点选快捷项即清空年月，改动年月即取消快捷项。[onApply] 回传最终 date 值（空串 = 全部）。
 */
@Composable
internal fun DateFilterSheet(
    initialDate: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    // 当前选中的快捷项 dataValue；null 表示由「年 / 月」自定义
    val quick = remember { mutableStateOf(dateQuickOptions.firstOrNull { it.dataValue == initialDate }?.dataValue) }
    val year = remember { mutableStateOf(dateYearOf(initialDate)) }
    val month = remember { mutableStateOf(dateMonthOf(initialDate)) }

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
                .fillMaxHeight(0.8f)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 消费背景点击，避免误触遮罩关闭面板 */ }
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_date_sheet_title),
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

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // 快捷项（单选）
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(items = dateQuickOptions, key = { it.dataValue }) { option ->
                    val isSelected = quick.value == option.dataValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                quick.value = option.dataValue
                                year.value = DATE_ALL
                                month.value = DATE_ALL
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // 自定义年 / 月
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateDropdown(
                    value = year.value,
                    options = dateYearOptions,
                    display = { yearDisplay(it) },
                    modifier = Modifier.weight(1f),
                    onSelect = {
                        year.value = it
                        quick.value = null
                    }
                )
                DateDropdown(
                    value = month.value,
                    options = dateMonthOptions,
                    display = { monthDisplay(it) },
                    modifier = Modifier.weight(1f),
                    onSelect = {
                        month.value = it
                        quick.value = null
                    }
                )
            }

            // 底部操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_sheet_cancel),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            val value = quick.value ?: buildDateValue(year.value, month.value)
                            onApply(value)
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_sheet_show_results),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
