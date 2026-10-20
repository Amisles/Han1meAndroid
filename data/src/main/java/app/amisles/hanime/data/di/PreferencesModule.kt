package app.amisles.hanime.data.di

import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.remote.NetworkDiagnostics
import app.amisles.hanime.domain.repository.DiagnosticsRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.SettingsRepository
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
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository = Preferences

    @Provides
    @Singleton
    fun provideSessionRepository(): SessionRepository = Preferences

    @Provides
    @Singleton
    fun provideDiagnosticsRepository(): DiagnosticsRepository = NetworkDiagnostics()
}
