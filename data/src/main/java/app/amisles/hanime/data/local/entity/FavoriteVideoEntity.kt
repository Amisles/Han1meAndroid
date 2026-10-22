package app.amisles.hanime.data.local.entity

import androidx.room.ColumnInfo
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
    val createdAt: Long,
    // 所属分组；空串表示「未分组」，与「全部分组」区分
    @ColumnInfo(name = "group_id")
    val groupId: String = "",
    // 用户自定义标签，逗号分隔。用单列存储避免为标签再开一张多对多表
    val tags: String = ""
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
    createdAt = createdAt,
    groupId = groupId,
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
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
    createdAt = createdAt,
    groupId = groupId,
    // 标签里可能含逗号，落库前统一替换，避免读回时被错误拆分
    tags = tags.joinToString(",") { it.replace(',', ' ') }
)
