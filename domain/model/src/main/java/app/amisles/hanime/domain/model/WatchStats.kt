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
