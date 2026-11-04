package app.amisles.hanime.data.local.entity

import androidx.room.Entity

@Entity(tableName = "playlist_membership", primaryKeys = ["userId", "videoId", "target"])
data class PlaylistMembershipEntity(
    val userId: String,
    val videoId: String,
    val target: String,
    val saved: Boolean,
    val updatedAt: Long,
    val dirty: Boolean
)