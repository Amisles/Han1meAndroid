package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.HanimeSwitch
import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerTapAction

/**
 * 播放器布局编辑器：控件显隐、更多菜单项、点按分区动作与分区宽度。
 *
 * 改动即时落盘（[onLayoutChange] 直接写偏好），播放器在对话框后面按新布局重绘，
 * 因此不需要「保存」这一步；关掉对话框就是当前生效的布局。
 */
@Composable
internal fun PlayerLayoutEditor(
    layout: PlayerLayout,
    onLayoutChange: (PlayerLayout) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.player_layout_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onLayoutChange(PlayerLayout.DEFAULT) }) {
                        Text(text = stringResource(R.string.player_layout_reset), fontSize = 13.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.player_layout_done), fontSize = 13.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    EditorSection(title = stringResource(R.string.player_layout_section_controls)) {
                        EditorSwitch(stringResource(R.string.player_layout_show_nav_buttons), layout.showNavButtons) {
                            onLayoutChange(layout.copy(showNavButtons = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_center_play), layout.showCenterPlayButton) {
                            onLayoutChange(layout.copy(showCenterPlayButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_rewind), layout.showRewindButton) {
                            onLayoutChange(layout.copy(showRewindButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_play), layout.showPlayButton) {
                            onLayoutChange(layout.copy(showPlayButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_forward), layout.showForwardButton) {
                            onLayoutChange(layout.copy(showForwardButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_progress), layout.showProgressBar) {
                            onLayoutChange(layout.copy(showProgressBar = it))
                        }
                        EditorSwitch(
                            label = stringResource(R.string.player_layout_show_time),
                            checked = layout.showTimeLabels,
                            enabled = layout.showProgressBar
                        ) {
                            onLayoutChange(layout.copy(showTimeLabels = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_mini_progress), layout.showMiniProgress) {
                            onLayoutChange(layout.copy(showMiniProgress = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_seek_preview), layout.showSeekPreview) {
                            onLayoutChange(layout.copy(showSeekPreview = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_volume), layout.showVolumeButton) {
                            onLayoutChange(layout.copy(showVolumeButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_speed), layout.showSpeedButton) {
                            onLayoutChange(layout.copy(showSpeedButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_quality), layout.showQualityButton) {
                            onLayoutChange(layout.copy(showQualityButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_lock), layout.showLockButton) {
                            onLayoutChange(layout.copy(showLockButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_orientation), layout.showOrientationButton) {
                            onLayoutChange(layout.copy(showOrientationButton = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_fullscreen), layout.showFullscreenButton) {
                            onLayoutChange(layout.copy(showFullscreenButton = it))
                        }
                    }

                    EditorSection(title = stringResource(R.string.player_layout_section_menu)) {
                        EditorSwitch(stringResource(R.string.player_layout_show_sleep_timer), layout.showSleepTimer) {
                            onLayoutChange(layout.copy(showSleepTimer = it))
                        }
                        EditorSwitch(stringResource(R.string.player_layout_show_pip), layout.showPipEntry) {
                            onLayoutChange(layout.copy(showPipEntry = it))
                        }
                    }

                    EditorSection(title = stringResource(R.string.player_layout_section_tap)) {
                        EditorAction(
                            label = stringResource(R.string.player_layout_tap_left),
                            selected = layout.tapLeft
                        ) { onLayoutChange(layout.copy(tapLeft = it)) }
                        EditorAction(
                            label = stringResource(R.string.player_layout_tap_center),
                            selected = layout.tapCenter
                        ) { onLayoutChange(layout.copy(tapCenter = it)) }
                        EditorAction(
                            label = stringResource(R.string.player_layout_tap_right),
                            selected = layout.tapRight
                        ) { onLayoutChange(layout.copy(tapRight = it)) }

                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.player_layout_side_zone),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(
                                        R.string.player_layout_side_zone_value,
                                        layout.sideZonePercent
                                    ),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Slider(
                                value = layout.sideZonePercent.toFloat(),
                                onValueChange = { value ->
                                    onLayoutChange(
                                        layout.copy(sideZonePercent = value.toInt())
                                    )
                                },
                                valueRange = PlayerLayout.MIN_SIDE_ZONE_PERCENT.toFloat()..
                                    PlayerLayout.MAX_SIDE_ZONE_PERCENT.toFloat(),
                                steps = PlayerLayout.MAX_SIDE_ZONE_PERCENT -
                                    PlayerLayout.MIN_SIDE_ZONE_PERCENT - 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
    Column(content = content)
}

@Composable
private fun EditorSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        HanimeSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun EditorAction(
    label: String,
    selected: PlayerTapAction,
    onSelect: (PlayerTapAction) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlayerTapAction.entries.forEach { action ->
                val active = action == selected
                Text(
                    text = stringResource(tapActionLabel(action)),
                    fontSize = 12.sp,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { onSelect(action) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

private fun tapActionLabel(action: PlayerTapAction): Int = when (action) {
    PlayerTapAction.REWIND -> R.string.player_layout_action_rewind
    PlayerTapAction.PLAY_PAUSE -> R.string.player_layout_action_play
    PlayerTapAction.FORWARD -> R.string.player_layout_action_forward
    PlayerTapAction.NONE -> R.string.player_layout_action_none
}
