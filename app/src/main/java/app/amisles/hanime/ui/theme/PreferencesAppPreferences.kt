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
    override val activeLoginStateFlow = Preferences.activeLoginStateFlow
    override val playbackSpeedFlow = Preferences.playbackSpeedFlow
    override val preferredQualityFlow = Preferences.preferredQualityFlow
    override val autoPlayNextFlow = Preferences.autoPlayNextFlow
    override val loopPlaybackFlow = Preferences.loopPlaybackFlow
    override val searchLayoutModeFlow = Preferences.searchLayoutModeFlow
    override val pageLayoutModesFlow = Preferences.pageLayoutModesFlow
    override val relatedLayoutModeFlow = Preferences.relatedLayoutModeFlow
    override val comicReadModeFlow = Preferences.comicReadModeFlow
    override val comicReaderBlackFlow = Preferences.comicReaderBlackFlow
    override val longPressBoostSpeedFlow = Preferences.longPressBoostSpeedFlow
    override val doubleTapSeekFlow = Preferences.doubleTapSeekFlow
    override val autoQualityFlow = Preferences.autoQualityFlow
    override val autoRotateFullscreenFlow = Preferences.autoRotateFullscreenFlow
    override val baseUrlFlow = Preferences.baseUrlFlow
    override val privacyModeFlow = Preferences.privacyModeFlow
    override val subscriptionsHideWatchedFlow = Preferences.subscriptionsHideWatchedFlow

    override fun setPlaybackSpeed(speed: Float) = Preferences.setPlaybackSpeed(speed)
    override fun setPreferredQuality(resolution: String) = Preferences.setPreferredQuality(resolution)
    override fun setAutoPlayNext(enabled: Boolean) = Preferences.setAutoPlayNext(enabled)
    override fun setLoopPlayback(enabled: Boolean) = Preferences.setLoopPlayback(enabled)
    override fun setSearchLayoutMode(mode: SearchLayoutMode) = Preferences.setSearchLayoutMode(mode)
    override fun setPageLayoutMode(page: PageLayoutKey, mode: PageLayoutMode) =
        Preferences.setPageLayoutMode(page, mode)
    override fun clearPageLayoutMode(page: PageLayoutKey) = Preferences.clearPageLayoutMode(page)
    override fun setRelatedLayoutMode(mode: RelatedLayoutMode) = Preferences.setRelatedLayoutMode(mode)
    override fun setComicReadMode(mode: ComicReadMode) = Preferences.setComicReadMode(mode)
    override fun setComicReaderBlack(enabled: Boolean) = Preferences.setComicReaderBlack(enabled)
    override fun clearRelatedLayoutMode() = Preferences.clearRelatedLayoutMode()
    override fun setLongPressBoostSpeed(speed: Float) = Preferences.setLongPressBoostSpeed(speed)
    override fun setDoubleTapSeek(enabled: Boolean) = Preferences.setDoubleTapSeek(enabled)
    override fun setAutoQuality(enabled: Boolean) = Preferences.setAutoQuality(enabled)
    override fun setPrivacyMode(enabled: Boolean) = Preferences.setPrivacyMode(enabled)
    override fun setSubscriptionsHideWatched(enabled: Boolean) =
        Preferences.setSubscriptionsHideWatched(enabled)

    override val antiHotlinkReferer: String
        get() = VideoAntiHotlink.refererOf(Preferences.baseUrl)
}

private typealias SearchLayoutMode = app.amisles.hanime.domain.preferences.SearchLayoutMode
private typealias PageLayoutKey = app.amisles.hanime.domain.preferences.PageLayoutKey
private typealias PageLayoutMode = app.amisles.hanime.domain.preferences.PageLayoutMode
private typealias ComicReadMode = app.amisles.hanime.domain.model.ComicReadMode
private typealias RelatedLayoutMode = app.amisles.hanime.domain.preferences.RelatedLayoutMode
