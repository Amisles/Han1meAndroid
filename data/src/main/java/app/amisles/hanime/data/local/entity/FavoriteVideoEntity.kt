package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.amisles.hanime.domain.model.FavoriteVideo

/**
 * 收藏表实体。
 *
 * 表名与列名与 v6 schema 完全一致（仅 Kotlin 类名变化，Room 的 schema 标识与 identityHash
 * 只由表/列定义推导，不含类名），因此无需新增 Migration。
 */
@Entity(tableName = "favorites")
data class FavoriteVideoEntity(
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

fun FavoriteVideoEntity.toDomain(): FavoriteVideo = FavoriteVideo(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = videoUrl,
    author = author,
    duration = duration,
    likeRate = likeRate,
    viewCount = viewCount,
    createdAt = createdAt
)

fun FavoriteVideo.toEntity(): FavoriteVideoEntity = FavoriteVideoEntity(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    videoUrl = videoUrl,
    author = author,
    duration = duration,
    likeRate = likeRate,
    viewCount = viewCount,
    createdAt = createdAt
)
