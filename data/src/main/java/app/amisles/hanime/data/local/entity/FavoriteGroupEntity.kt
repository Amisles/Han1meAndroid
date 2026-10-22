package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.amisles.hanime.domain.model.FavoriteGroup

/** 收藏分组表。用户自建，可改名 / 删除。 */
@Entity(tableName = "favorite_groups")
data class FavoriteGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

fun FavoriteGroupEntity.toDomain(): FavoriteGroup = FavoriteGroup(
    id = id,
    name = name,
    createdAt = createdAt
)

fun FavoriteGroup.toEntity(): FavoriteGroupEntity = FavoriteGroupEntity(
    id = id,
    name = name,
    createdAt = createdAt
)
