package app.amisles.hanime.domain.model

/**
 * 观看历史（领域模型）。
 *
 * 持久化由 `:data` 的 `WatchHistoryEntity` 负责，本类不再携带 Room 注解：
 * 领域层不应感知持久化框架。
 */
data class WatchHistory(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val watchedAt: Long,
    /** 播放进度记忆：上次播放位置（毫秒），用于下次续播 */
    val playbackPosition: Long = 0L,
    /** 播放进度记忆：视频总时长（毫秒） */
    val playbackDuration: Long = 0L
)
