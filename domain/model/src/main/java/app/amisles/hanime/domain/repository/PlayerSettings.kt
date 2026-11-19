package app.amisles.hanime.domain.repository

import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import kotlinx.coroutines.flow.StateFlow

/** 播放器相关设置：倍速、画质、连播、手势、画面比例与控件布局。 */
interface PlayerSettings {
    val playbackSpeed: Float
    val playbackSpeedFlow: StateFlow<Float>
    fun setPlaybackSpeed(speed: Float)

    val preferredQuality: String
    val preferredQualityFlow: StateFlow<String>
    fun setPreferredQuality(resolution: String)

    val autoPlayNext: Boolean
    val autoPlayNextFlow: StateFlow<Boolean>
    fun setAutoPlayNext(enabled: Boolean)

    /** 无播放列表时是否继续播放相关推荐；默认关闭，否则「连播」会跳到毫不相关的视频。 */
    val autoPlayRelated: Boolean
    val autoPlayRelatedFlow: StateFlow<Boolean>
    fun setAutoPlayRelated(enabled: Boolean)

    val loopPlayback: Boolean
    val loopPlaybackFlow: StateFlow<Boolean>
    fun setLoopPlayback(enabled: Boolean)

    /** 长按临时加速的目标倍速（1.5–3.0）。只影响按住期间，不写回 [playbackSpeed]。 */
    val longPressBoostSpeed: Float
    val longPressBoostSpeedFlow: StateFlow<Float>
    fun setLongPressBoostSpeed(speed: Float)

    /** 双击播放器左右区域快退 / 快进。 */
    val doubleTapSeek: Boolean
    val doubleTapSeekFlow: StateFlow<Boolean>
    fun setDoubleTapSeek(enabled: Boolean)

    /** 卡顿时自动降档、稳定后自动回升。关闭后手动选择的画质不会被自动切换覆盖。 */
    val autoQuality: Boolean
    val autoQualityFlow: StateFlow<Boolean>
    fun setAutoQuality(enabled: Boolean)

    /** 旋转屏幕时自动进入 / 退出全屏播放；关闭后旋转会让整个页面跟随转屏，进出全屏改用播放器按钮。 */
    val autoRotateFullscreen: Boolean
    val autoRotateFullscreenFlow: StateFlow<Boolean>
    fun setAutoRotateFullscreen(enabled: Boolean)

    /**
     * 全屏时是否钉住屏幕方向。
     *
     * 落盘而不只留在播放器实例内：切集与换视频都会重建播放器，若只在实例内保留，
     * 用户每看一集都要重新点一次「锁定屏幕方向」。
     */
    val orientationPinned: Boolean
    val orientationPinnedFlow: StateFlow<Boolean>
    fun setOrientationPinned(pinned: Boolean)

    /** 播放器画面比例；裁切与拉伸都会改变画面形状，默认适应。 */
    val playerResizeMode: PlayerResizeMode
    val playerResizeModeFlow: StateFlow<PlayerResizeMode>
    fun setPlayerResizeMode(mode: PlayerResizeMode)

    /** 播放器布局自定义：各控件显隐、点击分区动作与分区宽度。 */
    val playerLayout: PlayerLayout
    val playerLayoutFlow: StateFlow<PlayerLayout>
    fun setPlayerLayout(layout: PlayerLayout)

    /** 视频详情页常驻缓存的条目上限；超出后按最久未被打开的条目淘汰。 */
    val videoDetailCacheLimit: Int
    val videoDetailCacheLimitFlow: StateFlow<Int>
    fun setVideoDetailCacheLimit(limit: Int)
}
