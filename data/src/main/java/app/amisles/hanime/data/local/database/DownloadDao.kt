package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.domain.model.DownloadEntity

@Dao
interface DownloadDao {
    // P2-4：移除未使用的 Flow 版 getAllDownloads()（仅 getAllDownloadsOnce 被 DownloadManager 使用）。
    @Query("SELECT * FROM download_tasks ORDER BY id DESC")
    suspend fun getAllDownloadsOnce(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(task: DownloadEntity)

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    suspend fun deleteDownload(taskId: Int)

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Query("SELECT MAX(id) FROM download_tasks")
    suspend fun getMaxId(): Int?
}