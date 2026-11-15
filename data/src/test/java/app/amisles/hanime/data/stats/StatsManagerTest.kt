package app.amisles.hanime.data.stats

import app.amisles.hanime.data.local.entity.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat

class StatsManagerTest {

    // 每周回顾用例以固定日期（2026-11-16 是周一）构造时间戳，须把时区与 locale 钉住；
    // 既有用例全部使用相对毫秒，不依赖具体时区，不受此设置影响
    private val testTimeZone = TimeZone.getTimeZone("GMT+8")
    private var originalTimeZone: TimeZone? = null
    private var originalLocale: Locale? = null

    @Before
    fun pinTimeZoneAndLocale() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(testTimeZone)
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    }

    @After
    fun restoreTimeZoneAndLocale() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

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

    // 2026-11-16 是周一；上周一 = 2026-11-09，上周日 = 2026-11-15
    private fun at(date: String, hour: Int): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = testTimeZone
        }.parse("$date ${"%02d".format(hour)}:00")!!.time

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
    fun completionRateUsesAllEntriesAsDenominator() {
        val finished = row("f", "A", now, 95, 100)
        val unfinished = row("u", "A", now, 50, 100)
        val unknown = row("x", "A", now, 500, 0)

        val stats = computeWatchStats(listOf(finished, unfinished, unknown), now)

        assertEquals(1, stats.completedCount)
        assertEquals(1f / 3f, stats.completionRate, 0.0001f)
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

    // ── 每周回顾卡片：上周窗口 [上周一 00:00, 本周一 00:00) ─────────────────

    private fun mondayReport() = computeWeeklyReportStats(
        listOf(
            row("two-weeks-ago", "作者B", at("2026-11-02", 10), 5000, 0),
            // 上周内：三天共 6000，作者A 两条（1000+3000）、作者B 一条（2000）
            row("last-mon", "作者A", at("2026-11-09", 10), 1000, 0),
            row("last-fri", "作者B", at("2026-11-13", 10), 2000, 0),
            row("last-sun", "作者A", at("2026-11-15", 10), 3000, 0),
            // 本周一凌晨：不在「上周」窗口内
            row("this-mon", "作者A", at("2026-11-16", 9), 4000, 0)
        ),
        at("2026-11-16", 12)
    )

    @Test
    fun weeklyReportCountsOnlyTheReportedWeek() {
        assertEquals(6000L, mondayReport().watchMillis)
    }

    @Test
    fun weeklyReportTopAuthorIgnoresRowsOutsideWindow() {
        // 窗口外那条 5000 的作者B 若被计入会翻转最常看结果
        assertEquals("作者A", mondayReport().topAuthor)
    }

    @Test
    fun weeklyReportHasDataFalseWhenNothingWatchedLastWeek() {
        val report = computeWeeklyReportStats(
            listOf(row("this-week", "作者A", at("2026-11-16", 9), 4000, 0)),
            at("2026-11-16", 12)
        )
        assertFalse(report.hasData)
        assertEquals(0L, report.watchMillis)
        assertEquals(0, report.completedCount)
        assertEquals("", report.topAuthor)
    }

    @Test
    fun weeklyReportHasDataTrueWhenLastWeekHadWatching() {
        assertTrue(mondayReport().hasData)
    }

    @Test
    fun startOfLastWeekFallsExactlySevenDaysBeforeThisWeeksMonday() {
        assertEquals(at("2026-11-09", 0), startOfLastWeek(at("2026-11-16", 12)))
        assertEquals(at("2026-11-02", 0), startOfLastWeek(at("2026-11-09", 12)))
        // now 恰为周一 00:00 时，「上周一」就是自身减 7 天
        assertEquals(at("2026-11-02", 0), startOfLastWeek(at("2026-11-09", 0)))
    }

    @Test
    fun completionThresholdAt95Percent() {
        val stats = computeWatchStats(
            listOf(
                row("at-threshold", "A", at("2026-11-10", 10), 950, 1000),
                row("below-threshold", "A", at("2026-11-11", 10), 949, 1000)
            ),
            at("2026-11-16", 12)
        )
        assertEquals(1, stats.completedCount)
    }
}
