package app.amisles.hanime.feature.detail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.feature.detail.components.VideoPlayer
import java.io.File

/**
 * 本地视频播放页：播放「下载页」里已下载到本机的文件，不再依赖任何外部播放器应用。
 *
 * 播放器内部布局完全复用详情页的 [VideoPlayer]（控件、手势、倍速菜单、画中画等），因此
 * **手机横屏自动全屏**与**点全屏按钮进入横屏全屏**两项行为与详情页逐字一致 —— 二者都由
 * VideoPlayer 内部实现（方向监听 + `LaunchedEffect(isFullscreen, activity, deviceTilt)`），
 * 本页只需持有 `isFullscreen` 状态并把它转成回调。
 *
 * 与详情页的差异：
 * - 播放器占满整屏并垂直居中（详情页是长列表中的一项）。
 * - 无连播：`onPlaybackEnded` 不做事。连播开关仍读写**全局偏好**，与详情页同源，避免出现
 *   「开关拨不动」或「各页各说各话」。
 * - 平板不启用「横屏自动全屏」（沿用详情页的取舍：平板横持属常态握持会误触发），
 *   但全屏按钮在平板上照常可用。
 *
 * 文件不存在时（例如被系统清理或在文件管理器里删掉）不会起播，直接给出占位提示。
 */
@Composable
fun LocalPlayerScreen(
    filePath: String?
) {
    val context = LocalContext.current
    val autoPlayNext by Preferences.autoPlayNextFlow.collectAsStateWithLifecycle()
    val sizeInfo = currentWindowSizeInfo()
    var isPlayerFullscreen by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayerFactory.buildLocalVideoPlayer(context).apply {
            // 沿用详情页持久化的倍速偏好
            setPlaybackSpeed(Preferences.playbackSpeed)
        }
    }

    val fileExists = remember(filePath) { !filePath.isNullOrEmpty() && File(filePath).exists() }

    // 装载本地文件。playWhenReady 已在工厂里置 true，这里显式 play() 只是兜底
    LaunchedEffect(filePath, fileExists) {
        val path = filePath
        if (fileExists && !path.isNullOrEmpty()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // 全屏时返回键先退出全屏，而不是直接退出本页（与详情页同一处理）
    BackHandler(enabled = isPlayerFullscreen) { isPlayerFullscreen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (fileExists) {
            VideoPlayer(
                exoPlayer = exoPlayer,
                initialSourceUrl = filePath.orEmpty(),
                isFullscreen = isPlayerFullscreen,
                onFullscreenToggle = { full -> isPlayerFullscreen = full },
                onPlaybackSpeedChanged = { Preferences.setPlaybackSpeed(it) },
                onQualityChanged = { Preferences.setPreferredQuality(it) },
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChanged = { Preferences.setAutoPlayNext(it) },
                autoFullscreenEnabled = !sizeInfo.isTablet,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 文件不存在（被系统清理或在文件管理器里删掉）：给出明确原因，而不是笼统的「加载失败」
            Text(
                text = stringResource(R.string.download_file_not_exist),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
