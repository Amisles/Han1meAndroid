package app.amisles.hanime.di

import app.amisles.hanime.backup.UiConfigBackupAdapter
import app.amisles.hanime.domain.preferences.UiConfigBackup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupUiConfigModule {

    @Binds
    @Singleton
    abstract fun bindUiConfigBackup(adapter: UiConfigBackupAdapter): UiConfigBackup
}
