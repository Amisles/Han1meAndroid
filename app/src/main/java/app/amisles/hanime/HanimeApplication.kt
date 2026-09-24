package app.amisles.hanime

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.common.util.LocaleHelper
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

    override fun attachBaseContext(newBase: Context) {
        rawBaseContext = newBase
        // 必须在此处初始化 Preferences：只有读到持久化语言才能正确包装 Resources。
        // 此时 applicationContext 仍为 null，Preferences 内部已按传入 context 兜底。
        // init 幂等，onCreate 中的调用为兜底。
        runCatching { Preferences.init(newBase) }
        val lang = persistedLanguage()
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, lang))
        appliedLanguage = lang
    }

    /**
     * 刷新 Application 级 Resources 的语言。
     *
     * Application 的 attachBaseContext 每个进程只执行一次，因此设置页切换语言后（仅重建 Activity）
     * 需要显式刷新，否则 ViewModel 层文案要等到进程重启才会跟随。
     */
    fun applyLanguage(lang: String) {
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
        // Preferences 已在 attachBaseContext 初始化，此处仅作幂等兜底
        Preferences.init(this)
        AppLogger.init(this)
        ExoPlayerFactory.prewarmCache(this)
    }
}
