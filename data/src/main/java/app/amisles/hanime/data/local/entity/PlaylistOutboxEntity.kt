package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_outbox",
    indices = [Index(value = ["userId", "videoId", "target"], unique = true)]
)
data class PlaylistOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val videoId: String,
    val target: String,
    val desired: Boolean,
    val createdAt: Long,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0
)