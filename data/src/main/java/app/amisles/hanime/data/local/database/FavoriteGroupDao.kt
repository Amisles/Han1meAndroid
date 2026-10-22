package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.FavoriteGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteGroupDao {
    @Query("SELECT * FROM favorite_groups ORDER BY createdAt ASC")
    fun getAllGroups(): Flow<List<FavoriteGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: FavoriteGroupEntity)

    @Query("DELETE FROM favorite_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    /** 删除分组时把其下收藏移回「未分组」，避免条目失去归属后不可见。 */
    @Query("UPDATE favorites SET group_id = '' WHERE group_id = :groupId")
    suspend fun detachFavoritesOfGroup(groupId: String)
}
