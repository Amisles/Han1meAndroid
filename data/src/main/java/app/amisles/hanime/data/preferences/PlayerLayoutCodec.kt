package app.amisles.hanime.data.preferences

import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerTapAction

/**
 * 播放器布局的键值对编码。
 *
 * 用带键名的 `key=value` 而不是定长字段列表：布局会被「数据备份」跨版本搬运，
 * 定长列表在新增/删除一项时会整份错位，键值对缺项直接落回默认值。
 */
internal object PlayerLayoutCodec {

    private const val VERSION = "1"
    private const val PAIR_SEPARATOR = ";"
    private const val KEY_SEPARATOR = '='

    fun encode(layout: PlayerLayout): String {
        val fields = listOf(
            "v" to VERSION,
            "showNavButtons" to layout.showNavButtons.flag(),
            "showRewindButton" to layout.showRewindButton.flag(),
            "showPlayButton" to layout.showPlayButton.flag(),
            "showForwardButton" to layout.showForwardButton.flag(),
            "showVolumeButton" to layout.showVolumeButton.flag(),
            "showSpeedButton" to layout.showSpeedButton.flag(),
            "showQualityButton" to layout.showQualityButton.flag(),
            "showLockButton" to layout.showLockButton.flag(),
            "showOrientationButton" to layout.showOrientationButton.flag(),
            "showFullscreenButton" to layout.showFullscreenButton.flag(),
            "showCenterPlayButton" to layout.showCenterPlayButton.flag(),
            "showTimeLabels" to layout.showTimeLabels.flag(),
            "showProgressBar" to layout.showProgressBar.flag(),
            "showMiniProgress" to layout.showMiniProgress.flag(),
            "showSeekPreview" to layout.showSeekPreview.flag(),
            "showSleepTimer" to layout.showSleepTimer.flag(),
            "showPipEntry" to layout.showPipEntry.flag(),
            "tapLeft" to layout.tapLeft.name,
            "tapCenter" to layout.tapCenter.name,
            "tapRight" to layout.tapRight.name,
            "sideZonePercent" to layout.sideZonePercent.toString()
        )
        return fields.joinToString(PAIR_SEPARATOR) { (key, value) -> "$key$KEY_SEPARATOR$value" }
    }

    fun decode(raw: String?): PlayerLayout {
        if (raw.isNullOrBlank()) return PlayerLayout.DEFAULT
        val pairs = raw.split(PAIR_SEPARATOR).mapNotNull { pair ->
            val index = pair.indexOf(KEY_SEPARATOR)
            if (index <= 0) null else pair.substring(0, index) to pair.substring(index + 1)
        }.toMap()
        if (pairs["v"] != VERSION) return PlayerLayout.DEFAULT
        val fallback = PlayerLayout.DEFAULT
        return PlayerLayout.sanitize(
            PlayerLayout(
                showNavButtons = pairs.flag("showNavButtons", fallback.showNavButtons),
                showRewindButton = pairs.flag("showRewindButton", fallback.showRewindButton),
                showPlayButton = pairs.flag("showPlayButton", fallback.showPlayButton),
                showForwardButton = pairs.flag("showForwardButton", fallback.showForwardButton),
                showVolumeButton = pairs.flag("showVolumeButton", fallback.showVolumeButton),
                showSpeedButton = pairs.flag("showSpeedButton", fallback.showSpeedButton),
                showQualityButton = pairs.flag("showQualityButton", fallback.showQualityButton),
                showLockButton = pairs.flag("showLockButton", fallback.showLockButton),
                showOrientationButton = pairs.flag("showOrientationButton", fallback.showOrientationButton),
                showFullscreenButton = pairs.flag("showFullscreenButton", fallback.showFullscreenButton),
                showCenterPlayButton = pairs.flag("showCenterPlayButton", fallback.showCenterPlayButton),
                showTimeLabels = pairs.flag("showTimeLabels", fallback.showTimeLabels),
                showProgressBar = pairs.flag("showProgressBar", fallback.showProgressBar),
                showMiniProgress = pairs.flag("showMiniProgress", fallback.showMiniProgress),
                showSeekPreview = pairs.flag("showSeekPreview", fallback.showSeekPreview),
                showSleepTimer = pairs.flag("showSleepTimer", fallback.showSleepTimer),
                showPipEntry = pairs.flag("showPipEntry", fallback.showPipEntry),
                tapLeft = PlayerTapAction.fromNameOrNull(pairs["tapLeft"]) ?: fallback.tapLeft,
                tapCenter = PlayerTapAction.fromNameOrNull(pairs["tapCenter"]) ?: fallback.tapCenter,
                tapRight = PlayerTapAction.fromNameOrNull(pairs["tapRight"]) ?: fallback.tapRight,
                sideZonePercent = pairs["sideZonePercent"]?.toIntOrNull() ?: fallback.sideZonePercent
            )
        )
    }

    private fun Boolean.flag(): String = if (this) "1" else "0"

    private fun Map<String, String>.flag(key: String, default: Boolean): Boolean =
        when (this[key]) {
            "1" -> true
            "0" -> false
            else -> default
        }
}
