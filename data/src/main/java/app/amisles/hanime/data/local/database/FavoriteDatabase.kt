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
    version = 4,
    exportSchema = false
)
abstract class FavoriteDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS search_history (
                        query TEXT NOT NULL PRIMARY KEY,
                        searchedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE download_tasks ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}