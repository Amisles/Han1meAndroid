package app.amisles.hanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 搜索历史表实体。
 *
 * 表名与列名与 v6 schema 完全一致（由 domain 迁入 data），故无需新增 Migration。
 * 实体属于持久化实现细节，不应放在 domain 层。
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val searchedAt: Long
)
