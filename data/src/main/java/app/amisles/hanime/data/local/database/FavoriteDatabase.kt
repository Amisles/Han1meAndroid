package app.amisles.hanime.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.amisles.hanime.domain.model.DownloadEntity
import app.amisles.hanime.domain.model.FavoriteVideo
import app.amisles.hanime.domain.model.SearchHistoryEntity
import app.amisles.hanime.domain.model.WatchHistory

@Database(
    entities = [FavoriteVideo::class, WatchHistory::class, DownloadEntity::class, SearchHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class FavoriteDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS watch_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        videoUrl TEXT NOT NULL,
                        author TEXT NOT NULL,
                        duration TEXT NOT NULL,
                        watchedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS download_tasks (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        quality TEXT NOT NULL,
                        url TEXT NOT NULL,
                        totalBytes INTEGER NOT NULL,
                        downloadedBytes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        filePath TEXT NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS search_history (
                        query TEXT NOT NULL PRIMARY KEY,
                        searchedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE download_tasks ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // download_tasks 新增 videoId（去重依据）与 errorMessage（失败原因细分）
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE download_tasks ADD COLUMN videoId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE download_tasks ADD COLUMN errorMessage TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}