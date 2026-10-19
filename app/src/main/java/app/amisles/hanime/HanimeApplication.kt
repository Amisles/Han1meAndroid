package app.amisles.hanime

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.LocaleHelper
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.feature.detail.ExoPlayerFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HanimeApplication : Application() {

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
     * Activity.attachBaseContext 阶段要读取的语言缓存。
     *
     * 该回调发生在 Activity 创建的最早期，此时读 Preferences.appLanguage 是读 StateFlow，
     * 正常情况下只是一次内存读取；但若 Preferences 尚未初始化（例如系统在极早期拉起 Activity），
     * 就会退化为一次「加密存储解密」，把主线程卡在 Activity 创建路径上。
     * 这里由 onCreate 在主线程外预热一次，之后读取只走内存。
     */
    @Volatile
    private var cachedLanguage: String? = null

    /** 供 Activity.attachBaseContext 使用的语言快照；未预热时回退到当前值并缓存。 */
    fun currentLanguage(): String {
        cachedLanguage?.let { return it }
        val lang = runCatching { Preferences.appLanguage }
            .getOrDefault(Preferences.LANGUAGE_ZH_CN)
        cachedLanguage = lang
        return lang
    }

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
        cachedLanguage = lang
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

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        // 预热语言快照：Activity.attachBaseContext 会读它，避免在创建关键路径上触发解密
        cachedLanguage = persistedLanguage()
        UiConfigStore.init(this)
        applyLanguage(persistedLanguage())
        AppLogger.init(this)
        ExoPlayerFactory.prewarmCache(this)
    }
}
