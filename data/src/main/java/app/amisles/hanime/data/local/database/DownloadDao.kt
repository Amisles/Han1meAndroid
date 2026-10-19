package app.amisles.hanime.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.amisles.hanime.data.local.entity.DownloadEntity

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY id DESC")
    suspend fun getAllDownloadsOnce(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(task: DownloadEntity)

    /**
     * 批量 upsert：一次事务写入多条，供持久化消费端合并提交。
     * 下载任务的进度刷新在每次状态切换时都会触发，逐条 upsert 在批量添加
     * 或重试全部时会产生 N 次独立事务（每次 fsync），拖慢与下载竞争的磁盘 IO。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<DownloadEntity>)

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    suspend fun deleteDownload(taskId: Int)

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Query("SELECT MAX(id) FROM download_tasks")
    suspend fun getMaxId(): Int?
}