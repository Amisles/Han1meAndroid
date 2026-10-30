package app.amisles.hanime.feature.detail.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
    isFullscreen: Boolean,
    isInPip: Boolean,
    autoPlayNext: Boolean,
    onAutoPlayNextChanged: (Boolean) -> Unit,
    isLoopPlayback: Boolean,
    onLoopPlaybackChanged: (Boolean) -> Unit,
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
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleMute: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSwitchQuality: (VideoSource, Boolean) -> Unit,
    onToggleFullscreen: () -> Unit,
    onBuildPipParams: () -> PictureInPictureParams
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

    if (!isPlaying && !isBuffering && isReady && playbackError == null) {
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

        PlaybackProgressBar(
            exoPlayer = exoPlayer,
            onSeekInteract = { onMarkControlTap() },
            modifier = Modifier.weight(1f)
        )

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

        if (sortedSources.isNotEmpty()) {
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
                modifier = Modifier.widthIn(min = 160.dp, max = 280.dp),
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

                if (isFullscreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
