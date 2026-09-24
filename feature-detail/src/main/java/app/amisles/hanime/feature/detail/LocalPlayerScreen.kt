package app.amisles.hanime.feature.detail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.feature.detail.components.DetailBackButton
import app.amisles.hanime.feature.detail.components.VideoPlayer
import java.io.File

/**
 * 本地视频播放页：播放已下载到本机的文件，不依赖任何外部播放器应用。
 *
 * 播放器布局复用详情页的 [VideoPlayer]（控件、手势、倍速菜单、画中画等），故横屏自动全屏与
 * 点按钮全屏两项行为与详情页一致——本页只需持有 `isFullscreen` 状态并转成回调。
 *
 * 与详情页的差异：播放器占满整屏并垂直居中；无连播（连播开关仍读写全局偏好，与详情页同源）；
 * 平板不启用「横屏自动全屏」（横持属常态握持会误触发），但全屏按钮照常可用。
 *
 * 页面为沉浸式：不预留底部导航栏与系统栏内边距，播放器相对整屏居中，返回按钮以叠加方式绘制。
 * 文件不存在时（被清理或删除）不起播，直接给出占位提示。
 */
@Composable
fun LocalPlayerScreen(
    filePath: String?,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val autoPlayNext by Preferences.autoPlayNextFlow.collectAsStateWithLifecycle()
    val loopPlayback by Preferences.loopPlaybackFlow.collectAsStateWithLifecycle()
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
                isLoopPlayback = loopPlayback,
                onLoopPlaybackChanged = { Preferences.setLoopPlayback(it) },
                autoFullscreenEnabled = !sizeInfo.isTablet,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // 文件不存在：给出明确原因，而不是笼统的「加载失败」
            Text(
                text = stringResource(R.string.download_file_not_exist),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 返回按钮叠在左上角（写在播放器之后，绘制在其上层），用叠加而非顶栏以免把播放器挤离中心。
        // 全屏时隐藏：此时直接返回会把 Activity 留在横屏锁定态（VideoPlayer 只在 isFullscreen 翻回
        // false 时才恢复方向，而 popBackStack 会让本页来不及恢复），故全屏请先点播放器自带的「退出全屏」。
        if (!isPlayerFullscreen) {
            DetailBackButton(
                onBackClick = onBackClick,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 4.dp, top = 4.dp)
                    .size(48.dp)
            )
        }
    }
}
