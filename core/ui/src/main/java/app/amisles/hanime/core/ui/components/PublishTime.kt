package app.amisles.hanime.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.amisles.hanime.core.ui.R

/** 相对时间的归一化结果。 */
data class RelativeTime(val unit: RelativeTimeUnit, val value: Long = 0L)

enum class RelativeTimeUnit { JUST_NOW, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

/**
 * 站点发布时间的解析。
 *
 * 列表接口给的时间是站点渲染好的相对文案（繁体「3小時前」「2週前」），随 HTML 原样进入卡片后
 * 无法跟随应用语言。这里把它归一化为数量 + 单位，交由界面按当前语言重新输出；解析不出的输入
 * （绝对日期、站点改版后的新形态）返回 null，调用方原样展示，不会把错误信息写给用户。
 */
object PublishTime {

    // 量词按长短排序（「個月」必须先于「月」），繁简形态都收录
    private val RELATIVE_REGEX =
        Regex("^\\s*(\\d+)\\s*(分鐘|分钟|分|小時|小时|時|天|日|週|周|個月|个月|月|年)前\\s*$")

    private val JUST_NOW_TERMS = setOf("剛剛", "刚刚")

    fun parse(raw: String): RelativeTime? {
        val text = raw.trim()
        if (text in JUST_NOW_TERMS) return RelativeTime(RelativeTimeUnit.JUST_NOW)
        val match = RELATIVE_REGEX.find(text) ?: return null
        val value = match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val unit = when (match.groupValues[2]) {
            "分鐘", "分钟", "分" -> RelativeTimeUnit.MINUTES
            "小時", "小时", "時" -> RelativeTimeUnit.HOURS
            "天", "日" -> RelativeTimeUnit.DAYS
            "週", "周" -> RelativeTimeUnit.WEEKS
            "個月", "个月", "月" -> RelativeTimeUnit.MONTHS
            "年" -> RelativeTimeUnit.YEARS
            else -> return null
        }
        return RelativeTime(unit, value)
    }
}

/**
 * 站点发布时间的展示文案：可解析的相对时间按当前语言重排，其余原样返回
 * （绝对日期等本身就是语言中立或无歧义的）。
 */
@Composable
fun localizedPublishTime(raw: String): String {
    if (raw.isBlank()) return raw
    val relative = remember(raw) { PublishTime.parse(raw) } ?: return raw
    return when (relative.unit) {
        RelativeTimeUnit.JUST_NOW -> stringResource(R.string.time_just_now)
        RelativeTimeUnit.MINUTES -> stringResource(R.string.time_minutes_ago, relative.value)
        RelativeTimeUnit.HOURS -> stringResource(R.string.time_hours_ago, relative.value)
        RelativeTimeUnit.DAYS -> stringResource(R.string.time_days_ago, relative.value)
        RelativeTimeUnit.WEEKS -> stringResource(R.string.time_weeks_ago, relative.value)
        RelativeTimeUnit.MONTHS -> stringResource(R.string.time_months_ago, relative.value)
        RelativeTimeUnit.YEARS -> stringResource(R.string.time_years_ago, relative.value)
    }
}
