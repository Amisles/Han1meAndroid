package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloud_playlist")
data class CloudPlaylistEntity(
    @PrimaryKey val remoteId: String,
    val title: String,
    val userId: String,
    val updatedAt: Long
)