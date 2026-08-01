package app.amisles.hanime.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteVideo(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val likeRate: String,
    val viewCount: String,
    val createdAt: Long
)