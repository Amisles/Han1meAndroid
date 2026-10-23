package app.amisles.hanime.data.di

import app.amisles.hanime.data.backup.BackupManager
import app.amisles.hanime.data.download.DownloadManager
import app.amisles.hanime.data.repository.HanimeRepository
import app.amisles.hanime.data.stats.StatsManager
import app.amisles.hanime.data.update.GitHubUpdateRepository
import app.amisles.hanime.domain.repository.AccountRepository
import app.amisles.hanime.domain.repository.BackupRepository
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
 * 上层（feature-*）只依赖 `:domain:model` 中的接口，不再 import 任何 `:data` 类型，
 * 因此替换实现（例如加缓存层、或在测试中注入 fake）不需要改动上层代码。
 *
 * NetworkDiagnostics 不含 @Inject 构造且需要手动 new，其绑定放在 PreferencesModule
 * 以 @Provides 提供，不在本模块重复声明以免 Dagger 重复绑定报错。
 *
 * 注意 [DownloadManager] 同时是"实现"与"被其它模块直接引用的具体类"——
 * 详情页 / 批量下载页目前仍注入具体类型（它暴露了 tasks Flow 等接口未覆盖的成员）。
 * 这里同时提供接口绑定，供只需要接口能力的新代码使用。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: HanimeRepository): ContentRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: HanimeRepository): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: HanimeRepository): AccountRepository

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

}
