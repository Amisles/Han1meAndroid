package app.amisles.hanime.feature.detail.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.HanimeSwitch
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.domain.preferences.PlayerLayout
import app.amisles.hanime.domain.preferences.PlayerResizeMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxScope
import android.app.Activity
import android.app.PictureInPictureParams
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.domain.model.VideoSource
import androidx.compose.runtime.Composable

@Composable
internal fun BoxScope.PlayerControls(
    onPipChange: (Boolean) -> Unit,
    exoPlayer: ExoPlayer,
    layout: PlayerLayout,
    isFullscreen: Boolean,
    isInPip: Boolean,
    autoPlayNext: Boolean,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    isLoopPlayback: Boolean,
    onLoopPlaybackChanged: (Boolean) -> Unit,
    sleepTimer: PlayerSleepTimer,
    resizeMode: PlayerResizeMode,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isReady: Boolean,
    playbackError: PlaybackException?,
    isMuted: Boolean,
    volume: Float,
    effectiveSpeed: Float,
    currentResolution: String,
    currentSourceUrl: String,
    sortedSources: List<VideoSource>,
    playbackSpeeds: List<Float>,
    showSpeedMenu: Boolean,
    showQualityMenu: Boolean,
    showMoreMenu: Boolean,
    speedBtnBounds: Rect,
    qualityBtnBounds: Rect,
    playerPos: Offset,
    activity: Activity?,
    onShowSpeedMenuChange: (Boolean) -> Unit,
    onShowQualityMenuChange: (Boolean) -> Unit,
    onShowMoreMenuChange: (Boolean) -> Unit,
    onSpeedBoundsChange: (Rect) -> Unit,
    onQualityBoundsChange: (Rect) -> Unit,
    onMarkControlTap: () -> Unit,
    onSeekPreview: (Long?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleMute: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSwitchQuality: (VideoSource, Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onLock: () -> Unit,
    isOrientationPinned: Boolean = false,
    onToggleOrientationPin: () -> Unit = {},
    onSleepTimerChange: (PlayerSleepTimer) -> Unit,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    onBuildPipParams: () -> PictureInPictureParams,
    onEditLayout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.6f)
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    )

    if (layout.showCenterPlayButton && !isPlaying && !isBuffering && isReady && playbackError == null) {
        IconButton(
            onClick = {
                onMarkControlTap()
                onTogglePlayPause()
            },
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (layout.showRewindButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onSeekBackward()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = stringResource(R.string.cd_rewind_15s),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (layout.showPlayButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onTogglePlayPause()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (layout.showForwardButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onSeekForward()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = stringResource(R.string.cd_forward_15s),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (layout.showProgressBar) {
            PlaybackProgressBar(
                exoPlayer = exoPlayer,
                onSeekInteract = { onMarkControlTap() },
                onSeekPreview = onSeekPreview,
                showTimeLabels = layout.showTimeLabels,
                modifier = Modifier.weight(1f)
            )
        } else {
            // 进度条隐藏后仍要让右侧控件贴边，否则整排按钮会挤在左侧
            Spacer(modifier = Modifier.weight(1f))
        }

        if (layout.showVolumeButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onToggleMute()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isMuted || volume == 0f) Icons.AutoMirrored.Filled.VolumeMute
                    else if (volume < 0.5f) Icons.AutoMirrored.Filled.VolumeDown
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.cd_volume),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (layout.showSpeedButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onShowSpeedMenuChange(!showSpeedMenu)
                    onShowQualityMenuChange(false)
                    onShowMoreMenuChange(false)
                },
                modifier = Modifier
                    .size(24.dp)
                    .onGloballyPositioned { coords ->
                        val newBounds = Rect(
                            left = coords.positionInRoot().x,
                            top = coords.positionInRoot().y,
                            right = coords.positionInRoot().x + coords.size.width,
                            bottom = coords.positionInRoot().y + coords.size.height
                        )
                        if (speedBtnBounds != newBounds) onSpeedBoundsChange(newBounds)
                    }
            ) {
                Text(
                    text = "${effectiveSpeed}x",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        if (sortedSources.isNotEmpty() && layout.showQualityButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onShowQualityMenuChange(!showQualityMenu)
                    onShowSpeedMenuChange(false)
                    onShowMoreMenuChange(false)
                },
                modifier = Modifier
                    .size(36.dp)
                    .onGloballyPositioned { coords ->
                        val newBounds = Rect(
                            left = coords.positionInRoot().x,
                            top = coords.positionInRoot().y,
                            right = coords.positionInRoot().x + coords.size.width,
                            bottom = coords.positionInRoot().y + coords.size.height
                        )
                        if (qualityBtnBounds != newBounds) onQualityBoundsChange(newBounds)
                    }
            ) {
                Text(
                    text = currentResolution.ifEmpty { stringResource(R.string.cd_quality) },
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        if (isFullscreen) {
            if (layout.showLockButton) {
                IconButton(
                    onClick = {
                        onMarkControlTap()
                        onLock()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.player_lock),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            if (layout.showOrientationButton) {
                IconButton(
                    onClick = {
                        onMarkControlTap()
                        onToggleOrientationPin()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isOrientationPinned) {
                            Icons.Default.ScreenLockRotation
                        } else {
                            Icons.Default.ScreenRotation
                        },
                        contentDescription = stringResource(
                            if (isOrientationPinned) R.string.cd_unlock_orientation else R.string.cd_lock_orientation
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        if (layout.showFullscreenButton) {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onToggleFullscreen()
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen)
                        stringResource(R.string.cd_exit_fullscreen)
                    else
                        stringResource(R.string.cd_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // 更多：点击弹出下拉框，内含连播与循环播放开关
        Box {
            IconButton(
                onClick = {
                    onMarkControlTap()
                    onShowMoreMenuChange(!showMoreMenu)
                    onShowSpeedMenuChange(false)
                    onShowQualityMenuChange(false)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.detail_more),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { onShowMoreMenuChange(false) },
                modifier = Modifier.widthIn(min = 160.dp, max = 300.dp),
                containerColor = Color.Black.copy(alpha = 0.7f)
            ) {
                // 连播（下一集自动播放）开关
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.detail_auto_play_next),
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        HanimeSwitch(
                            checked = autoPlayNext,
                            onCheckedChange = { onAutoPlayNextChanged(it) },
                            modifier = Modifier.scale(0.78f)
                        )
                    },
                    onClick = { onAutoPlayNextChanged(!autoPlayNext) },
                    modifier = Modifier.height(40.dp)
                )

                // 循环播放开关：开启后当前视频播完自动重播，而不是停在结束画面
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.detail_loop_playback),
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        HanimeSwitch(
                            checked = isLoopPlayback,
                            onCheckedChange = { onLoopPlaybackChanged(it) },
                            modifier = Modifier.scale(0.78f)
                        )
                    },
                    onClick = { onLoopPlaybackChanged(!isLoopPlayback) },
                    modifier = Modifier.height(40.dp)
                )

                ResizeModeMenu(
                    resizeMode = resizeMode,
                    onSelect = {
                        onResizeModeChange(it)
                        onShowMoreMenuChange(false)
                    }
                )

                if (layout.showSleepTimer) {
                    SleepTimerMenu(
                        timer = sleepTimer,
                        onChange = {
                            onSleepTimerChange(it)
                            onShowMoreMenuChange(false)
                        }
                    )
                }

                // 布局编辑器入口是「更多」菜单的常驻项：它若也能被隐藏，用户就再也改不回布局
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.player_layout_edit),
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onShowMoreMenuChange(false)
                        onEditLayout()
                    },
                    modifier = Modifier.height(40.dp)
                )

                if (isFullscreen && layout.showPipEntry && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.detail_pip),
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onShowMoreMenuChange(false)
                            activity?.enterPictureInPictureMode(onBuildPipParams())
                            onPipChange(true)
                        },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }
    }

    // 弹框打开时覆盖一层透明遮罩，点击其他区域即收起弹框
    if (showSpeedMenu || showQualityMenu) {
        PlayerMenuScrim(onDismiss = {
            onMarkControlTap()
            onShowSpeedMenuChange(false)
            onShowQualityMenuChange(false)
        })
    }

    if (showSpeedMenu && speedBtnBounds != Rect.Zero) {
        SpeedMenuPopup(
            anchor = speedBtnBounds,
            ui = PlayerMenuUi(
                playerPos = playerPos,
                effectiveSpeed = effectiveSpeed,
                playbackSpeeds = playbackSpeeds,
                onSpeedSelected = { onSetPlaybackSpeed(it) },
                onControlTap = { onMarkControlTap() }
            )
        )
    }

    if (showQualityMenu && sortedSources.isNotEmpty() && qualityBtnBounds != Rect.Zero) {
        QualityMenuPopup(
            anchor = qualityBtnBounds,
            ui = QualityMenuUi(
                playerPos = playerPos,
                sources = sortedSources,
                currentSourceUrl = currentSourceUrl,
                onSourceSelected = { onSwitchQuality(it, true) },
                onControlTap = { onMarkControlTap() }
            )
        )
    }
}

private val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60)

@Composable
private fun ResizeModeMenu(resizeMode: PlayerResizeMode, onSelect: (PlayerResizeMode) -> Unit) {
    MenuSection(label = stringResource(R.string.player_resize_mode)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            listOf(
                PlayerResizeMode.FIT to R.string.player_resize_fit,
                PlayerResizeMode.CROP to R.string.player_resize_crop,
                PlayerResizeMode.STRETCH to R.string.player_resize_stretch,
                PlayerResizeMode.RATIO_16_9 to R.string.player_resize_16_9
            ).forEach { (mode, labelRes) ->
                MenuChip(
                    label = stringResource(labelRes),
                    selected = mode == resizeMode,
                    onClick = { onSelect(mode) }
                )
            }
        }
    }
}

@Composable
private fun SleepTimerMenu(timer: PlayerSleepTimer, onChange: (PlayerSleepTimer) -> Unit) {
    val remaining = rememberSleepRemaining(timer.endAtMillis)
    MenuSection(
        label = stringResource(R.string.player_sleep_timer),
        trailing = remaining.takeIf { it > 0L }?.let { formatSleepRemaining(it) }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            SLEEP_TIMER_MINUTES.forEach { minutes ->
                MenuChip(
                    label = stringResource(R.string.player_sleep_minutes, minutes),
                    selected = timer.minutes == minutes,
                    onClick = {
                        onChange(
                            PlayerSleepTimer(
                                endAtMillis = System.currentTimeMillis() + minutes * 60_000L,
                                minutes = minutes
                            )
                        )
                    }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            MenuChip(
                label = stringResource(R.string.player_sleep_episode),
                selected = timer.afterEpisode,
                onClick = { onChange(PlayerSleepTimer(afterEpisode = true)) }
            )
            MenuChip(
                label = stringResource(R.string.player_sleep_off),
                selected = !timer.active,
                onClick = { onChange(PlayerSleepTimer()) }
            )
        }
    }
}

@Composable
private fun MenuSection(
    label: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            if (trailing != null) {
                Text(text = " · $trailing", color = HanimePrimary, fontSize = 11.sp)
            }
        }
        content()
    }
}

@Composable
private fun MenuChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) HanimePrimary else Color.White.copy(alpha = 0.85f),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun rememberSleepRemaining(endAtMillis: Long): Long {
    var remaining by remember(endAtMillis) {
        mutableLongStateOf((endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(endAtMillis) {
        while (endAtMillis > 0L) {
            remaining = (endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining <= 0L) break
            delay(1000L)
        }
    }
    return remaining
}

private fun formatSleepRemaining(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "${totalSeconds / 60}:$seconds"
}
