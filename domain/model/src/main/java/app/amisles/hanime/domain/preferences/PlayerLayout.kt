package app.amisles.hanime.domain.preferences

enum class PlayerTapAction {
    REWIND,
    PLAY_PAUSE,
    FORWARD,
    NONE;

    companion object {
        fun fromNameOrNull(name: String?): PlayerTapAction? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

/**
 * 播放器布局的用户自定义项。
 *
 * 只决定控件是否绘制、点击分区如何响应，不参与播放逻辑：任何一项关掉都不会让视频停下，
 * 也不会阻止用户通过「更多」菜单重新打开编辑器。
 *
 * 默认值即内置布局；[DEFAULT] 用于「恢复默认」以及旧版本没有存过这份配置时的回落。
 *
 * 「更多」按钮不在可隐藏范围内：它同时是布局编辑器的唯一入口，能被藏起来的编辑器等于不自救。
 */
data class PlayerLayout(
    val showNavButtons: Boolean = true,
    val showRewindButton: Boolean = true,
    val showPlayButton: Boolean = true,
    val showForwardButton: Boolean = true,
    val showVolumeButton: Boolean = true,
    val showSpeedButton: Boolean = true,
    val showQualityButton: Boolean = true,
    val showLockButton: Boolean = true,
    val showOrientationButton: Boolean = true,
    val showFullscreenButton: Boolean = true,
    val showCenterPlayButton: Boolean = true,
    val showTimeLabels: Boolean = true,
    val showProgressBar: Boolean = true,
    val showMiniProgress: Boolean = true,
    val showSeekPreview: Boolean = true,
    val showResetZoom: Boolean = true,
    val showSleepTimer: Boolean = true,
    val showPipEntry: Boolean = true,
    val tapLeft: PlayerTapAction = PlayerTapAction.REWIND,
    val tapCenter: PlayerTapAction = PlayerTapAction.PLAY_PAUSE,
    val tapRight: PlayerTapAction = PlayerTapAction.FORWARD,
    val sideZonePercent: Int = DEFAULT_SIDE_ZONE_PERCENT
) {
    companion object {
        const val MIN_SIDE_ZONE_PERCENT = 15
        const val MAX_SIDE_ZONE_PERCENT = 45
        const val DEFAULT_SIDE_ZONE_PERCENT = 33

        val DEFAULT = PlayerLayout()

        fun sanitize(layout: PlayerLayout): PlayerLayout = layout.copy(
            sideZonePercent = layout.sideZonePercent
                .coerceIn(MIN_SIDE_ZONE_PERCENT, MAX_SIDE_ZONE_PERCENT)
        )
    }
}
