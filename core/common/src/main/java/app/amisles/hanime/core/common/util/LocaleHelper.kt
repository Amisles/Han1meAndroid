package app.amisles.hanime.core.common.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用语言管理工具
 */
object LocaleHelper {

    /**
     * 用指定的语言包装 Context
     * 在 Activity.attachBaseContext 中调用
     * @param lang 语言代码（如 "zh-CN", "zh-TW", "en", "ja"）
     */
    fun wrapContext(context: Context, lang: String): Context {
        if (lang.isEmpty() || lang == "zh-CN") {
            return context
        }
        val locale = langToLocale(lang) ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * 获取语言显示名称
     */
    fun getLanguageDisplayName(lang: String): String {
        return when (lang) {
            "zh-CN" -> "简体中文"
            "zh-TW" -> "繁體中文"
            "en" -> "English"
            "ja" -> "日本語"
            else -> "简体中文"
        }
    }

    private fun langToLocale(lang: String): Locale? {
        return when (lang) {
            "zh-CN" -> null
            "zh-TW" -> Locale.TAIWAN
            "en" -> Locale.ENGLISH
            "ja" -> Locale.JAPANESE
            else -> null
        }
    }
}
