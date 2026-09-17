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
 * 页面为**沉浸式**：本页不预留底部导航栏与系统栏内边距（由 NavHost 对该路由跳过内距），
 * 因此播放器是相对**整块屏幕**垂直居中；左上角的返回按钮以**叠加**方式绘制，不占布局高度，
 * 不会把播放器挤离中心。
 *
 * 文件不存在时（例如被系统清理或在文件管理器里删掉）不会起播，直接给出占位提示。
 */
@Composable
fun LocalPlayerScreen(
    filePath: String?,
    onBackClick: () -> Unit = {}
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

        // 返回按钮叠在左上角（写在播放器之后，即绘制在其上层）。刻意用叠加而不是顶栏：
        // 顶栏会占据布局高度，把播放器从屏幕正中心挤下去。
        //
        // 全屏时隐藏：全屏下画面已铺满整屏，且此时直接返回会把 Activity 留在横屏锁定态 ——
        // VideoPlayer 只在 isFullscreen 翻回 false 时才把 requestedOrientation 恢复为进入前的值，
        // 而 popBackStack 会让本页来不及恢复就被销毁。全屏请先点播放器自带的「退出全屏」，
        // 退出后本按钮即出现。
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
