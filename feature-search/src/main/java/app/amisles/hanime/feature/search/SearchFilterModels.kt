package app.amisles.hanime.feature.search

import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.model.Category
import app.amisles.hanime.core.ui.model.categories

/** 「全部分类」哨兵：apiValue 为空表示搜索时不带 genre 参数。 */
val allCategory = Category("", R.string.search_all, "")

/** 分类筛选项：首位为「全部」，其后为站点的全部分类。 */
val filterTypes = listOf(allCategory) + categories

/**
 * 排序选项。[label] 与 [apiValue] 只是匹配键（站点简中 / 繁中两套用词），
 * 用于把导航传入的 `initialSort` 映射到具体选项；[labelRes] 才用于界面展示。
 */
data class SortOption(val label: String, val labelRes: Int, val apiValue: String)

val sortOptions = listOf(
    SortOption("最新上市", R.string.search_sort_new_release, "最新上市"),
    SortOption("最新上传", R.string.search_sort_new_upload, "最新上傳"),
    SortOption("本日排行", R.string.search_sort_daily, "本日排行"),
    SortOption("本周排行", R.string.search_sort_weekly, "本週排行"),
    SortOption("本月排行", R.string.search_sort_monthly, "本月排行"),
    SortOption("观看次数", R.string.search_sort_views, "觀看次數"),
    SortOption("点赞比例", R.string.search_sort_like_ratio, "點讚比例"),
    SortOption("时长最长", R.string.search_sort_longest, "時長最長"),
    SortOption("他们在看", R.string.search_sort_watching, "他們在看")
)
