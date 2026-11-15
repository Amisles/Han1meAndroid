package app.amisles.hanime.data.stats

import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.local.entity.WatchHistoryEntity
import app.amisles.hanime.domain.model.AuthorWatch
import app.amisles.hanime.domain.model.DailyWatch
import app.amisles.hanime.domain.model.WatchStats
import app.amisles.hanime.domain.model.WeeklyReportStats
import app.amisles.hanime.domain.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsManager @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao
) : StatsRepository {

    override suspend fun loadWatchStats(): WatchStats = withContext(Dispatchers.IO) {
        computeWatchStats(watchHistoryDao.getAllOnce(), System.currentTimeMillis())
    }

    override suspend fun loadWeeklyReportStats(): WeeklyReportStats = withContext(Dispatchers.IO) {
        computeWeeklyReportStats(watchHistoryDao.getAllOnce(), System.currentTimeMillis())
    }
}

internal const val TOP_AUTHOR_LIMIT = 5
internal const val DAILY_BUCKET_COUNT = 7
internal const val COMPLETION_PERCENT = 95

internal fun computeWatchStats(
    rows: List<WatchHistoryEntity>,
    nowMillis: Long
): WatchStats {
    if (rows.isEmpty()) {
        return WatchStats(
            weekWatchMillis = 0L,
            totalWatchMillis = 0L,
            totalEntries = 0,
            completedCount = 0,
            completionRate = 0f,
            topAuthors = emptyList(),
            dailyWatch = emptyList()
        )
    }

    val weekStart = startOfWeek(nowMillis)
    var weekWatch = 0L
    var totalWatch = 0L
    var finished = 0
    val authorCount = linkedMapOf<String, Int>()
    val authorWatch = linkedMapOf<String, Long>()

    rows.forEach { row ->
        val watched = row.playbackPosition.coerceAtLeast(0L)
        totalWatch += watched
        if (row.watchedAt >= weekStart) weekWatch += watched

        if (row.playbackDuration > 0L &&
            row.playbackPosition * 100L >= row.playbackDuration * COMPLETION_PERCENT
        ) {
            finished++
        }

        val author = row.author.trim()
        if (author.isNotEmpty()) {
            authorCount[author] = (authorCount[author] ?: 0) + 1
            authorWatch[author] = (authorWatch[author] ?: 0L) + watched
        }
    }

    val topAuthors = authorCount.entries
        .map { AuthorWatch(it.key, it.value, authorWatch[it.key] ?: 0L) }
        .sortedWith(compareByDescending<AuthorWatch> { it.count }.thenByDescending { it.watchMillis })
        .take(TOP_AUTHOR_LIMIT)

    return WatchStats(
        weekWatchMillis = weekWatch,
        totalWatchMillis = totalWatch,
        totalEntries = rows.size,
        completedCount = finished,
        completionRate = if (rows.isNotEmpty()) finished.toFloat() / rows.size else 0f,
        topAuthors = topAuthors,
        dailyWatch = buildDailyBuckets(rows, nowMillis)
    )
}

internal fun startOfWeek(nowMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = nowMillis
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
}.timeInMillis

/**
 * 上周一 00:00：每周回顾卡片的统计窗口下界，上界为 [startOfWeek]（本周一 00:00）。
 * 经 Calendar.add 回退 7 天而非毫秒减法，避免夏令时周（一整天 23/25 小时）把边界偏出整小时。
 */
internal fun startOfLastWeek(nowMillis: Long): Long {
    val weekStart = startOfWeek(nowMillis)
    return Calendar.getInstance().apply {
        timeInMillis = weekStart
        add(Calendar.DAY_OF_YEAR, -7)
    }.timeInMillis
}

/**
 * 每周观看回顾卡片的统计：窗口为 [上周一 00:00, 本周一 00:00)。
 *
 * 与卡片文案「上周共观看 / 看完了 N 部 / 最常看」逐项对齐；统计页的
 * [computeWatchStats] 是全历史口径，两者各自独立，不可互相替代。
 */
internal fun computeWeeklyReportStats(
    rows: List<WatchHistoryEntity>,
    nowMillis: Long
): WeeklyReportStats {
    val lastWeekStart = startOfLastWeek(nowMillis)
    val weekStart = startOfWeek(nowMillis)
    var watchMillis = 0L
    var finished = 0
    val authorCount = linkedMapOf<String, Int>()
    val authorWatch = linkedMapOf<String, Long>()

    rows.forEach { row ->
        if (row.watchedAt < lastWeekStart || row.watchedAt >= weekStart) return@forEach
        val watched = row.playbackPosition.coerceAtLeast(0L)
        watchMillis += watched
        if (row.playbackDuration > 0L &&
            row.playbackPosition * 100L >= row.playbackDuration * COMPLETION_PERCENT
        ) {
            finished++
        }
        val author = row.author.trim()
        if (author.isNotEmpty()) {
            authorCount[author] = (authorCount[author] ?: 0) + 1
            authorWatch[author] = (authorWatch[author] ?: 0L) + watched
        }
    }

    val topAuthor = authorCount.entries
        .maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenByDescending { authorWatch[it.key] ?: 0L }
        )
        ?.key
        .orEmpty()

    return WeeklyReportStats(watchMillis = watchMillis, completedCount = finished, topAuthor = topAuthor)
}

private fun buildDailyBuckets(rows: List<WatchHistoryEntity>, nowMillis: Long): List<DailyWatch> {
    val dayStarts = ArrayList<Long>(DAILY_BUCKET_COUNT)
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    repeat(DAILY_BUCKET_COUNT) {
        dayStarts.add(calendar.timeInMillis)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    dayStarts.reverse()

    val buckets = LongArray(DAILY_BUCKET_COUNT)
    rows.forEach { row ->
        val watched = row.playbackPosition.coerceAtLeast(0L)
        if (watched <= 0L) return@forEach
        for (i in dayStarts.indices.reversed()) {
            if (row.watchedAt >= dayStarts[i]) {
                buckets[i] += watched
                break
            }
        }
    }
    return dayStarts.mapIndexed { index, start -> DailyWatch(start, buckets[index]) }
}
