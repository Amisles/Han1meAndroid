package app.amisles.hanime.feature.detail.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.domain.model.VideoDetail
import app.amisles.hanime.feature.detail.util.pickInitialSourceUrl

/**
 * 详情页播放器：把两处（平板左半屏 / 手机单列）重复的 VideoPlayer 调用收拢为一处。
 * 画质、倍速、连播、横屏全屏等偏好统一落盘到 Preferences，行为与拆分前完全一致。
 *
 * [autoFullscreenEnabled] 默认跟随偏好 [autoFullscreen]；平板分栏下由调用方传 false，
 * 避免平板常态横持时误触发自动全屏（菜单开关仍显示用户真实偏好）。
 */
@Composable
internal fun DetailVideoPlayer(
    exoPlayer: ExoPlayer,
    detail: VideoDetail,
    initialPositionMs: Long,
    isFullscreen: Boolean,
    onFullscreenToggle: (Boolean) -> Unit,
    onPlaybackEnded: () -> Unit,
    autoPlayNext: Boolean,
    autoFullscreen: Boolean,
    autoFullscreenEnabled: Boolean = autoFullscreen,
    modifier: Modifier = Modifier
) {
    VideoPlayer(
        exoPlayer = exoPlayer,
        posterUrl = detail.posterUrl,
        videoSources = detail.videoSources,
        initialSourceUrl = pickInitialSourceUrl(detail, Preferences.preferredQuality),
        initialPositionMs = initialPositionMs,
        preloadUrl = detail.relatedVideos.firstOrNull { it.videoUrl.isNotBlank() }?.videoUrl ?: "",
        isFullscreen = isFullscreen,
        onFullscreenToggle = onFullscreenToggle,
        onPlaybackSpeedChanged = { Preferences.setPlaybackSpeed(it) },
        onQualityChanged = { Preferences.setPreferredQuality(it) },
        onPlaybackEnded = { onPlaybackEnded() },
        autoPlayNext = autoPlayNext,
        onAutoPlayNextChanged = { Preferences.setAutoPlayNext(it) },
        autoFullscreen = autoFullscreen,
        onAutoFullscreenChanged = { Preferences.setAutoFullscreenLandscape(it) },
        autoFullscreenEnabled = autoFullscreenEnabled,
        modifier = modifier
    )
}

/**
 * 视频不可用时的占位文案：分栏布局填充整块、单列布局固定 225dp 高。
 * [hasError] 为真显示「加载失败」，否则显示「暂无视频」。
 */
@Composable
internal fun VideoUnavailableHint(
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (hasError) R.string.detail_load_failed else R.string.common_no_videos),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

/**
 * 详情页返回按钮。分栏布局用白色图标浮在播放器左上角，单列布局用 onBackground 图标，
 * 故 tint 与整条 modifier 链都由调用方提供，保持原有尺寸与内边距。
 */
@Composable
internal fun DetailBackButton(
    onBackClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = { onBackClick() }, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
