package app.amisles.hanime.feature.detail.components

import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerTapAction

internal enum class PlayerControlId {
    NAV_BUTTONS,
    CENTER_PLAY,
    REWIND,
    PLAY,
    FORWARD,
    PROGRESS,
    VOLUME,
    SPEED,
    QUALITY,
    LOCK,
    ORIENTATION,
    FULLSCREEN,
    RESET_ZOOM,
    TIME_LABELS,
    MINI_PROGRESS,
    SEEK_PREVIEW,
    SLEEP_TIMER,
    PIP,
    RELATED_LIST
}

internal fun PlayerLayout.isShown(id: PlayerControlId): Boolean = when (id) {
    PlayerControlId.NAV_BUTTONS -> showNavButtons
    PlayerControlId.CENTER_PLAY -> showCenterPlayButton
    PlayerControlId.REWIND -> showRewindButton
    PlayerControlId.PLAY -> showPlayButton
    PlayerControlId.FORWARD -> showForwardButton
    PlayerControlId.PROGRESS -> showProgressBar
    PlayerControlId.VOLUME -> showVolumeButton
    PlayerControlId.SPEED -> showSpeedButton
    PlayerControlId.QUALITY -> showQualityButton
    PlayerControlId.LOCK -> showLockButton
    PlayerControlId.ORIENTATION -> showOrientationButton
    PlayerControlId.FULLSCREEN -> showFullscreenButton
    PlayerControlId.RESET_ZOOM -> showResetZoom
    PlayerControlId.TIME_LABELS -> showTimeLabels
    PlayerControlId.MINI_PROGRESS -> showMiniProgress
    PlayerControlId.SEEK_PREVIEW -> showSeekPreview
    PlayerControlId.SLEEP_TIMER -> showSleepTimer
    PlayerControlId.PIP -> showPipEntry
    PlayerControlId.RELATED_LIST -> showRelatedList
}

internal fun PlayerLayout.withToggled(id: PlayerControlId): PlayerLayout = when (id) {
    PlayerControlId.NAV_BUTTONS -> copy(showNavButtons = !showNavButtons)
    PlayerControlId.CENTER_PLAY -> copy(showCenterPlayButton = !showCenterPlayButton)
    PlayerControlId.REWIND -> copy(showRewindButton = !showRewindButton)
    PlayerControlId.PLAY -> copy(showPlayButton = !showPlayButton)
    PlayerControlId.FORWARD -> copy(showForwardButton = !showForwardButton)
    PlayerControlId.PROGRESS -> copy(showProgressBar = !showProgressBar)
    PlayerControlId.VOLUME -> copy(showVolumeButton = !showVolumeButton)
    PlayerControlId.SPEED -> copy(showSpeedButton = !showSpeedButton)
    PlayerControlId.QUALITY -> copy(showQualityButton = !showQualityButton)
    PlayerControlId.LOCK -> copy(showLockButton = !showLockButton)
    PlayerControlId.ORIENTATION -> copy(showOrientationButton = !showOrientationButton)
    PlayerControlId.FULLSCREEN -> copy(showFullscreenButton = !showFullscreenButton)
    PlayerControlId.RESET_ZOOM -> copy(showResetZoom = !showResetZoom)
    PlayerControlId.TIME_LABELS -> copy(showTimeLabels = !showTimeLabels)
    PlayerControlId.MINI_PROGRESS -> copy(showMiniProgress = !showMiniProgress)
    PlayerControlId.SEEK_PREVIEW -> copy(showSeekPreview = !showSeekPreview)
    PlayerControlId.SLEEP_TIMER -> copy(showSleepTimer = !showSleepTimer)
    PlayerControlId.PIP -> copy(showPipEntry = !showPipEntry)
    PlayerControlId.RELATED_LIST -> copy(showRelatedList = !showRelatedList)
}

internal enum class PlayerTapZoneSlot { LEFT, CENTER, RIGHT }

internal fun PlayerLayout.tapActionOf(slot: PlayerTapZoneSlot): PlayerTapAction = when (slot) {
    PlayerTapZoneSlot.LEFT -> tapLeft
    PlayerTapZoneSlot.CENTER -> tapCenter
    PlayerTapZoneSlot.RIGHT -> tapRight
}

internal fun PlayerLayout.withNextTapAction(slot: PlayerTapZoneSlot): PlayerLayout = when (slot) {
    PlayerTapZoneSlot.LEFT -> copy(tapLeft = tapLeft.next())
    PlayerTapZoneSlot.CENTER -> copy(tapCenter = tapCenter.next())
    PlayerTapZoneSlot.RIGHT -> copy(tapRight = tapRight.next())
}

private fun PlayerTapAction.next(): PlayerTapAction {
    val values = PlayerTapAction.entries
    return values[(ordinal + 1) % values.size]
}
