package app.amisles.hanime.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val watchedAt: Long,
    // 播放进度记忆：上次播放位置与视频总时长（毫秒），用于下次进入续播
    @ColumnInfo(name = "playback_position")
    val playbackPosition: Long = 0L,
    @ColumnInfo(name = "playback_duration")
    val playbackDuration: Long = 0L
)