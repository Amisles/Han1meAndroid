package app.amisles.hanime.data.di

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.remote.NetworkDiagnostics
import app.amisles.hanime.domain.repository.AppearanceSettings
import app.amisles.hanime.domain.repository.DiagnosticsRepository
import app.amisles.hanime.domain.repository.DownloadSettings
import app.amisles.hanime.domain.repository.PlayerSettings
import app.amisles.hanime.domain.repository.ReadingSettings
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.repository.SiteSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 设置与会话仓储的绑定。
 *
 * 与 [RepositoryModule] 分开：Hilt 不允许同一个 @Module 里既放 abstract 绑定方法
 * （@Binds）又放带方法体的 @Provides。Preferences 是进程级 object，无法用 @Binds
 * （它需要一个实例），只能 @Provides 直接返回。
 *
 * 五个窄接口与总接口指向同一个 Preferences：调用方按需注入，注入面越窄，改设置时受影响的文件越少。
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = Preferences

    @Provides
    @Singleton
    fun providePlayerSettings(): PlayerSettings = Preferences

    @Provides
    @Singleton
    fun provideDownloadSettings(): DownloadSettings = Preferences

    @Provides
    @Singleton
    fun provideSiteSettings(): SiteSettings = Preferences

    @Provides
    @Singleton
    fun provideAppearanceSettings(): AppearanceSettings = Preferences

    @Provides
    @Singleton
    fun provideReadingSettings(): ReadingSettings = Preferences

    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository = Preferences

    @Provides
    @Singleton
    fun provideDiagnosticsRepository(): DiagnosticsRepository = NetworkDiagnostics()
}
