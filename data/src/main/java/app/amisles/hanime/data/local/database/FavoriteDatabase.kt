package app.amisles.hanime.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.amisles.hanime.data.local.entity.ComicProgressEntity
import app.amisles.hanime.data.local.entity.DownloadEntity
import app.amisles.hanime.data.local.entity.FavoriteGroupEntity
import app.amisles.hanime.data.local.entity.FavoriteVideoEntity
import app.amisles.hanime.data.local.entity.SearchHistoryEntity
import app.amisles.hanime.data.local.entity.WatchHistoryEntity

@Database(
    entities = [FavoriteVideoEntity::class, WatchHistoryEntity::class, DownloadEntity::class, SearchHistoryEntity::class, FavoriteGroupEntity::class, ComicProgressEntity::class],
    version = 8,
    exportSchema = true
)
abstract class FavoriteDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteGroupDao(): FavoriteGroupDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun comicProgressDao(): ComicProgressDao

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

        // 收藏分组与自定义标签：favorites 增加 group_id / tags，另建 favorite_groups 表
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN group_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE favorites ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS favorite_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        // 漫画阅读进度表：一本漫画一行，记录最后停留页
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS comic_progress (
                        comicId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        coverUrl TEXT NOT NULL,
                        pageCount INTEGER NOT NULL,
                        last_page INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        // 观看历史（watch_history）新增播放进度字段：上次播放位置与总时长
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE watch_history ADD COLUMN playback_position INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE watch_history ADD COLUMN playback_duration INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}