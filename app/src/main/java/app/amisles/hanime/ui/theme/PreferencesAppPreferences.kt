package app.amisles.hanime.ui.theme

import app.amisles.hanime.core.ui.theme.AppPreferences
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.remote.VideoAntiHotlink

/**
 * 把 `:data` 的 Preferences 适配为 `:core:ui` 的 AppPreferences。
 *
 * 适配层放在 `:app`（唯一同时知道两层的地方），使 `:core:ui` 与 `:feature-*`
 * 只依赖接口。以后若要换掉 Preferences 的实现，只需改这里。
 */
object PreferencesAppPreferences : AppPreferences {
    override val loginStateFlow = Preferences.loginStateFlow
    override val loginSupportedFlow = Preferences.loginSupportedFlow
    override val playbackSpeedFlow = Preferences.playbackSpeedFlow
    override val preferredQualityFlow = Preferences.preferredQualityFlow
    override val autoPlayNextFlow = Preferences.autoPlayNextFlow
    override val loopPlaybackFlow = Preferences.loopPlaybackFlow
    override val searchLayoutModeFlow = Preferences.searchLayoutModeFlow
    override val longPressBoostSpeedFlow = Preferences.longPressBoostSpeedFlow
    override val doubleTapSeekFlow = Preferences.doubleTapSeekFlow
    override val autoQualityFlow = Preferences.autoQualityFlow

    override fun setPlaybackSpeed(speed: Float) = Preferences.setPlaybackSpeed(speed)
    override fun setPreferredQuality(resolution: String) = Preferences.setPreferredQuality(resolution)
    override fun setAutoPlayNext(enabled: Boolean) = Preferences.setAutoPlayNext(enabled)
    override fun setLoopPlayback(enabled: Boolean) = Preferences.setLoopPlayback(enabled)
    override fun setSearchLayoutMode(mode: SearchLayoutMode) = Preferences.setSearchLayoutMode(mode)
    override fun setLongPressBoostSpeed(speed: Float) = Preferences.setLongPressBoostSpeed(speed)
    override fun setDoubleTapSeek(enabled: Boolean) = Preferences.setDoubleTapSeek(enabled)
    override fun setAutoQuality(enabled: Boolean) = Preferences.setAutoQuality(enabled)

    override val antiHotlinkReferer: String
        get() = VideoAntiHotlink.refererOf(Preferences.baseUrl)
}

private typealias SearchLayoutMode = app.amisles.hanime.domain.preferences.SearchLayoutMode
