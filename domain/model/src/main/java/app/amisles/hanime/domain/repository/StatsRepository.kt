package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.model.WatchStats
import app.amisles.hanime.domain.model.WeeklyReportStats

interface StatsRepository {
    /** 统计页的全历史口径：各项不做周期过滤，与页面标签对应。 */
    suspend fun loadWatchStats(): WatchStats

    /** 每周回顾卡片的上周口径：窗口为上周一 00:00 至本周一 00:00。 */
    suspend fun loadWeeklyReportStats(): WeeklyReportStats
}
