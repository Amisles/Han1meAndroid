package app.amisles.hanime.data.di

import app.amisles.hanime.data.backup.BackupManager
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.data.icon.LauncherIconRepository
import app.amisles.hanime.data.repository.AccountRepositoryImpl
import app.amisles.hanime.data.repository.ComicRepositoryImpl
import app.amisles.hanime.data.repository.ContentRepositoryImpl
import app.amisles.hanime.data.repository.LibraryRepositoryImpl
import app.amisles.hanime.data.stats.StatsManager
import app.amisles.hanime.data.update.GitHubUpdateRepository
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.AppIconRepository
import app.amisles.hanime.domain.repository.BackupRepository
import app.amisles.hanime.domain.repository.ComicRepository
import app.amisles.hanime.domain.repository.ContentRepository
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.domain.repository.LibraryRepository
import app.amisles.hanime.domain.repository.SessionRepository
import app.amisles.hanime.domain.repository.StatsRepository
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.domain.repository.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓储接口 → 实现的绑定。
 *
 * 上层（feature-*）原则上依赖 `:domain:model` 的接口，替换实现不需要改动上层代码；
 * 但仍有少数具体类型被直接引用（Preferences、NetworkService、CloudflareChallenge、
 * BackupManager 等），这些调用点不受本模块的接口绑定约束。
 *
 * NetworkDiagnostics 不含 @Inject 构造且需要手动 new，其绑定放在 PreferencesModule
 * 以 @Provides 提供，不在本模块重复声明以免 Dagger 重复绑定报错。
 *
 * [DownloadManager] 既是接口实现，也被 app 层的 DownloadService 直接注入具体类型；
 * 各 UI 的 ViewModel 注入的是下载仓储接口。这里同时提供接口绑定，
 * 供只需要接口能力的代码使用。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: ContentRepositoryImpl): ContentRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindComicRepository(impl: ComicRepositoryImpl): ComicRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadManager): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(impl: GitHubUpdateRepository): UpdateRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupManager): BackupRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsManager): StatsRepository

    @Binds
    @Singleton
    abstract fun bindAppIconRepository(impl: LauncherIconRepository): AppIconRepository

}
