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
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.feature.detail.components.DetailBackButton
import app.amisles.hanime.feature.detail.components.VideoPlayer
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

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
    val appPrefs = LocalAppPreferences.current
    val context = LocalContext.current
    val autoPlayNext by appPrefs.autoPlayNextFlow.collectAsStateWithLifecycle()
    val loopPlayback by appPrefs.loopPlaybackFlow.collectAsStateWithLifecycle()
    val sizeInfo = currentWindowSizeInfo()
    var isPlayerFullscreen by remember { mutableStateOf(false) }

    // 与详情页同理：不能在组合期同步构建播放器，否则整段构建压在页面首帧上
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(Unit) {
        val player = ExoPlayerFactory.buildLocalVideoPlayer(context, appPrefs.antiHotlinkReferer).apply {
            // 沿用详情页持久化的倍速偏好；读 StateFlow 缓存值，避免同步解密
            setPlaybackSpeed(appPrefs.playbackSpeedFlow.value)
        }
        // 构建期间页面可能已离开组合（快速返回）：此时无人持有该播放器，就地释放
        if (!currentCoroutineContext().isActive) {
            player.release()
            return@LaunchedEffect
        }
        exoPlayer = player
    }

    val fileExists = remember(filePath) { !filePath.isNullOrEmpty() && File(filePath).exists() }

    // 装载本地文件。playWhenReady 已在工厂里置 true，这里显式 play() 只是兜底。
    // 以 exoPlayer 为键：播放器异步就绪，就绪后必须重新装载一次。
    LaunchedEffect(filePath, fileExists, exoPlayer) {
        val path = filePath
        val player = exoPlayer
        if (player != null && fileExists && !path.isNullOrEmpty()) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        if (player == null) {
            // 播放器尚未就绪：无可释放。此分支同时保证 DisposableEffect 一定有返回值。
            onDispose { }
        } else {
            // 同步释放：延后一帧的 post 在宿主提前销毁时会丢失，导致播放器与解码器泄漏
            onDispose { releasePlayerSafely(player) }
        }
    }

    // 全屏时返回键先退出全屏，而不是直接退出本页（与详情页同一处理）
    BackHandler(enabled = isPlayerFullscreen) { isPlayerFullscreen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!fileExists) {
            // 文件不存在：给出明确原因，而不是笼统的「加载失败」
            Text(
                text = stringResource(R.string.download_file_not_exist),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val player = exoPlayer
            // 播放器仍在后台构建时留空：外层已是黑底，不显示错误提示以免误报
            if (player != null) {
                VideoPlayer(
                    exoPlayer = player,
                    initialSourceUrl = filePath.orEmpty(),
                    isFullscreen = isPlayerFullscreen,
                    onFullscreenToggle = { full -> isPlayerFullscreen = full },
                    onPlaybackSpeedChanged = { appPrefs.setPlaybackSpeed(it) },
                    onQualityChanged = { appPrefs.setPreferredQuality(it) },
                    autoPlayNext = autoPlayNext,
                    onAutoPlayNextChanged = { appPrefs.setAutoPlayNext(it) },
                    isLoopPlayback = loopPlayback,
                    onLoopPlaybackChanged = { appPrefs.setLoopPlayback(it) },
                    autoFullscreenEnabled = !sizeInfo.isTablet,
                    mediaTitle = File(filePath.orEmpty()).name,
                    longPressBoostSpeed = appPrefs.longPressBoostSpeedFlow.value,
                    doubleTapSeekEnabled = appPrefs.doubleTapSeekFlow.value,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
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
