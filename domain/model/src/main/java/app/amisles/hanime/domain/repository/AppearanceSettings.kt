package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.preferences.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** 外观设置：语言、主题、动态配色与隐私遮罩。 */
interface AppearanceSettings {
    val appLanguage: String
    val appLanguageFlow: StateFlow<String>
    fun setAppLanguage(lang: String)

    val themeMode: ThemeMode
    val themeModeFlow: StateFlow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)

    val dynamicColor: Boolean
    val dynamicColorFlow: StateFlow<Boolean>
    fun setDynamicColor(enabled: Boolean)

    /** 隐私模式：遮罩内容图片；播放器照常可用，息屏时自动暂停并遮住播放器。 */
    val privacyMode: Boolean
    val privacyModeFlow: StateFlow<Boolean>
    fun setPrivacyMode(enabled: Boolean)
}
