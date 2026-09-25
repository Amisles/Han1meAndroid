package app.amisles.hanime.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.amisles.hanime.domain.model.WatchHistory

/**
 * 观看历史表实体。
 *
 * 表名与列名与 v6 schema 完全一致（仅 Kotlin 类名变化），故无需新增 Migration。
 */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val watchedAt: Long,
    // 播放进度记忆：上次播放位置与视频总时长（毫秒），用于下次续播
    @ColumnInfo(name = "playback_position")
    val playbackPosition: Long = 0L,
    @ColumnInfo(name = "playback_duration")
    val playbackDuration: Long = 0L
)

fun WatchHistoryEntity.toDomain(): WatchHistory = WatchHistory(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = videoUrl,
    author = author,
    duration = duration,
    watchedAt = watchedAt,
    playbackPosition = playbackPosition,
    playbackDuration = playbackDuration
)

fun WatchHistory.toEntity(): WatchHistoryEntity = WatchHistoryEntity(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = videoUrl,
    author = author,
    duration = duration,
    watchedAt = watchedAt,
    playbackPosition = playbackPosition,
    playbackDuration = playbackDuration
)
