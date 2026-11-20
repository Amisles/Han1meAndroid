package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.model.ComicReadMode
import app.amisles.hanime.domain.preferences.AppPreferences
import app.amisles.hanime.domain.preferences.PageLayoutKey
import app.amisles.hanime.domain.preferences.PageLayoutMode
import app.amisles.hanime.domain.preferences.PageLayoutModes
import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import app.amisles.hanime.domain.preferences.SearchLayoutMode
import app.amisles.hanime.domain.preferences.UiHintStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/** Compose 层读取界面偏好的组合入口：会话流来自 [SessionStore]，其余设置来自 [SettingsStore]。 */
@Singleton
class AppPreferencesFacade @Inject constructor(
    private val settings: SettingsStore,
    private val session: SessionStore
) : AppPreferences {

    override val loginStateFlow: StateFlow<Boolean> = session.loginStateFlow
    override val loginSupportedFlow: StateFlow<Boolean> = settings.loginSupportedFlow
    override val activeLoginStateFlow: StateFlow<Boolean> = session.activeLoginStateFlow
    override val playbackSpeedFlow: StateFlow<Float> = settings.playbackSpeedFlow
    override val preferredQualityFlow: StateFlow<String> = settings.preferredQualityFlow
    override val autoPlayNextFlow: StateFlow<Boolean> = settings.autoPlayNextFlow
    override val autoPlayRelatedFlow: StateFlow<Boolean> = settings.autoPlayRelatedFlow
    override val loopPlaybackFlow: StateFlow<Boolean> = settings.loopPlaybackFlow
    override val searchLayoutModeFlow: StateFlow<SearchLayoutMode?> = settings.searchLayoutModeFlow
    override val pageLayoutModesFlow: StateFlow<PageLayoutModes> = settings.pageLayoutModesFlow
    override val comicReadModeFlow: StateFlow<ComicReadMode?> = settings.comicReadModeFlow
    override val comicReaderBlackFlow: StateFlow<Boolean> = settings.comicReaderBlackFlow
    override val relatedLayoutModeFlow: StateFlow<RelatedLayoutMode?> = settings.relatedLayoutModeFlow
    override val longPressBoostSpeedFlow: StateFlow<Float> = settings.longPressBoostSpeedFlow
    override val doubleTapSeekFlow: StateFlow<Boolean> = settings.doubleTapSeekFlow
    override val autoQualityFlow: StateFlow<Boolean> = settings.autoQualityFlow
    override val autoRotateFullscreenFlow: StateFlow<Boolean> = settings.autoRotateFullscreenFlow
    override val orientationPinnedFlow: StateFlow<Boolean> = settings.orientationPinnedFlow
    override val playerResizeModeFlow: StateFlow<PlayerResizeMode> = settings.playerResizeModeFlow
    override val playerLayoutFlow: StateFlow<PlayerLayout> = settings.playerLayoutFlow
    override val baseUrlFlow: StateFlow<String> = settings.baseUrlFlow
    override val privacyModeFlow: StateFlow<Boolean> = settings.privacyModeFlow
    override val subscriptionsHideWatchedFlow: StateFlow<Boolean> = settings.subscriptionsHideWatchedFlow
    override val hints: UiHintStore = settings

    override fun setPlaybackSpeed(speed: Float) = settings.setPlaybackSpeed(speed)
    override fun setPreferredQuality(resolution: String) = settings.setPreferredQuality(resolution)
    override fun setAutoPlayNext(enabled: Boolean) = settings.setAutoPlayNext(enabled)
    override fun setLoopPlayback(enabled: Boolean) = settings.setLoopPlayback(enabled)
    override fun setSearchLayoutMode(mode: SearchLayoutMode) = settings.setSearchLayoutMode(mode)
    override fun setPageLayoutMode(page: PageLayoutKey, mode: PageLayoutMode) =
        settings.setPageLayoutMode(page, mode)
    override fun clearPageLayoutMode(page: PageLayoutKey) = settings.clearPageLayoutMode(page)
    override fun setComicReadMode(mode: ComicReadMode) = settings.setComicReadMode(mode)
    override fun setComicReaderBlack(enabled: Boolean) = settings.setComicReaderBlack(enabled)
    override fun setRelatedLayoutMode(mode: RelatedLayoutMode) = settings.setRelatedLayoutMode(mode)
    override fun clearRelatedLayoutMode() = settings.clearRelatedLayoutMode()
    override fun setLongPressBoostSpeed(speed: Float) = settings.setLongPressBoostSpeed(speed)
    override fun setDoubleTapSeek(enabled: Boolean) = settings.setDoubleTapSeek(enabled)
    override fun setPrivacyMode(enabled: Boolean) = settings.setPrivacyMode(enabled)
    override fun setAutoQuality(enabled: Boolean) = settings.setAutoQuality(enabled)
    override fun setOrientationPinned(pinned: Boolean) = settings.setOrientationPinned(pinned)
    override fun setPlayerResizeMode(mode: PlayerResizeMode) = settings.setPlayerResizeMode(mode)
    override fun setPlayerLayout(layout: PlayerLayout) = settings.setPlayerLayout(layout)
    override fun setSubscriptionsHideWatched(enabled: Boolean) =
        settings.setSubscriptionsHideWatched(enabled)
}
