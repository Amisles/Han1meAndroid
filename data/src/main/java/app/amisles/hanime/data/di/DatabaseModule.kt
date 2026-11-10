package app.amisles.hanime.data.di

import android.content.Context
import androidx.room.Room
import app.amisles.hanime.data.local.database.CloudPlaylistDao
import app.amisles.hanime.data.local.database.DownloadDao
import app.amisles.hanime.data.local.database.FavoriteDao
import app.amisles.hanime.data.local.database.ComicProgressDao
import app.amisles.hanime.data.local.database.FavoriteDatabase
import app.amisles.hanime.data.local.database.FavoriteGroupDao
import app.amisles.hanime.data.local.database.PlaylistMembershipDao
import app.amisles.hanime.data.local.database.PlaylistOutboxDao
import app.amisles.hanime.data.local.database.SearchHistoryDao
import app.amisles.hanime.data.local.database.WatchHistoryDao
import app.amisles.hanime.data.local.database.WatchLaterDao
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
                FavoriteDatabase.MIGRATION_5_6,
                FavoriteDatabase.MIGRATION_6_7,
            FavoriteDatabase.MIGRATION_7_8,
                FavoriteDatabase.MIGRATION_8_9,
                FavoriteDatabase.MIGRATION_9_10
            )
            // 全量迁移已覆盖 1→10，禁止改用 fallbackToDestructiveMigration：它会清空用户数据
            .build()
    }

    @Provides
    fun provideCloudPlaylistDao(db: FavoriteDatabase): CloudPlaylistDao = db.cloudPlaylistDao()

    @Provides
    fun providePlaylistMembershipDao(db: FavoriteDatabase): PlaylistMembershipDao =
        db.playlistMembershipDao()

    @Provides
    fun providePlaylistOutboxDao(db: FavoriteDatabase): PlaylistOutboxDao = db.playlistOutboxDao()

    @Provides
    fun provideFavoriteDao(db: FavoriteDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideFavoriteGroupDao(db: FavoriteDatabase): FavoriteGroupDao = db.favoriteGroupDao()

    @Provides
    fun provideWatchHistoryDao(db: FavoriteDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideDownloadDao(db: FavoriteDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideSearchHistoryDao(db: FavoriteDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    @Singleton
    fun provideComicProgressDao(db: FavoriteDatabase): ComicProgressDao = db.comicProgressDao()

    @Provides
    fun provideWatchLaterDao(db: FavoriteDatabase): WatchLaterDao = db.watchLaterDao()
}
