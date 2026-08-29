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
            // 不再使用 fallbackToDestructiveMigration
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
