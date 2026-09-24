package app.amisles.hanime.core.common.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * 应用语言管理工具。
 * 无论用户选择什么语言（含 zh-CN）都必须显式设置 Locale，以覆盖系统默认语言，保证选中正确的 values-* 目录。
 */
object LocaleHelper {

    /** Application 与 Activity 的 attachBaseContext 中都必须调用。 */
    fun wrapContext(context: Context, lang: String): Context {
        val safeLang = lang.ifBlank { LANGUAGE_ZH_CN }
        val locale = langToLocale(safeLang)

        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        val updated = context.createConfigurationContext(config)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            updated.resources.updateConfiguration(config, updated.resources.displayMetrics)
        }
        return updated
    }

    /** 供 Settings UI 使用 */
    fun getLanguageDisplayName(lang: String): String {
        return when (lang) {
            LANGUAGE_ZH_CN -> "简体中文"
            LANGUAGE_ZH_TW -> "繁體中文"
            LANGUAGE_EN -> "English"
            LANGUAGE_JA -> "日本語"
            else -> runCatching {
                Locale.forLanguageTag(lang.replace('_', '-')).getDisplayName(Locale.SIMPLIFIED_CHINESE)
            }.getOrDefault(lang)
        }
    }

    private fun langToLocale(lang: String): Locale {
        return when (lang) {
            LANGUAGE_ZH_TW -> Locale.TAIWAN
            LANGUAGE_EN -> Locale.ENGLISH
            LANGUAGE_JA -> Locale.JAPANESE
            else -> Locale.SIMPLIFIED_CHINESE // 默认兜底
        }
    }

    // 与 Preferences 保持一致的常量，避免循环依赖
    private const val LANGUAGE_ZH_CN = "zh-CN"
    private const val LANGUAGE_ZH_TW = "zh-TW"
    private const val LANGUAGE_EN = "en"
    private const val LANGUAGE_JA = "ja"
}
