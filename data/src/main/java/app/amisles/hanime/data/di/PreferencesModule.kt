package app.amisles.hanime.data.di

import app.amisles.hanime.data.preferences.AppPreferencesFacade
import app.amisles.hanime.data.preferences.SessionStore
import app.amisles.hanime.data.preferences.SettingsStore
import app.amisles.hanime.data.remote.NetworkDiagnostics
import app.amisles.hanime.domain.preferences.AppPreferences
import app.amisles.hanime.domain.preferences.UiHintStore
import app.amisles.hanime.domain.repository.AppLockSettings
import app.amisles.hanime.domain.repository.AppearanceSettings
import app.amisles.hanime.domain.repository.CrashSettings
import app.amisles.hanime.domain.repository.DiagnosticsRepository
import app.amisles.hanime.domain.repository.DownloadSettings
import app.amisles.hanime.domain.repository.PersistenceHealth
import app.amisles.hanime.domain.repository.PlaylistSettings
import app.amisles.hanime.domain.repository.PlayerSettings
import app.amisles.hanime.domain.repository.ReadingSettings
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.repository.SiteSettings
import app.amisles.hanime.domain.repository.UpdateSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 设置、会话与提示的接口绑定。
 *
 * [SettingsStore] 只承载非敏感设置，[SessionStore] 只承载凭据；[AppPreferencesFacade] 仅把
 * 两者组合成 Compose 需要的读模型。调用方按需注入窄接口，不再接触具体存储实现。
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(store: SettingsStore): SettingsRepository = store

    @Provides
    @Singleton
    fun providePlayerSettings(store: SettingsStore): PlayerSettings = store

    @Provides
    @Singleton
    fun provideDownloadSettings(store: SettingsStore): DownloadSettings = store

    @Provides
    @Singleton
    fun provideSiteSettings(store: SettingsStore): SiteSettings = store

    @Provides
    @Singleton
    fun provideAppearanceSettings(store: SettingsStore): AppearanceSettings = store

    @Provides
    @Singleton
    fun provideReadingSettings(store: SettingsStore): ReadingSettings = store

    @Provides
    @Singleton
    fun provideUpdateSettings(store: SettingsStore): UpdateSettings = store

    @Provides
    @Singleton
    fun providePlaylistSettings(store: SettingsStore): PlaylistSettings = store

    @Provides
    @Singleton
    fun provideCrashSettings(store: SettingsStore): CrashSettings = store

    @Provides
    @Singleton
    fun providePersistenceHealth(store: SettingsStore): PersistenceHealth = store

    @Provides
    @Singleton
    fun provideAppLockSettings(store: SettingsStore): AppLockSettings = store

    @Provides
    @Singleton
    fun provideSessionRepository(store: SessionStore): SessionRepository = store

    @Provides
    @Singleton
    fun provideAppPreferences(facade: AppPreferencesFacade): AppPreferences = facade

    @Provides
    @Singleton
    fun provideUiHintStore(store: SettingsStore): UiHintStore = store

    @Provides
    @Singleton
    fun provideDiagnosticsRepository(impl: NetworkDiagnostics): DiagnosticsRepository = impl
}
