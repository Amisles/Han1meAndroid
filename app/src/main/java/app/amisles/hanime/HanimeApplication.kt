package app.amisles.hanime

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.CrashLogger
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.app.work.SubscriptionReminderWorker
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.subscription.SubscriptionReminderScheduler
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.amisles.hanime.feature.detail.ExoPlayerFactory
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HanimeApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager 延迟初始化时由此提供工厂，使 Worker 能注入依赖。 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * 语言包装后的 Context。
     *
     * Application 级 Resources 也必须随所选语言变化：ViewModel 通过 `@ApplicationContext` 取得的
     * 字符串（错误提示 / Toast 等）走的是 Application 的 Resources，不包装会恒用系统语言，
     * 表现为「界面已切英文、提示仍是中文」。
     */
    @Volatile
    private var localizedContext: Context? = null

    /** 未被语言包装的原始 base context，语言变更后据此重新包装。 */
    @Volatile
    private var rawBaseContext: Context? = null

    /** 当前已应用的 Application 级语言，避免重复包装。 */
    @Volatile
    private var appliedLanguage: String? = null

    /**
     * 供 Activity.attachBaseContext 使用的语言快照。
     *
     * 该回调发生在 Activity 创建的最早期，此时读 Preferences.appLanguage 是读 StateFlow，
     * 正常情况下只是一次内存读取；但若 Preferences 尚未初始化（例如系统在极早期拉起 Activity），
     * 就会退化为一次「加密存储解密」，把主线程卡在 Activity 创建路径上。
     * 快照统一托管在 LocaleHelper：启动时预热，Preferences 写入语言时同步刷新。
     */
    fun currentLanguage(): String = LocaleHelper.languageSnapshot { persistedLanguage() }

    /**
     * 只记录原始 base context，供 [applyLanguage] 重新包装。
     *
     * **此处不能初始化 Preferences**：attachBaseContext 阶段 Application 尚未 attach 完成，
     * `context.applicationContext` 为 null、且传入的 base context 也不足以让
     * `EncryptedSharedPreferences.create` 正常工作。曾在此调用 init，导致加密存储创建失败 →
     * 触发「清空旧偏好后重建」→ 每次启动都会把用户配置抹成默认值。
     */
    override fun attachBaseContext(newBase: Context) {
        rawBaseContext = newBase
        super.attachBaseContext(newBase)
    }

    /**
     * 应用 Application 级语言。语言包装不通过 attachBaseContext 完成，而是由
     * [getResources] / [getAssets] 覆盖生效，因此可以在 Preferences 初始化之后调用。
     *
     * 初始调用来自 [onCreate]（进程启动），语言切换后由 MainActivity 重建时再次调用
     * （Application 的 attachBaseContext 每进程只执行一次）。
     */
    fun applyLanguage(lang: String) {
        LocaleHelper.rememberLanguage(lang)
        val base = rawBaseContext ?: return
        if (lang == appliedLanguage) return
        runCatching {
            localizedContext = LocaleHelper.wrapContext(base, lang)
            appliedLanguage = lang
        }.onFailure { e ->
            AppLogger.logError("APP", "刷新 Application 语言失败: ${e.message}", e)
        }
    }

    /** 优先返回语言包装后的 Resources；未包装时退回基类行为。 */
    override fun getResources(): Resources =
        localizedContext?.resources ?: super.getResources()

    override fun getAssets(): AssetManager =
        localizedContext?.assets ?: super.getAssets()

    private fun persistedLanguage(): String =
        runCatching { Preferences.appLanguage }.getOrDefault(Preferences.LANGUAGE_ZH_CN)

    /** 图片磁盘缓存上限。Coil 默认取可用空间的 2% 且没有绝对上限，
     *  在本项目这种缩略图量级下（首页分区 + 搜索无限滚动 + 批量下载整作者列表）
     *  会随使用时长无限增长，故显式给定容量。 */
    private val IMAGE_DISK_CACHE_BYTES = 256L * 1024 * 1024

    /** 图片内存缓存占可用堆的比例。列表快速滚动时主要靠它避免重复解码。 */
    private val IMAGE_MEMORY_CACHE_PERCENT = 0.15

    /**
     * 自定义 Coil 单例：为图片磁盘 / 内存缓存设定显式上限。
     *
     * 之前完全用默认配置 —— Coil 3 的磁盘缓存默认「可用空间的 2%」，没有绝对上限，
     * 在缩略图数量极大的场景下会持续吃满存储，且与 ExoPlayer 的 512MB 视频缓存叠加。
     * 这里统一收敛到 256MB，并按 15% 堆上限设置内存缓存。
     */
    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, IMAGE_MEMORY_CACHE_PERCENT)
                    .build()
            }
            // 不设置 crossfade：该版本 ImageLoader.Builder 未暴露此方法，Coil 3 默认不对
            // AsyncImage 施加全局淡入，与 v1.6.0「移除图片淡入」的效果一致。
            .build()

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        UiConfigStore.init(this)
        applyLanguage(persistedLanguage())
        AppLogger.init(this)
        // 崩溃记录依赖 AppLogger 已就绪（会同步追加一条错误日志），故紧随其后
        CrashLogger.init(this)
        ExoPlayerFactory.prewarmCache(this)
        syncSubscriptionReminder()
    }

    /**
     * 让 WorkManager 的调度状态与设置项保持一致。
     *
     * 放在 Application 而非设置页：用户可能在系统设置里清掉后台任务，
     * 每次启动重建一次才能保证「开着就真的在跑」。
     */
    private fun syncSubscriptionReminder() {
        runCatching {
            if (Preferences.subscriptionReminder) {
                SubscriptionReminderScheduler.schedule(this, SubscriptionReminderWorker::class.java)
            } else {
                SubscriptionReminderScheduler.cancel(this)
            }
        }
    }
}
