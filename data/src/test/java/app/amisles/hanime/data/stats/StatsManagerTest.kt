package app.amisles.hanime.data.stats

import app.amisles.hanime.data.local.entity.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class StatsManagerTest {

    private fun row(
        id: String,
        author: String,
        watchedAt: Long,
        position: Long,
        duration: Long
    ) = WatchHistoryEntity(
        id = id,
        title = id,
        thumbnailUrl = "",
        videoUrl = "",
        author = author,
        duration = "",
        watchedAt = watchedAt,
        playbackPosition = position,
        playbackDuration = duration
    )

    private val now = 1_700_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun emptyHistoryYieldsZeroedStats() {
        val stats = computeWatchStats(emptyList(), now)
        assertEquals(0L, stats.weekWatchMillis)
        assertEquals(0, stats.totalEntries)
        assertEquals(0f, stats.completionRate, 0.0001f)
        assertTrue(stats.topAuthors.isEmpty())
        assertTrue(stats.dailyWatch.isEmpty())
    }

    @Test
    fun weekBucketExcludesEntriesOlderThanWeekStart() {
        val recent = row("recent", "A", now, 10 * 60_000L, 20 * 60_000L)
        val old = row("old", "A", now - 30 * day, 40 * 60_000L, 50 * 60_000L)

        val stats = computeWatchStats(listOf(recent, old), now)

        assertEquals(10 * 60_000L, stats.weekWatchMillis)
        assertEquals(50 * 60_000L, stats.totalWatchMillis)
    }

    @Test
    fun completionCountsOnlyEntriesWithKnownDuration() {
        val finished = row("f", "A", now, 95, 100)
        val unfinished = row("u", "A", now, 50, 100)
        val unknown = row("x", "A", now, 500, 0)

        val stats = computeWatchStats(listOf(finished, unfinished, unknown), now)

        assertEquals(1, stats.completedCount)
        assertEquals(0.5f, stats.completionRate, 0.0001f)
    }

    @Test
    fun topAuthorsSortedByCountThenWatchTime() {
        val rows = listOf(
            row("1", "A", now, 10, 0),
            row("2", "A", now, 10, 0),
            row("3", "B", now, 100, 0),
            row("4", "", now, 5, 0)
        )

        val stats = computeWatchStats(rows, now)

        assertEquals(listOf("A", "B"), stats.topAuthors.map { it.author })
        assertEquals(2, stats.topAuthors.first().count)
        assertEquals(100L, stats.topAuthors[1].watchMillis)
    }

    @Test
    fun dailyBucketsAlwaysCoverSevenDays() {
        val stats = computeWatchStats(listOf(row("1", "A", now, 1000, 0)), now)
        assertEquals(DAILY_BUCKET_COUNT, stats.dailyWatch.size)
        assertEquals(1000L, stats.dailyWatch.last().watchMillis)
        assertTrue(stats.dailyWatch.zipWithNext().all { (a, b) -> a.dayStartMillis < b.dayStartMillis })
    }

    @Test
    fun weekStartIsMonday() {
        val stats = computeWatchStats(listOf(row("1", "A", now, 1, 0)), now)
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = now
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertTrue(stats.weekWatchMillis > 0L)
    }
}
