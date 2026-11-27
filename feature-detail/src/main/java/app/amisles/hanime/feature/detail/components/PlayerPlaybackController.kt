package app.amisles.hanime.feature.detail.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.net.toUri
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.VideoSource
import app.amisles.hanime.feature.detail.playback.PlaybackSessionBridge
import kotlin.math.abs
import kotlin.math.roundToInt

internal class PlayerPlaybackController(
    private val exoPlayer: ExoPlayer,
    private val context: Context,
    private val sources: () -> List<VideoSource>,
    private val mediaTitle: () -> String,
    private val mediaArtist: () -> String,
    private val posterUrl: () -> String,
    initialSourceUrl: String,
    private val initialPositionMs: () -> Long,
    private val isPrivacyMode: () -> Boolean,
    private val autoQualityEnabled: () -> Boolean,
    private val isLoopPlayback: () -> Boolean,
    private val sleepAfterEpisode: () -> Boolean,
    private val longPressBoostSpeed: () -> Float,
    private val onSourceError: () -> Unit,
    private val onPlaybackEnded: () -> Unit,
    private val onQualityChanged: (String) -> Unit,
    private val onPlaybackSpeedChanged: (Float) -> Unit,
    private val onInitialSeekApplied: (Long) -> Unit,
    private val onSleepEpisodeFinished: () -> Unit,
    private val onGestureHint: (String?) -> Unit,
    private val onCloseSpeedMenu: () -> Unit,
    private val onCloseQualityMenu: () -> Unit,
    private val onServiceNeeded: () -> Unit
) {
    val isPlaying = mutableStateOf(false)
    val isBuffering = mutableStateOf(false)
    val isReady = mutableStateOf(false)
    val isSwitchingQuality = mutableStateOf(false)
    val currentSourceUrl = mutableStateOf(initialSourceUrl)
    val playbackError = mutableStateOf<PlaybackException?>(null)
    val videoAspect = mutableFloatStateOf(0f)
    val showPoster = mutableStateOf(posterUrl().isNotEmpty())
    val isMuted = mutableStateOf(false)
    // 手势调整的是系统媒体音量，这里存它的 0..1 比例；exoPlayer.volume 只留给静音开关
    val volume = mutableFloatStateOf(1f)
    val playbackSpeed = mutableFloatStateOf(1f)
    val isLongPressBoost = mutableStateOf(false)
    val boostSpeed = mutableFloatStateOf(longPressBoostSpeed())

    private var manualQualityOverride = false
    private var autoSwitched = false
    private var stableTicks = 0
    private var rebufferCount = 0
    private var lastPlaybackState = Player.STATE_IDLE
    private var autoErrorRetryUsed = false
    private var sourceRefreshRequested = false
    private var initialSeekApplied = false
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var lastVolumeStep = -1

    val effectiveSpeed: Float
        get() = if (isLongPressBoost.value) boostSpeed.floatValue else playbackSpeed.floatValue

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val previousState = lastPlaybackState
            lastPlaybackState = playbackState
            isBuffering.value = playbackState == Player.STATE_BUFFERING
            when (playbackState) {
                Player.STATE_IDLE -> Unit
                Player.STATE_BUFFERING -> {
                    // 仅「从 READY 掉回 BUFFERING 且用户意图播放」算一次 rebuffer。
                    // 不能用 isPlaying 判定：ExoPlayer 进入缓冲会先回调 onIsPlayingChanged(false) 再回调
                    // STATE_BUFFERING，以它为条件会让计数恒为 0。首次起播与换画质均不计入。
                    val isRebuffer = previousState == Player.STATE_READY &&
                        exoPlayer.playWhenReady && !isSwitchingQuality.value
                    if (isRebuffer && !autoSwitched && autoQualityEnabled() && !manualQualityOverride) {
                        rebufferCount++
                        if (rebufferCount >= 2) {
                            val available = sources()
                            val idx = available.indexOfFirst { it.url == currentSourceUrl.value }
                            if (idx in 0 until available.lastIndex && !isSwitchingQuality.value) {
                                switchQuality(available[idx + 1])
                                rebufferCount = 0
                                autoSwitched = true
                                stableTicks = 0
                            }
                        }
                    }
                }
                Player.STATE_READY -> {
                    isReady.value = true
                    isSwitchingQuality.value = false
                    sourceRefreshRequested = false
                    rebufferCount = 0
                    playbackError.value = null
                    if (!initialSeekApplied && initialPositionMs() > 5000) {
                        val duration = exoPlayer.duration
                        if (duration <= 0 || initialPositionMs() < duration - 5000) {
                            exoPlayer.seekTo(initialPositionMs())
                            onInitialSeekApplied(initialPositionMs())
                        }
                        initialSeekApplied = true
                    }
                    // 不调用 setForegroundMode(true)：前台状态由 PlaybackService 的媒体通知承担，
                    // 播放器本身无需再为后台保活另开一条路径。
                    if (autoSwitched && autoQualityEnabled() && !manualQualityOverride) {
                        stableTicks++
                        val available = sources()
                        val idx = available.indexOfFirst { it.url == currentSourceUrl.value }
                        if (stableTicks >= STABLE_TICKS_FOR_UPGRADE && idx > 0) {
                            switchQuality(available[idx - 1])
                            autoSwitched = false
                            stableTicks = 0
                        }
                    } else {
                        stableTicks = 0
                    }
                }
                Player.STATE_ENDED -> {
                    if (!isLoopPlayback()) {
                        if (sleepAfterEpisode()) {
                            onSleepEpisodeFinished()
                        } else {
                            onPlaybackEnded()
                        }
                    }
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoAspect.floatValue = if (videoSize.height > 0) {
                videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            } else {
                0f
            }
        }

        override fun onRenderedFirstFrame() {
            showPoster.value = false
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
            if (playing) onServiceNeeded()
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            isBuffering.value = false
            isSwitchingQuality.value = false
            if (isSourceUnreadable(error) && !sourceRefreshRequested) {
                sourceRefreshRequested = true
                onSourceError()
            }
            val available = sources()
            val index = available.indexOfFirst { it.url == currentSourceUrl.value }
            val linkBroken = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
            if (linkBroken && !autoErrorRetryUsed && index in 0 until available.lastIndex) {
                autoErrorRetryUsed = true
                playbackError.value = null
                onGestureHint(context.getString(R.string.player_quality_fallback))
                switchQuality(available[index + 1])
                exoPlayer.playWhenReady = true
                return
            }
            playbackError.value = error
        }
    }

    fun attach() {
        PlaybackSessionBridge.bind(exoPlayer, context)
        exoPlayer.addListener(listener)
        isMuted.value = exoPlayer.volume == 0f
        syncVolume()
        playbackSpeed.floatValue = exoPlayer.playbackParameters.speed
        isPlaying.value = exoPlayer.isPlaying
        isBuffering.value = exoPlayer.playbackState == Player.STATE_BUFFERING
        if (exoPlayer.playbackState == Player.STATE_READY) isReady.value = true
    }

    fun detach() {
        PlaybackSessionBridge.unbind(exoPlayer)
        exoPlayer.removeListener(listener)
    }

    fun onSourceUrlChanged(url: String) {
        if (url.isNotBlank() && currentSourceUrl.value != url) {
            currentSourceUrl.value = url
        }
    }

    fun onMediaChanged(sourceUrl: String) {
        currentSourceUrl.value = sourceUrl
        playbackError.value = null
        isReady.value = false
        isSwitchingQuality.value = false
        manualQualityOverride = false
        autoSwitched = false
        rebufferCount = 0
        stableTicks = 0
        autoErrorRetryUsed = false
        sourceRefreshRequested = false
        initialSeekApplied = false
        lastPlaybackState = Player.STATE_IDLE
    }

    fun switchQuality(source: VideoSource, userInitiated: Boolean = false) {
        if (source.url == currentSourceUrl.value || (isSwitchingQuality.value && !userInitiated)) {
            onCloseQualityMenu()
            return
        }
        if (userInitiated) {
            manualQualityOverride = true
            autoSwitched = false
            stableTicks = 0
            rebufferCount = 0
        }
        isSwitchingQuality.value = true
        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition
        currentSourceUrl.value = source.url
        isBuffering.value = true
        onCloseQualityMenu()
        exoPlayer.setMediaItem(buildMediaItem(source.url))
        exoPlayer.prepare()
        exoPlayer.seekTo(position)
        if (wasPlaying) exoPlayer.play()
        if (userInitiated) onQualityChanged(source.resolution)
    }

    /**
     * 换用另一路直链（简繁字幕是两份文件），保留进度与播放态。
     *
     * 与 [switchQuality] 的区别：不标记手动覆盖、不回写画质偏好 —— 换的只是字幕版本，画质档位没变。
     */
    fun switchToSource(url: String) {
        if (url.isBlank() || url == currentSourceUrl.value) return
        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition
        currentSourceUrl.value = url
        // 复用换画质的标记：换源瞬间的缓冲区切换不应被算成卡顿降档
        isSwitchingQuality.value = true
        isBuffering.value = true
        exoPlayer.setMediaItem(buildMediaItem(url))
        exoPlayer.prepare()
        exoPlayer.seekTo(position)
        if (wasPlaying) exoPlayer.play()
    }

    fun retry() {
        val position = exoPlayer.currentPosition
        playbackError.value = null
        isBuffering.value = true
        if (exoPlayer.mediaItemCount == 0 && currentSourceUrl.value.isNotBlank()) {
            exoPlayer.setMediaItem(buildMediaItem(currentSourceUrl.value))
        }
        exoPlayer.prepare()
        if (position > 0L) exoPlayer.seekTo(position)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekBackward() {
        exoPlayer.seekTo((exoPlayer.currentPosition - 15000).coerceAtLeast(0L))
    }

    fun seekForward() {
        val target = exoPlayer.currentPosition + 15000
        val duration = exoPlayer.duration
        exoPlayer.seekTo(if (duration > 0L) target.coerceAtMost(duration) else target)
    }

    fun toggleMute() {
        isMuted.value = !isMuted.value
        exoPlayer.volume = if (isMuted.value) 0f else 1f
    }

    fun syncVolume() {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val step = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        lastVolumeStep = step
        volume.floatValue = step.toFloat() / max
    }

    fun setVolume(fraction: Float): Float {
        val target = fraction.coerceIn(0f, 1f)
        volume.floatValue = target
        if (isMuted.value) {
            isMuted.value = false
            exoPlayer.volume = 1f
        }
        val manager = audioManager ?: return target
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return target
        val step = (target * max).roundToInt().coerceIn(0, max)
        if (step != lastVolumeStep) {
            lastVolumeStep = step
            runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
        }
        return step.toFloat() / max
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed.floatValue = speed
        exoPlayer.setPlaybackSpeed(speed)
        onCloseSpeedMenu()
        onPlaybackSpeedChanged(speed)
    }

    // 长按加速：只临时修改播放器与「生效倍速」，不改写用户选择的 playbackSpeed。
    // 原实现把 playbackSpeed 直接置 2f，加速期间若打开倍速菜单再选中任一档，
    // UI 高亮与恢复值都会错乱（松手恢复到被覆盖后的值）；这里让两者彻底分离。
    // 加速的起手倍率取偏好里的「长按加速倍速」，之后可被上下滑动改写，松手一律回到 playbackSpeed。
    fun setLongPressBoost(active: Boolean) {
        if (active) {
            if (!isLongPressBoost.value) {
                isLongPressBoost.value = true
                boostSpeed.floatValue = longPressBoostSpeed()
                runCatching { exoPlayer.setPlaybackSpeed(boostSpeed.floatValue) }
            }
        } else if (isLongPressBoost.value) {
            isLongPressBoost.value = false
            runCatching { exoPlayer.setPlaybackSpeed(playbackSpeed.floatValue) }
        }
    }

    /**
     * 长按加速期间上下滑动调整倍率。
     *
     * 只在加速中生效：手势若在松手后才补发一次位移，不能把常驻倍速改掉。
     * 与 [setLongPressBoost] 一样不回写偏好 —— 加速是临时状态，用户松手就该回到自己选的档位。
     */
    fun updateLongPressBoostSpeed(speed: Float) {
        if (!isLongPressBoost.value) return
        val target = speed.coerceIn(
            PlayerGestureMath.MIN_BOOST_SPEED,
            PlayerGestureMath.MAX_BOOST_SPEED
        )
        // 倍率已量化到 0.1x，同一档位下每帧事件都会重复下发同一个值，跳过可省掉无谓的播放器调用
        if (abs(target - boostSpeed.floatValue) < 0.05f) return
        boostSpeed.floatValue = target
        runCatching { exoPlayer.setPlaybackSpeed(target) }
    }

    /**
     * 隐私模式下只留通用标题：媒体元数据同时驱动通知栏、锁屏与蓝牙控件，任何一处带标题都会泄露内容。
     */
    private fun buildMetadata(): MediaMetadata {
        if (isPrivacyMode()) {
            return MediaMetadata.Builder()
                .setTitle(context.getString(R.string.privacy_media_title))
                .build()
        }
        val artwork = posterUrl()
        return MediaMetadata.Builder()
            .setTitle(mediaTitle())
            .setArtist(mediaArtist())
            .apply { if (artwork.isNotBlank()) setArtworkUri(artwork.toUri()) }
            .build()
    }

    /** 开关隐私模式时重写当前条目的元数据：同一直链只改元数据，ExoPlayer 会无缝续播。 */
    fun refreshMetadata() {
        val item = exoPlayer.currentMediaItem ?: return
        runCatching {
            exoPlayer.replaceMediaItem(
                exoPlayer.currentMediaItemIndex,
                item.buildUpon().setMediaMetadata(buildMetadata()).build()
            )
        }
    }

    private fun buildMediaItem(url: String): MediaItem {
        val metadata = buildMetadata()
        val mimeType = sources().firstOrNull { it.url == url }?.normalizedMimeType.orEmpty()
        val builder = MediaItem.Builder().setUri(url).setMediaMetadata(metadata)
        if (mimeType.isNotEmpty()) builder.setMimeType(mimeType)
        return builder.build()
    }
}

private fun isSourceUnreadable(error: PlaybackException): Boolean =
    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
