package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.amisles.hanime.domain.model.WatchLaterVideo

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val author: String,
    val duration: String,
    val addedAt: Long
)

fun WatchLaterEntity.toDomain(): WatchLaterVideo = WatchLaterVideo(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = videoUrl,
    author = author,
    duration = duration,
    addedAt = addedAt
)

fun WatchLaterVideo.toEntity(): WatchLaterEntity = WatchLaterEntity(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = videoUrl,
    author = author,
    duration = duration,
    addedAt = addedAt
)
