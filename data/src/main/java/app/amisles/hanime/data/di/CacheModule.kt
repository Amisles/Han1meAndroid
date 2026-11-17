package app.amisles.hanime.data.di

import android.content.Context
import app.amisles.hanime.data.cache.VideoDetailStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideVideoDetailStore(@ApplicationContext context: Context): VideoDetailStore =
        VideoDetailStore(File(context.filesDir, VideoDetailStore.DIR_NAME))
}
