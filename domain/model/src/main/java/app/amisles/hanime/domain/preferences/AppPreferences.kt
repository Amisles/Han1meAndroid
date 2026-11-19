package app.amisles.hanime.domain.preferences

import app.amisles.hanime.domain.model.ComicReadMode
import app.amisles.hanime.domain.remote.VideoAntiHotlink
import kotlinx.coroutines.flow.StateFlow

/**
 * Compose 层读取的「界面相关偏好」最小集合。
 *
 * 放在 domain 而不是 core:ui：实现必须是数据层的设置存储，而 core:ui 不能反向依赖 :data。
 * 界面只认这个接口，换实现（新存储、测试替身）不必改任何 Composable。
 */
interface AppPreferences {

    val loginStateFlow: StateFlow<Boolean>
    val loginSupportedFlow: StateFlow<Boolean>
    val activeLoginStateFlow: StateFlow<Boolean>
    val playbackSpeedFlow: StateFlow<Float>
    val preferredQualityFlow: StateFlow<String>
    val autoPlayNextFlow: StateFlow<Boolean>
    val autoPlayRelatedFlow: StateFlow<Boolean>
    val loopPlaybackFlow: StateFlow<Boolean>
    val searchLayoutModeFlow: StateFlow<SearchLayoutMode?>
    val pageLayoutModesFlow: StateFlow<PageLayoutModes>
    val comicReadModeFlow: StateFlow<ComicReadMode?>
    val comicReaderBlackFlow: StateFlow<Boolean>
    val relatedLayoutModeFlow: StateFlow<RelatedLayoutMode?>
    val longPressBoostSpeedFlow: StateFlow<Float>
    val doubleTapSeekFlow: StateFlow<Boolean>
    val autoQualityFlow: StateFlow<Boolean>
    val autoRotateFullscreenFlow: StateFlow<Boolean>
    val orientationPinnedFlow: StateFlow<Boolean>
    val playerResizeModeFlow: StateFlow<PlayerResizeMode>
    val playerLayoutFlow: StateFlow<PlayerLayout>
    val baseUrlFlow: StateFlow<String>
    val privacyModeFlow: StateFlow<Boolean>
    val subscriptionsHideWatchedFlow: StateFlow<Boolean>

    fun setPlaybackSpeed(speed: Float)
    fun setPreferredQuality(resolution: String)
    fun setAutoPlayNext(enabled: Boolean)
    fun setLoopPlayback(enabled: Boolean)
    fun setSearchLayoutMode(mode: SearchLayoutMode)
    fun setPageLayoutMode(page: PageLayoutKey, mode: PageLayoutMode)
    fun clearPageLayoutMode(page: PageLayoutKey)
    fun setComicReadMode(mode: ComicReadMode)
    fun setComicReaderBlack(enabled: Boolean)
    fun setRelatedLayoutMode(mode: RelatedLayoutMode)
    fun clearRelatedLayoutMode()
    fun setLongPressBoostSpeed(speed: Float)
    fun setDoubleTapSeek(enabled: Boolean)
    fun setPrivacyMode(enabled: Boolean)
    fun setAutoQuality(enabled: Boolean)
    fun setOrientationPinned(pinned: Boolean)
    fun setPlayerResizeMode(mode: PlayerResizeMode)
    fun setPlayerLayout(layout: PlayerLayout)
    fun setSubscriptionsHideWatched(enabled: Boolean)

    /**
     * 当前站点应注入的防盗链 Referer（结尾带 /）。
     *
     * 必须是 getter：站点切换后旧域名会让 CDN 判为盗链。播放器与取帧预览都依赖它。
     */
    val antiHotlinkReferer: String
        get() = VideoAntiHotlink.refererOf(baseUrlFlow.value)

    /** 一次性提示的展示标记；界面只读它决定是否首次弹出。 */
    val hints: UiHintStore
}