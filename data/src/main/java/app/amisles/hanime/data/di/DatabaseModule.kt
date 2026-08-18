package app.amisles.hanime.data.di

import android.content.Context
import androidx.room.Room
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.local.database.FavoriteDao
import app.amisles.hanime.data.local.database.FavoriteDatabase
import app.amisles.hanime.data.local.database.SearchHistoryDao
import app.amisles.hanime.data.local.database.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFavoriteDatabase(@ApplicationContext context: Context): FavoriteDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            FavoriteDatabase::class.java,
            "favorite_database"
        )
            .addMigrations(
                FavoriteDatabase.MIGRATION_1_2,
                FavoriteDatabase.MIGRATION_2_3,
                FavoriteDatabase.MIGRATION_3_4,
                FavoriteDatabase.MIGRATION_4_5,
                FavoriteDatabase.MIGRATION_5_6
            )
            // 安全兜底：未来若新增 schema 变更但漏写对应 Migration，
            // 旧版本升级时采用破坏性重建而非直接崩溃（同时修复 H3 隐患）
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideFavoriteDao(db: FavoriteDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchHistoryDao(db: FavoriteDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideDownloadDao(db: FavoriteDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideSearchHistoryDao(db: FavoriteDatabase): SearchHistoryDao = db.searchHistoryDao()
}
