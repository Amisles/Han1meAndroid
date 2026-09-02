package app.amisles.hanime.feature.detail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.LoginUnsupportedDialog
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.feature.detail.components.DetailBackButton
import app.amisles.hanime.feature.detail.components.DetailDownloadDialog
import app.amisles.hanime.feature.detail.components.DetailSkeletonScreen
import app.amisles.hanime.feature.detail.components.DetailVideoPlayer
import app.amisles.hanime.feature.detail.components.TabletDetailSkeleton
import app.amisles.hanime.feature.detail.components.VideoUnavailableHint
import app.amisles.hanime.feature.detail.util.pickInitialSourceUrl
import app.amisles.hanime.feature.detail.util.shareVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun DetailScreen(
    videoUrl: String? = null,
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onAuthorPageClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val context = LocalContext.current
    val videoDetail by viewModel.videoDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadQualities by viewModel.downloadQualities.collectAsStateWithLifecycle()
    val isLoadingQualities by viewModel.isLoadingQualities.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val isSubscribing by viewModel.isSubscribing.collectAsStateWithLifecycle()
    val subscribeError by viewModel.subscribeError.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val commentsError by viewModel.commentsError.collectAsStateWithLifecycle()
    val commentsLoaded by viewModel.commentsLoaded.collectAsStateWithLifecycle()
    val repliesCache by viewModel.repliesCache.collectAsStateWithLifecycle()
    val loadingReplies by viewModel.loadingReplies.collectAsStateWithLifecycle()
    val repliesError by viewModel.repliesError.collectAsStateWithLifecycle()
    val isPostingComment by viewModel.isPostingComment.collectAsStateWithLifecycle()
    val postCommentError by viewModel.postCommentError.collectAsStateWithLifecycle()
    val likingComments by viewModel.likingComments.collectAsStateWithLifecycle()
    val commentLikeError by viewModel.commentLikeError.collectAsStateWithLifecycle()
    val activeReplyTarget by viewModel.activeReplyTarget.collectAsStateWithLifecycle()
    val isPostingReply by viewModel.isPostingReply.collectAsStateWithLifecycle()
    val replyError by viewModel.replyError.collectAsStateWithLifecycle()
    val expandedReplies by viewModel.expandedReplies.collectAsStateWithLifecycle()
    val isLogin by Preferences.loginStateFlow.collectAsStateWithLifecycle()
    val isLoginSupported by Preferences.loginSupportedFlow.collectAsStateWithLifecycle()
    val autoPlayNext by Preferences.autoPlayNextFlow.collectAsStateWithLifecycle()
    val autoFullscreen by Preferences.autoFullscreenLandscapeFlow.collectAsStateWithLifecycle()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    // 0 = 简介（除评论外的全部信息），1 = 评论
    var selectedTab by remember { mutableStateOf(0) }
    var showLoginUnsupportedDialog by remember { mutableStateOf(false) }

    // 评论登录入口拦截：不支持登录时弹窗提示，否则跳转登录页
    val tryNavigateToLogin: () -> Unit = {
        if (isLoginSupported) {
            onNavigateToLogin()
        } else {
            showLoginUnsupportedDialog = true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 续播点（毫秒）：进入时由历史记录计算，传递给 VideoPlayer 在首帧就绪后跳转
    var initialSeekMs by remember { mutableStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayerFactory.buildVideoPlayer(context).apply {
            // 进入即应用已持久化的倍速偏好
            setPlaybackSpeed(Preferences.playbackSpeed)
        }
    }

    // 进入即加载：优先用持久化画质偏好的源，并读取已保存进度用于续播
    LaunchedEffect(videoDetail?.defaultSourceUrl) {
        val detail = videoDetail
        val url = detail?.defaultSourceUrl
        if (!url.isNullOrEmpty()) {
            val preferredUrl = pickInitialSourceUrl(
                detail,
                Preferences.preferredQuality
            )
            // 续播：读取已保存进度（有效续播点 >5s），进入即跳转由 VideoPlayer 在首帧就绪后执行
            initialSeekMs = viewModel.getSavedPlaybackPosition(viewModel.videoId)
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(preferredUrl)))
            exoPlayer.prepare()
        }
    }

    // 播放结束处理：保存进度；若开启连播则自动播放下一集（相关影片首条有效直链）
    fun handlePlaybackEnded() {
        val pos = exoPlayer.currentPosition
        if (pos > 0) {
            viewModel.savePlaybackProgress(pos, exoPlayer.duration)
        }
        if (Preferences.autoPlayNext) {
            val next = videoDetail?.relatedVideos?.firstOrNull { it.videoUrl.isNotBlank() }
            if (next != null) {
                onVideoClick(next.videoUrl)
            }
        }
    }

    // 进度记忆：每 5 秒保存一次当前播放位置/时长，离场时保存最终进度并释放
    DisposableEffect(exoPlayer) {
        val job = scope.launch {
            while (true) {
                delay(5000)
                val state = exoPlayer.playbackState
                if (state != Player.STATE_IDLE && exoPlayer.currentPosition > 0) {
                    viewModel.savePlaybackProgress(exoPlayer.currentPosition, exoPlayer.duration)
                }
            }
        }
        onDispose {
            job.cancel()
            if (exoPlayer.currentPosition > 0) {
                viewModel.savePlaybackProgress(exoPlayer.currentPosition, exoPlayer.duration)
            }
            exoPlayer.release()
        }
    }

    BackHandler(enabled = isPlayerFullscreen) {
        isPlayerFullscreen = false
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl != null && videoUrl.isNotEmpty()) {
            selectedTab = 0
            viewModel.loadVideoDetail(videoUrl)
        }
    }

    // 评论点赞出错时弹出提示
    LaunchedEffect(commentLikeError) {
        commentLikeError?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            viewModel.clearCommentLikeError()
        }
    }

    // 订阅出错时弹出本地化提示
    val subscribeErrorText = when (subscribeError) {
        SubscribeError.NOT_LOGGED_IN -> stringResource(R.string.detail_subscribe_login_required)
        SubscribeError.CSRF_MISSING -> stringResource(R.string.detail_csrf_missing)
        SubscribeError.ARTIST_ID_MISSING -> stringResource(R.string.detail_subscribe_artist_missing)
        SubscribeError.FAILED -> stringResource(R.string.detail_subscribe_failed)
        null -> ""
    }
    LaunchedEffect(subscribeError) {
        subscribeError?.let {
            snackbarHostState.showSnackbar(message = subscribeErrorText, duration = SnackbarDuration.Short)
            viewModel.clearSubscribeError()
        }
    }

    // 平板且非全屏、已加载内容时采用左右分栏（左播放器 / 右其余组件）；手机与加载/错误态由下方 ResponsiveContent 包裹
    val sizeInfo = currentWindowSizeInfo()
    // 启用条件：平板 + 非全屏 + 非「错误且未加载」致命态（回落到手机错误页）
    val useTabletUI = sizeInfo.isTablet && !isPlayerFullscreen
            && !(error != null && videoDetail == null)

    // 评论 tab 的懒加载判断：原 Tab onClick 内的判断逻辑随 detailRestItems 抽取上移至此
    val selectTab: (Int) -> Unit = { tab ->
        selectedTab = tab
        if (tab == 1 && !commentsLoaded && !isLoadingComments) {
            viewModel.loadComments()
        }
    }

    // 「其余组件」的状态与回调包：集中传给 detailRestItems（手机单列与平板右栏共用）
    val restState = DetailRestState(
        selectedTab = selectedTab,
        showDescription = showDescription,
        isFavorite = isFavorite,
        isSubscribed = isSubscribed,
        isSubscribing = isSubscribing,
        comments = comments,
        isLoadingComments = isLoadingComments,
        commentsError = commentsError,
        repliesCache = repliesCache,
        loadingReplies = loadingReplies,
        repliesError = repliesError,
        expandedReplies = expandedReplies,
        isLogin = isLogin,
        isPostingComment = isPostingComment,
        postCommentError = postCommentError,
        likingComments = likingComments,
        activeReplyTarget = activeReplyTarget,
        isPostingReply = isPostingReply,
        replyError = replyError
    )
    val restActions = DetailRestActions(
        onTabSelected = selectTab,
        onToggleDescription = { showDescription = !showDescription },
        onDownloadClick = {
            showDownloadDialog = true
            viewModel.loadDownloadQualities()
        },
        onToggleFavorite = { viewModel.toggleFavorite() },
        onShareClick = { shareVideo(context, videoDetail?.title.orEmpty(), videoUrl.orEmpty()) },
        onToggleSubscribe = { viewModel.toggleSubscribe() },
        onTagClick = onTagClick,
        onAuthorClick = onAuthorClick,
        onAuthorPageClick = onAuthorPageClick,
        onVideoClick = onVideoClick,
        onLoadComments = { force -> viewModel.loadComments(force = force) },
        onLoadReplies = { viewModel.loadReplies(it) },
        onToggleReplies = { viewModel.toggleReplies(it) },
        onPostComment = { viewModel.postComment(it) },
        onClearPostCommentError = { viewModel.clearPostCommentError() },
        onToggleCommentLike = { viewModel.toggleCommentLike(it) },
        onStartReply = { commentId, replyToUsername -> viewModel.startReply(commentId, replyToUsername) },
        onSubmitReply = { viewModel.submitReply(it) },
        onCancelReply = { viewModel.cancelReply() },
        onClearReplyError = { viewModel.clearReplyError() },
        onNavigateToLogin = tryNavigateToLogin
    )

    if (useTabletUI) {
        if (isLoading) {
            // 平板加载骨架：左 3/4 视频区占位、右 1/4 详情占位
            TabletDetailSkeleton()
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 左侧：视频播放器（垂直居中）
                Box(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val detail = videoDetail
                    if (detail != null && detail.defaultSourceUrl.isNotEmpty()) {
                        DetailVideoPlayer(
                            exoPlayer = exoPlayer,
                            detail = detail,
                            initialPositionMs = initialSeekMs,
                            isFullscreen = false,
                            onFullscreenToggle = { full -> isPlayerFullscreen = full },
                            onPlaybackEnded = { handlePlaybackEnded() },
                            autoPlayNext = autoPlayNext,
                            autoFullscreen = autoFullscreen,
                            // 平板分栏左半屏已是放大播放器，横屏属常态握持，自动全屏会误触发，故禁用
                            autoFullscreenEnabled = false,
                            modifier = Modifier
                        )
                    } else {
                        VideoUnavailableHint(
                            hasError = error != null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 返回按钮覆盖在播放器左上角
                    DetailBackButton(
                        onBackClick = onBackClick,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(48.dp)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }

                // 右侧：其余组件（可滚动）
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    videoDetail?.let { detailRestItems(it, restState, restActions) }
                }
            }
        }
    } else {
        ResponsiveContent {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isPlayerFullscreen) Modifier.statusBarsPadding() else Modifier)
                    .background(if (isPlayerFullscreen) Color.Black else MaterialTheme.colorScheme.background)
            ) {
                if (!isPlayerFullscreen) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 15.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DetailBackButton(
                                onBackClick = onBackClick,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                if (isLoading) {
                    item(key = "detail_skeleton") {
                        DetailSkeletonScreen()
                    }
                } else if (error != null && videoDetail == null) {
                    item(key = "detail_error") {
                        KaomojiErrorView(
                            message = error,
                            onRetry = { videoUrl?.let { viewModel.loadVideoDetail(it) } }
                        )
                    }
                } else {

                    item(key = "video_player") {
                        val detail = videoDetail
                        if (detail != null && detail.defaultSourceUrl.isNotEmpty()) {
                            DetailVideoPlayer(
                                exoPlayer = exoPlayer,
                                detail = detail,
                                initialPositionMs = initialSeekMs,
                                isFullscreen = isPlayerFullscreen,
                                onFullscreenToggle = { full -> isPlayerFullscreen = full },
                                onPlaybackEnded = { handlePlaybackEnded() },
                                autoPlayNext = autoPlayNext,
                                autoFullscreen = autoFullscreen,
                                modifier = if (isPlayerFullscreen) Modifier.fillParentMaxSize() else Modifier
                            )
                        } else {
                            VideoUnavailableHint(
                                hasError = error != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(225.dp)
                                    .background(Color.Black)
                            )
                        }
                    }

                    videoDetail?.takeIf { !isPlayerFullscreen }?.let { detailRestItems(it, restState, restActions) }

                }
            }
        }
    }

    if (showDownloadDialog) {
        DetailDownloadDialog(
            isLoadingQualities = isLoadingQualities,
            downloadQualities = downloadQualities,
            onQualitySelected = { quality ->
                viewModel.startDownload(quality)
                showDownloadDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.detail_download_added),
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onDismiss = { showDownloadDialog = false }
        )
    }


    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(bottom = 16.dp)
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (showLoginUnsupportedDialog) {
        LoginUnsupportedDialog(
            onGoToSettings = {
                showLoginUnsupportedDialog = false
                onNavigateToSettings()
            },
            onDismiss = { showLoginUnsupportedDialog = false }
        )
    }
}
