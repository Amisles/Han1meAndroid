package app.amisles.hanime.domain.model

data class AuthorWatch(
    val author: String,
    val count: Int,
    val watchMillis: Long
)

data class DailyWatch(
    val dayStartMillis: Long,
    val watchMillis: Long
)

data class WatchStats(
    val weekWatchMillis: Long,
    val totalWatchMillis: Long,
    val totalEntries: Int,
    val completedCount: Int,
    val completionRate: Float,
    val topAuthors: List<AuthorWatch>,
    val dailyWatch: List<DailyWatch>
) {
    val hasData: Boolean get() = totalEntries > 0
}

/**
 * 每周观看回顾卡片的数据：统计窗口为上周一 00:00 至本周一 00:00，与卡片文案的「上周」对齐。
 * 各项均只统计窗口内的观看记录，与统计页的全历史口径（[WatchStats]）互不相干。
 */
data class WeeklyReportStats(
    val watchMillis: Long,
    val completedCount: Int,
    val topAuthor: String
) {
    val hasData: Boolean get() = watchMillis > 0L || completedCount > 0
}
