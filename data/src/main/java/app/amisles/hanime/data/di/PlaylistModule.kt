package app.amisles.hanime.data.di

import app.amisles.hanime.data.playlist.NetworkPlaylistRemote
import app.amisles.hanime.data.playlist.PlaylistClock
import app.amisles.hanime.data.playlist.PlaylistLocalStore
import app.amisles.hanime.data.playlist.PlaylistRemote
import app.amisles.hanime.data.playlist.PlaylistSyncRepositoryImpl
import app.amisles.hanime.data.playlist.PlaylistTokenStore
import app.amisles.hanime.data.playlist.PreferencesPlaylistTokenStore
import app.amisles.hanime.data.playlist.RoomPlaylistLocalStore
import app.amisles.hanime.domain.repository.PlaylistSyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaylistModule {

    @Binds
    @Singleton
    abstract fun bindPlaylistLocalStore(impl: RoomPlaylistLocalStore): PlaylistLocalStore

    @Binds
    @Singleton
    abstract fun bindPlaylistRemote(impl: NetworkPlaylistRemote): PlaylistRemote

    @Binds
    @Singleton
    abstract fun bindPlaylistSyncRepository(impl: PlaylistSyncRepositoryImpl): PlaylistSyncRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistTokenStore(impl: PreferencesPlaylistTokenStore): PlaylistTokenStore

    companion object {
        @Provides
        fun providePlaylistClock(): PlaylistClock = PlaylistClock { System.currentTimeMillis() }
    }
}