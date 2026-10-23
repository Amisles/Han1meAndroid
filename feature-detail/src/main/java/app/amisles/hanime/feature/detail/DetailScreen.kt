package app.amisles.hanime.feature.detail

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.common.util.AppLogger
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.LoginUnsupportedDialog
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.domain.repository.DownloadRepository
import app.amisles.hanime.feature.detail.components.DetailBackButton
import app.amisles.hanime.feature.detail.components.DetailDownloadDialog
import app.amisles.hanime.feature.detail.components.DetailSkeletonScreen
import app.amisles.hanime.feature.detail.components.DetailVideoPlayer
import app.amisles.hanime.feature.detail.components.TabletDetailSkeleton
import app.amisles.hanime.feature.detail.components.VideoUnavailableHint
import app.amisles.hanime.feature.detail.util.pickInitialSourceUrl
import app.amisles.hanime.feature.detail.util.shareVideo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.amisles.hanime.feature.detail.comment.CommentContext
import app.amisles.hanime.feature.detail.comment.CommentViewModel


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
    val appPrefs = LocalAppPreferences.current
    val viewModel: DetailViewModel = hiltViewModel()
    // 评论域独立 VM。用 key 区分，避免与其它页面的 CommentViewModel 冲突。
    val commentVm: CommentViewModel = hiltViewModel(key = "comment")
    val videoDetail by viewModel.videoDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // 评论域的调用上下文：csrfToken 与 videoId 都来自视频域，按需构造后传给评论 VM。
    val commentCtx: () -> CommentContext = {
        CommentContext(
            videoId = viewModel.videoId,
            csrfToken = videoDetail?.csrfToken.orEmpty(),
            commentCount = videoDetail?.commentCount ?: 0
        )
    }
    val context = LocalContext.current
    val downloadQualities by viewModel.downloadQualities.collectAsStateWithLifecycle()
    val isLoadingQualities by viewModel.isLoadingQualities.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val isSubscribing by viewModel.isSubscribing.collectAsStateWithLifecycle()
    val subscribeError by viewModel.subscribeError.collectAsStateWithLifecycle()
    val comments by commentVm.comments.collectAsStateWithLifecycle()
    val isLoadingComments by commentVm.isLoadingComments.collectAsStateWithLifecycle()
    val commentsError by commentVm.commentsError.collectAsStateWithLifecycle()
    val commentsLoaded by commentVm.commentsLoaded.collectAsStateWithLifecycle()
    val repliesCache by commentVm.repliesCache.collectAsStateWithLifecycle()
    val loadingReplies by commentVm.loadingReplies.collectAsStateWithLifecycle()
    val repliesError by commentVm.repliesError.collectAsStateWithLifecycle()
    val isPostingComment by commentVm.isPostingComment.collectAsStateWithLifecycle()
    val postCommentError by commentVm.postCommentError.collectAsStateWithLifecycle()
    val likingComments by commentVm.likingComments.collectAsStateWithLifecycle()
    val commentLikeError by commentVm.commentLikeError.collectAsStateWithLifecycle()
    val activeReplyTarget by commentVm.activeReplyTarget.collectAsStateWithLifecycle()
    val isPostingReply by commentVm.isPostingReply.collectAsStateWithLifecycle()
    val replyError by commentVm.replyError.collectAsStateWithLifecycle()

    // 视频域切换视频时通知评论域复位：两个 VM 独立，同步走这条单向通道。
    // videoSwitched 是 StateFlow，带当前值，晚订阅也不会丢（此前的 SharedFlow replay=0
    // 会让首次进入的事件必然丢失）。初始值与「尚未加载视频」都表现为空串，直接跳过。
    LaunchedEffect(viewModel, commentVm) {
        viewModel.videoSwitched.collect { videoId ->
            if (videoId.isNotEmpty()) commentVm.selectVideo(videoId)
        }
    }
    val expandedReplies by commentVm.expandedReplies.collectAsStateWithLifecycle()
    val isLogin by appPrefs.loginStateFlow.collectAsStateWithLifecycle()
    val isLoginSupported by appPrefs.loginSupportedFlow.collectAsStateWithLifecycle()
    val autoPlayNext by appPrefs.autoPlayNextFlow.collectAsStateWithLifecycle()

    // 播放通知需要 POST_NOTIFICATIONS（Android 13+）。此前只有下载页申请过，
    // 用户在详情页直接播放时通知会被静默丢弃，而前台服务仍在运行，表现为「功能坏了」。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不阻断播放：只是没有媒体通知 */ }
    var notificationPermissionAsked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!notificationPermissionAsked) {
            notificationPermissionAsked = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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

    // 续播点（毫秒）：进入时由历史记录计算，传给 VideoPlayer 在首帧就绪后跳转
    var initialSeekMs by remember { mutableStateOf(0L) }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // 续播点在播放器构建期间并行读取，避免「先 await 播放器、再 await 数据库」串行两次 IO，
    // 把首帧起播往后推。读取结果是 State，VideoPlayer 在首帧就绪时再消费。
    val savedSeekMs by produceState(0L, viewModel.videoId) {
        value = if (viewModel.videoId.isNotEmpty()) {
            viewModel.getSavedPlaybackPosition(viewModel.videoId)
        } else {
            0L
        }
    }

    LaunchedEffect(Unit) {
        val deps = ExoPlayerFactory.prepareVideoPlayerDeps(context)
        // Referer 由本层从配置派生后传入，ExoPlayerFactory 不读配置（保持为无依赖的工具 object）
        val referer = appPrefs.antiHotlinkReferer
        val player = ExoPlayerFactory.buildVideoPlayer(context, deps, referer).apply {
            // 读 StateFlow 缓存值，避免在首帧路径上再触发一次加密存储解密
            setPlaybackSpeed(appPrefs.playbackSpeedFlow.value)
        }
        // 连播切集时本页面会在构建期间离开组合：此时 exoPlayer 的 DisposableEffect 已随组合
        // 一同销毁，之后再赋值的播放器不会被任何人释放（含 CacheDataSource 持有的 SimpleCache
        // 引用、解码器与网络连接）。故先确认组合仍然有效，否则就地释放。
        if (!currentCoroutineContext().isActive) {
            player.release()
            return@LaunchedEffect
        }
        exoPlayer = player
    }

    LaunchedEffect(videoDetail?.defaultSourceUrl, exoPlayer) {
        val detail = videoDetail
        val player = exoPlayer
        val url = detail?.defaultSourceUrl
        if (player != null && !url.isNullOrEmpty()) {
            val preferredUrl = pickInitialSourceUrl(
                detail,
                appPrefs.preferredQualityFlow.value
            )
            // 有效续播点（>5s）由 VideoPlayer 在首帧就绪后执行跳转。
            // 这里只做赋值，不再 await 数据库：起播不必等待历史记录查询完成。
            initialSeekMs = savedSeekMs
            // 元数据供媒体通知 / 锁屏展示；VideoPlayer 换源与重试会复用同一套元数据
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(preferredUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(detail.title)
                            .setArtist(detail.author)
                            .apply {
                                if (detail.posterUrl.isNotBlank()) {
                                    setArtworkUri(Uri.parse(detail.posterUrl))
                                }
                            }
                            .build()
                    )
                    .build()
            )
            player.prepare()
        }
    }

    // 播放结束处理：保存进度；若开启连播则自动播放下一集（相关影片首条有效直链）
    fun handlePlaybackEnded() {
        val player = exoPlayer ?: return
        val pos = player.currentPosition
        if (pos > 0) {
            viewModel.savePlaybackProgress(pos, player.duration)
        }
        // 复用已收集的 StateFlow 值，避免同步读加密存储
        if (autoPlayNext) {
            val next = videoDetail?.relatedVideos?.firstOrNull { it.videoUrl.isNotBlank() }
            if (next != null) {
                onVideoClick(next.videoUrl)
            }
        }
    }

    // 进度记忆：每 5 秒检查一次，仅当「页面至少 STARTED 且播放位置确实前进」时落库，离场时保存最终进度。
    // 两个判据各管一半：位置不推进跳过暂停 / 缓冲，页面不在 STARTED 跳过切后台 / 熄屏。
    // 此处刻意用 LocalLifecycleOwner：离开本页即停落库正是期望行为；VideoPlayer 用 Activity 的 owner
    // 是为避免画中画被应用内导航误触发，两者需求相反，勿照搬。
    val lifecycleOwner = LocalLifecycleOwner.current
    val activePlayer = exoPlayer
    DisposableEffect(activePlayer, lifecycleOwner) {
        val player = activePlayer
        if (player == null) {
            // 播放器尚未就绪：无需订阅进度落库。此分支同时保证 DisposableEffect 一定有返回值。
            onDispose { }
        } else {
            val job = scope.launch {
                var lastSavedPosition = -1L
                while (true) {
                    delay(5000)
                    val position = player.currentPosition
                    val visible = lifecycleOwner.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                    if (visible && position > 0 && position != lastSavedPosition) {
                        viewModel.savePlaybackProgress(position, player.duration)
                        lastSavedPosition = position
                    }
                }
            }
            onDispose {
                job.cancel()
                val position = player.currentPosition
                val duration = player.duration
                if (position > 0) {
                    // 必须等这一帧组合提交后再落库：viewModelScope 的取消会在其上一条 launch 起始
                    // 分段（Dispatchers.IO）执行前生效，若在组合自身作用域里 suspend，离场保存会被直接取消。
                    // 等一帧也顺带错开 release()，不在其调用栈内触发任何挂起的数据库往返。
                    scope.launch {
                        withFrameNanos { }
                        viewModel.savePlaybackProgress(position, duration)
                    }
                }
                // 必须同步释放：之前用 mainHandler.post 延后一帧，一旦宿主 Activity 在
                // posted runnable 执行前销毁，或 release() 自身抛异常，播放器就永久泄漏
                // （连同解码器、Cache 数据源与在途网络连接）。ExoPlayer.release() 本身要求
                // 在其应用线程（此处为主线程）同步完成，开销也远小于一次页面转场。
                releasePlayerSafely(player)
            }
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
            commentVm.clearCommentLikeError()
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

    // 平板且非全屏、已加载内容时采用左右分栏（左播放器 / 右其余组件）
    val sizeInfo = currentWindowSizeInfo()
    // 启用条件：平板 + 非全屏 + 非「错误且未加载」致命态（回落到手机错误页）
    val useTabletUI = sizeInfo.isTablet && !isPlayerFullscreen
            && !(error != null && videoDetail == null)

    // 评论 tab 的懒加载判断
    val selectTab: (Int) -> Unit = { tab ->
        selectedTab = tab
        if (tab == 1 && !commentsLoaded && !isLoadingComments) {
            commentVm.loadComments()
        }
    }

    // 「其余组件」的状态与回调包，供 detailRestItems 使用
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
        onLoadComments = { force -> commentVm.loadComments(force = force) },
        onLoadReplies = { commentVm.loadReplies(it) },
        onToggleReplies = { commentVm.toggleReplies(it) },
        onPostComment = { text ->
                commentVm.postComment(text, commentCtx()) { newCount ->
                    // 评论数变化回到视频域，保持 VideoDetail.commentCount 准确
                    viewModel.onCommentCountUpdated(newCount)
                }
            },
        onClearPostCommentError = { commentVm.clearPostCommentError() },
        onToggleCommentLike = { commentVm.toggleCommentLike(it, commentCtx()) },
        onStartReply = { commentId, replyToUsername -> commentVm.startReply(commentId, replyToUsername) },
        onSubmitReply = { text -> commentVm.submitReply(text, commentCtx()) },
        onCancelReply = { commentVm.cancelReply() },
        onClearReplyError = { commentVm.clearReplyError() },
        onNavigateToLogin = tryNavigateToLogin
    )

    if (useTabletUI) {
        if (isLoading) {
            // 平板加载骨架
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
                    val player = exoPlayer
                    if (detail == null || detail.defaultSourceUrl.isEmpty()) {
                        VideoUnavailableHint(
                            hasError = error != null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (player == null) {
                        // 播放器仍在后台构建：保持黑底占位，不能走「无法播放」分支否则会误报
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        DetailVideoPlayer(
                            exoPlayer = player,
                            detail = detail,
                            initialPositionMs = initialSeekMs,
                            isFullscreen = false,
                            onFullscreenToggle = { full -> isPlayerFullscreen = full },
                            onPlaybackEnded = { handlePlaybackEnded() },
                            autoPlayNext = autoPlayNext,
                            // 平板常态横持，自动全屏会误触发，此处仅保留按钮手动进入全屏
                            autoFullscreenEnabled = false,
                            modifier = Modifier
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
                        val player = exoPlayer
                        if (detail == null || detail.defaultSourceUrl.isEmpty()) {
                            VideoUnavailableHint(
                                hasError = error != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(225.dp)
                                    .background(Color.Black)
                            )
                        } else if (player == null) {
                            // 播放器仍在后台构建：黑底占位与正式播放器同高，避免布局跳动
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(225.dp)
                                    .background(Color.Black)
                            )
                        } else {
                            DetailVideoPlayer(
                                exoPlayer = player,
                                detail = detail,
                                initialPositionMs = initialSeekMs,
                                isFullscreen = isPlayerFullscreen,
                                onFullscreenToggle = { full -> isPlayerFullscreen = full },
                                onPlaybackEnded = { handlePlaybackEnded() },
                                autoPlayNext = autoPlayNext,
                                modifier = if (isPlayerFullscreen) Modifier.fillParentMaxSize() else Modifier
                            )
                        }
                    }

                    videoDetail?.takeIf { !isPlayerFullscreen }?.let { detailRestItems(it, restState, restActions) }

                }
            }
        }
    }

    // 避免在点击回调里同步等待磁盘操作而阻塞主线程。
    LaunchedEffect(Unit) {
        viewModel.downloadStartResults.collect { result ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    // 区分「已加入 / 已在下载 / 已下载 / 地址无效」
                    message = context.getString(downloadResultMessage(result)),
                    duration = SnackbarDuration.Short
                )
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
            },
            onDismiss = { showDownloadDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onBackground,
                shape = RoundedCornerShape(8.dp)
            )
        }
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

/** 把 DownloadRepository.startDownload 的结果码映射为提示文案。 */
private fun downloadResultMessage(result: Int): Int = when (result) {
    DownloadRepository.RESULT_INVALID_URL -> R.string.detail_download_invalid_url
    DownloadRepository.RESULT_ALREADY_ACTIVE -> R.string.detail_download_in_progress
    DownloadRepository.RESULT_ALREADY_COMPLETED -> R.string.detail_download_completed
    else -> R.string.detail_download_added
}


/**
 * 安全释放播放器：先移除监听再 release，任何异常只记录不外抛。
 *
 * 不带上报错直接 release 时，Media3 在某些状态下（如 prepare 过程中被释放）会从回调链上抛出；
 * 这里的清理动作发生在页面销毁的 onDispose 中，异常逃逸只会炸掉组合器，且让后续播放器也得不到释放。
 */
internal fun releasePlayerSafely(player: ExoPlayer) {
    runCatching {
        player.clearVideoSurface()
        player.release()
    }.onFailure { e ->
        AppLogger.logError("DetailScreen", "释放播放器失败: " + e.message, e)
    }
}
