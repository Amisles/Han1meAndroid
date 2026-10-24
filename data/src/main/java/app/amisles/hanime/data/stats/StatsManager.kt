package app.amisles.hanime.data.stats

import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.local.entity.WatchHistoryEntity
import app.amisles.hanime.domain.model.AuthorWatch
import app.amisles.hanime.domain.model.DailyWatch
import app.amisles.hanime.domain.model.WatchStats
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

private fun startOfWeek(nowMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = nowMillis
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
}.timeInMillis

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
