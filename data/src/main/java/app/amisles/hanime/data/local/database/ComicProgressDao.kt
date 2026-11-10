package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.ComicProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicProgressDao {

    @Query("SELECT * FROM comic_progress ORDER BY updated_at DESC")
    fun getAllProgress(): Flow<List<ComicProgressEntity>>

    @Query("SELECT * FROM comic_progress WHERE comicId = :comicId")
    fun observeProgress(comicId: String): Flow<ComicProgressEntity?>

    @Query("SELECT * FROM comic_progress WHERE comicId = :comicId")
    suspend fun getProgress(comicId: String): ComicProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ComicProgressEntity)

    /** 只翻页不换书时只更新 last_page 与 updated_at 两列，避免整行 REPLACE。 */
    @Query("UPDATE comic_progress SET last_page = :page, updated_at = :updatedAt WHERE comicId = :comicId")
    suspend fun updateLastPage(comicId: String, page: Int, updatedAt: Long): Int

    @Query("DELETE FROM comic_progress WHERE comicId = :comicId")
    suspend fun remove(comicId: String)

    @Query("DELETE FROM comic_progress")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM comic_progress")
    suspend fun count(): Int
}
