package app.amisles.hanime.feature.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.LoginUnsupportedDialog
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.feature.detail.components.VideoPlayer
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.domain.model.VideoDetail

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
    val isLogin by app.amisles.hanime.data.preferences.Preferences.loginStateFlow.collectAsStateWithLifecycle()
    val isLoginSupported by app.amisles.hanime.data.preferences.Preferences.loginSupportedFlow.collectAsStateWithLifecycle()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    // 0 = 相关影片，1 = 评论
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

    val exoPlayer = remember {
        ExoPlayerFactory.buildVideoPlayer(context)
    }

    LaunchedEffect(videoDetail?.defaultSourceUrl) {
        val url = videoDetail?.defaultSourceUrl
        if (!url.isNullOrEmpty()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exoPlayer.prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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

    // 订阅作者出错时弹出提示（错误文案在合成作用域内本地化，再传入 LaunchedEffect）
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

    // 平板分栏以根层级全宽渲染，去除 ResponsiveContent 限宽产生的左右空隙；手机/加载/错误态仍由下方 ResponsiveContent 包裹
        val sizeInfo = currentWindowSizeInfo()
        // 平板且非全屏、已加载内容（非加载中/非错误空态）时采用左右分栏：
        // 左侧仅视频播放器，右侧为标题/作者/简介/操作/评论/相关视频等其余组件。
        // 必须保证 videoDetail 已加载且非错误态（下方各处均已改用 ?.let/?.takeIf 安全调用，不再使用 !!，避免首次组合或数据为空时的 NPE）
        // 平板 UI（含加载期骨架）：非全屏 + 非致命错误态（error!=null && videoDetail==null 回落到手机错误页）即启用
        val useTabletUI = sizeInfo.isTablet && !isPlayerFullscreen
            && !(error != null && videoDetail == null)

        // 其余组件抽成 LazyListScope 扩展，手机单列与平板右栏复用。
        val detailRestItems: LazyListScope.(VideoDetail) -> Unit = { detail ->

            item {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = detail.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (detail.author.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            // 左侧：头像 + 作者名（点击进入作者页）
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (detail.authorPageUrl.isNotEmpty()) {
                                            onAuthorPageClick(detail.authorPageUrl)
                                        } else {
                                            onAuthorClick(detail.author)
                                        }
                                    }
                            ) {
                                if (detail.authorAvatarUrl.isNotEmpty()) {
                                    coil3.compose.AsyncImage(
                                        model = detail.authorAvatarUrl,
                                        contentDescription = "作者头像",
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Text(
                                    text = detail.author,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // 右侧：订阅按钮（作者 ID 可解析时展示）
                            if (detail.subscribeArtistId.isNotEmpty()) {
                                SubscribeButton(
                                    isSubscribed = isSubscribed,
                                    isSubscribing = isSubscribing,
                                    onClick = { viewModel.toggleSubscribe() }
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        if (detail.releaseDate.isNotEmpty()) {
                            Text(
                                text = "${stringResource(R.string.detail_release_date)}: ${detail.releaseDate}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (detail.fileSize.isNotEmpty()) {
                            Text(
                                text = "${stringResource(R.string.detail_file_size)}: ${detail.fileSize}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (detail.description.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clickable { showDescription = !showDescription },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showDescription) stringResource(R.string.detail_collapse) else stringResource(R.string.detail_expand),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showDescription) "▲" else "▼",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (showDescription) {
                            Text(
                                text = detail.description,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .padding(bottom = 15.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        val favoriteTint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        DetailActionButton(
                            icon = Icons.Default.Download,
                            text = stringResource(R.string.detail_download),
                            onClick = {
                                showDownloadDialog = true
                                viewModel.loadDownloadQualities()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        DetailActionButton(
                            icon = Icons.Default.Favorite,
                            text = stringResource(if (isFavorite) R.string.detail_unfavorite else R.string.detail_favorite),
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.weight(1f),
                            tint = favoriteTint
                        )
                        DetailActionButton(
                            icon = Icons.Default.Share,
                            text = stringResource(R.string.detail_share),
                            onClick = { shareVideo(context, detail.title, videoUrl.orEmpty()) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (detail.tags.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.detail_tags),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            items(detail.tags) { tag ->
                                val cleanedTag = remember(tag) {
                                    tag
                                        .trimStart('#')
                                        .trim()
                                        .replace(Regex("[\\(（][\\d+]+[\\)）]$"), "")
                                        .trim()
                                }
                                Text(
                                    text = tag,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .clickable { onTagClick(cleanedTag) }
                                )
                            }
                        }
                    }
                }
            }

            val playlist = detail.playlist
            if (playlist != null && playlist.videos.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.detail_playlist),
                                        fontSize = 11.sp,
                                        color = Color.Black,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = playlist.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = playlist.author,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onAuthorClick(playlist.author) }
                                    )
                                    Text(
                                        text = " · " + stringResource(R.string.detail_playlist_count, playlist.videoCount),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlist.videos) { video ->
                            val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
                            val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }
                            
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { onVideoClick(video.videoUrl) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(6.dp))
                                ) {
                                    app.amisles.hanime.core.ui.components.VideoThumbnail(
                                        thumbnailUrl = video.thumbnailUrl,
                                        emoji = emoji,
                                        gradient = gradient,
                                        duration = video.duration,
                                        likeRate = video.likeRate,
                                        viewCount = video.viewCount,
                                        crop = true
                                    )
                                }
                                Text(
                                    text = video.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (detail.relatedVideos.isNotEmpty() || commentsLoaded || isLoadingComments) {
                item(key = "tab_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CommentTabButton(
                            text = stringResource(R.string.detail_tab_related),
                            isSelected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        CommentTabButton(
                            text = stringResource(R.string.detail_tab_comments),
                            isSelected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                if (!commentsLoaded && !isLoadingComments) {
                                    viewModel.loadComments()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedTab == 0) {
                    items(detail.relatedVideos) { video ->
                        val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
                        val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onVideoClick(video.videoUrl) },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.amisles.hanime.core.ui.components.VideoThumbnail(
                                thumbnailUrl = video.thumbnailUrl,
                                emoji = emoji,
                                gradient = gradient,
                                duration = "",
                                likeRate = "",
                                viewCount = "",
                                crop = true,
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = video.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (video.author.isNotEmpty()) {
                                    Text(
                                        text = video.author,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clickable { onAuthorClick(video.author) }
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = video.duration,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = video.likeRate,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = video.viewCount,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "comments_section") {
                        CommentSection(
                            comments = comments,
                            isLoading = isLoadingComments,
                            error = commentsError,
                            onRetry = { viewModel.loadComments(force = true) },
                            repliesCache = repliesCache,
                            loadingReplies = loadingReplies,
                            repliesError = repliesError,
                            onLoadReplies = { commentId -> viewModel.loadReplies(commentId) },
                            expandedReplies = expandedReplies,
                            onToggleExpand = { commentId -> viewModel.toggleReplies(commentId) },
                            isLogin = isLogin,
                            isPostingComment = isPostingComment,
                            postCommentError = postCommentError,
                            onPostComment = { text ->
                                viewModel.postComment(text)
                            },
                            onClearPostError = { viewModel.clearPostCommentError() },
                            onToggleLike = { viewModel.toggleCommentLike(it) },
                            likingComments = likingComments,
                            activeReplyCommentId = activeReplyTarget?.commentId,
                            replyPrefill = activeReplyTarget?.replyToUsername?.let { "@$it " } ?: "",
                            isPostingReply = isPostingReply,
                            replyError = replyError,
                            onStartReply = { commentId, replyToUsername -> viewModel.startReply(commentId, replyToUsername) },
                            onSendReply = { text -> viewModel.submitReply(text) },
                            onCancelReply = { viewModel.cancelReply() },
                            onClearReplyError = { viewModel.clearReplyError() },
                            onNavigateToLogin = tryNavigateToLogin
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
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
                            VideoPlayer(
                                exoPlayer = exoPlayer,
                                posterUrl = detail.posterUrl,
                                videoSources = detail.videoSources,
                                initialSourceUrl = detail.defaultSourceUrl,
                                preloadUrl = detail.relatedVideos.firstOrNull { it.videoUrl.isNotBlank() }?.videoUrl ?: "",
                                isFullscreen = false,
                                onFullscreenToggle = { full -> isPlayerFullscreen = full },
                                modifier = Modifier
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (error != null) stringResource(R.string.detail_load_failed) else "暂无视频",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        // 返回按钮覆盖在播放器左上角
                        IconButton(
                            onClick = { onBackClick() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(48.dp)
                                .padding(start = 4.dp, top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 右侧：其余组件（可滚动）
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        videoDetail?.let { detailRestItems(it) }
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
                    IconButton(
                        onClick = { onBackClick() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                VideoPlayer(
                    exoPlayer = exoPlayer,
                    posterUrl = detail.posterUrl,
                    videoSources = detail.videoSources,
                    initialSourceUrl = detail.defaultSourceUrl,
                    preloadUrl = detail.relatedVideos.firstOrNull { it.videoUrl.isNotBlank() }?.videoUrl ?: "",
                    isFullscreen = isPlayerFullscreen,
                    onFullscreenToggle = { full -> isPlayerFullscreen = full },
                    modifier = if (isPlayerFullscreen) Modifier.fillParentMaxSize() else Modifier
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (error != null) stringResource(R.string.detail_load_failed) else "暂无视频",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }

        videoDetail?.takeIf { !isPlayerFullscreen }?.let { detailRestItems(it) }

        }
        }
        }
        }

    if (showDownloadDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showDownloadDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.detail_select_quality),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isLoadingQualities) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(20.dp)
                    )
                } else if (downloadQualities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_no_download),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    downloadQualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.startDownload(quality)
                                    showDownloadDialog = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.detail_download_added),
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = quality.quality,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium
                                )
                                if (quality.fileSize.isNotEmpty() && quality.fileSize != "N/A") {
                                    Text(
                                        text = quality.fileSize,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.detail_download),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.common_cancel),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { showDownloadDialog = false }
                        .padding(8.dp)
                )
            }
        }
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

/**
 * 详情页操作按钮：图标在上、文字在下，避免长文本（英文 Unfavorite / 日语お気に入り解除等）换行。
 */
@Composable
private fun DetailActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 订阅作者按钮：横向胶囊样式（图标 + 文字），置于作者名右侧。
 * - 未订阅：实心 primary 胶囊 + 订阅
 * - 已订阅：surfaceVariant 胶囊 + 已订阅
 * - 请求中：显示环形进度，禁止点击
 */
@Composable
private fun SubscribeButton(
    isSubscribed: Boolean,
    isSubscribing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSubscribed) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isSubscribed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(enabled = !isSubscribing, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSubscribing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isSubscribed) Icons.Default.Person else Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
                Text(
                    text = stringResource(if (isSubscribed) R.string.detail_subscribed else R.string.detail_subscribe),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

private fun shareVideo(context: Context, title: String, url: String) {
    val shareText = if (url.isNotEmpty()) "$title\n$url" else title
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_TITLE, title)
    }
    val chooser = Intent.createChooser(intent, "分享视频")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/**
 * 评论/相关影片 Tab 按钮
 */
@Composable
private fun CommentTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * 评论区：包含输入框、加载中、错误、空、列表五种状态
 */
@Composable
private fun CommentSection(
    comments: List<app.amisles.hanime.domain.model.Comment>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    repliesCache: Map<String, List<app.amisles.hanime.domain.model.Reply>>,
    loadingReplies: Set<String>,
    repliesError: Map<String, String?>,
    onLoadReplies: (String) -> Unit,
    expandedReplies: Set<String>,
    onToggleExpand: (String) -> Unit,
    isLogin: Boolean,
    isPostingComment: Boolean,
    postCommentError: String?,
    onPostComment: (String) -> Unit,
    onClearPostError: () -> Unit,
    onToggleLike: (app.amisles.hanime.domain.model.Comment) -> Unit,
    likingComments: Set<String>,
    activeReplyCommentId: String?,
    replyPrefill: String,
    isPostingReply: Boolean,
    replyError: String?,
    onStartReply: (String, String?) -> Unit,
    onSendReply: (String) -> Unit,
    onCancelReply: () -> Unit,
    onClearReplyError: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 评论输入框
        CommentInputBar(
            isLogin = isLogin,
            isPosting = isPostingComment,
            error = postCommentError,
            onPost = onPostComment,
            onClearError = onClearPostError,
            onNavigateToLogin = onNavigateToLogin
        )

        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            error != null -> {
                KaomojiErrorView(
                    message = error,
                    onRetry = onRetry
                )
            }
            comments.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.comment_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                comments.forEach { comment ->
                    CommentItem(
                        comment = comment,
                        replies = repliesCache[comment.id],
                        isLoadingReplies = loadingReplies.contains(comment.id),
                        repliesError = repliesError[comment.id],
                        onLoadReplies = onLoadReplies,
                        isExpanded = expandedReplies.contains(comment.id),
                        onToggleExpand = { onToggleExpand(comment.id) },
                        onToggleLike = onToggleLike,
                        isLiking = likingComments.contains(comment.id),
                        isLogin = isLogin,
                        onNavigateToLogin = onNavigateToLogin,
                        onReply = { replyToUsername -> onStartReply(comment.id, replyToUsername) },
                        isReplying = activeReplyCommentId == comment.id,
                        replyPrefill = replyPrefill,
                        isPostingReply = isPostingReply,
                        replyError = replyError,
                        onSendReply = onSendReply,
                        onCancelReply = onCancelReply,
                        onClearReplyError = onClearReplyError
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .padding(horizontal = 15.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 评论输入栏：未登录时提示点击登录；已登录时显示输入框和发送按钮。
 */
@Composable
private fun CommentInputBar(
    isLogin: Boolean,
    isPosting: Boolean,
    error: String?,
    onPost: (String) -> Unit,
    onClearError: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp)
    ) {
        if (!isLogin) {
            // 未登录：点击跳转登录页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onNavigateToLogin() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.comment_login_required),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 已登录：输入框 + 发送按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        if (error != null) onClearError()
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.comment_input_hint),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isPosting,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isPosting) {
                            onPost(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isPosting && inputText.isNotBlank()
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.comment_post_button),
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 错误提示
        if (error != null) {
            Text(
                text = error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

/**
 * 内联回复输入框：缩进对齐到回复列表，支持 @用户名 预填、发送、取消与错误提示。
 */
@Composable
private fun ReplyInputBar(
    prefill: String,
    isPosting: Boolean,
    error: String?,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit
) {
    var inputText by remember(prefill) { mutableStateOf(prefill) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    if (error != null) onClearError()
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.comment_reply_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                singleLine = false,
                maxLines = 3,
                enabled = !isPosting,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isPosting) {
                        onSend(inputText)
                        inputText = ""
                    }
                },
                enabled = !isPosting && inputText.isNotBlank()
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.comment_post_button),
                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 取消按钮 + 错误提示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.common_cancel),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isPosting) { onCancel() }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * 单条评论（含展开/收起回复）
 */
@Composable
private fun CommentItem(
    comment: app.amisles.hanime.domain.model.Comment,
    replies: List<app.amisles.hanime.domain.model.Reply>?,
    isLoadingReplies: Boolean,
    repliesError: String?,
    onLoadReplies: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleLike: (app.amisles.hanime.domain.model.Comment) -> Unit,
    isLiking: Boolean,
    isLogin: Boolean,
    onNavigateToLogin: () -> Unit,
    onReply: (String?) -> Unit,
    isReplying: Boolean,
    replyPrefill: String,
    isPostingReply: Boolean,
    replyError: String?,
    onSendReply: (String) -> Unit,
    onCancelReply: () -> Unit,
    onClearReplyError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            coil3.compose.AsyncImage(
                model = comment.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = comment.username,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = comment.time,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = comment.content,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val liked = comment.likeStatus == 1
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !isLiking) { onToggleLike(comment) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = stringResource(R.string.comment_like),
                            modifier = Modifier.size(14.dp),
                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = comment.likeCount.toString(),
                            fontSize = 12.sp,
                            color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 回复按钮
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                if (isLogin) onReply(null) else onNavigateToLogin()
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.comment_reply),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 展开/收起回复按钮（放在点赞同一行，红色主题色）
                    if (comment.replyCount > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onToggleExpand()
                                    if (!isExpanded && replies == null && !isLoadingReplies) {
                                        onLoadReplies(comment.id)
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isExpanded) {
                                    stringResource(R.string.comment_hide_replies)
                                } else {
                                    stringResource(R.string.comment_view_replies, comment.replyCount)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 内联回复输入框（仅在该评论的回复目标激活时显示）
                if (isReplying) {
                    ReplyInputBar(
                        prefill = replyPrefill,
                        isPosting = isPostingReply,
                        error = replyError,
                        onSend = onSendReply,
                        onCancel = onCancelReply,
                        onClearError = onClearReplyError
                    )
                }
            }
        }

        // 展开时显示回复列表
        if (isExpanded && comment.replyCount > 0) {
            ReplyList(
                replies = replies,
                isLoading = isLoadingReplies,
                error = repliesError,
                onRetry = { onLoadReplies(comment.id) },
                onReplyToReply = { username -> onReply(username) },
                isLogin = isLogin,
                onNavigateToLogin = onNavigateToLogin
            )
        }
    }
}

/**
 * 回复列表：加载中、错误、列表三态
 */
@Composable
private fun ReplyList(
    replies: List<app.amisles.hanime.domain.model.Reply>?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onReplyToReply: (String) -> Unit,
    isLogin: Boolean,
    onNavigateToLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, top = 8.dp)
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.comment_reply_load_failed),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.common_retry),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onRetry)
                    )
                }
            }
            replies != null -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    replies.forEachIndexed { index, reply ->
                        ReplyItem(
                            reply = reply,
                            onReply = {
                                if (isLogin) onReplyToReply(reply.username) else onNavigateToLogin()
                            }
                        )
                        if (index < replies.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(Color.White.copy(alpha = 0.06f))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条回复
 */
@Composable
private fun ReplyItem(
    reply: app.amisles.hanime.domain.model.Reply,
    onReply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        coil3.compose.AsyncImage(
            model = reply.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = reply.username,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = reply.time,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 如果是回复其他回复，显示 "回复 @用户名" 前缀
            val replyToUser = reply.replyTo
            if (replyToUser != null) {
                Text(
                    text = stringResource(R.string.comment_reply_to, replyToUser),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = reply.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (reply.likeCount > 0) reply.likeCount.toString() else "0",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 回复这条回复
                Text(
                    text = stringResource(R.string.comment_reply),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onReply)
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * 详情页骨架屏：加载时模拟详情页布局的占位
 */
@Composable
private fun DetailSkeletonScreen() {
    val transition = rememberInfiniteTransition(label = "detail-skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detail-skeleton-alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 播放器区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        )

        Column(modifier = Modifier.padding(15.dp)) {
            // 标题
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 作者行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(alpha)
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(alpha)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 元信息行
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(alpha)
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .alpha(alpha)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 操作按钮行
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .alpha(alpha)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 标签行骨架
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .alpha(alpha)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 相关推荐标题骨架
        Box(
            modifier = Modifier
                .padding(horizontal = 15.dp, vertical = 4.dp)
                .width(100.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .alpha(alpha)
        )

        // 相关推荐视频骨架（3 条）
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 90.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .alpha(alpha)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .alpha(alpha)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .alpha(alpha)
                    )
                }
            }
        }
    }
}

/**
 * 平板分栏骨架屏：左侧 3/4 视频区占位（垂直居中），右侧 1/4 详情占位。
 * 用于平板设备进入详情页的加载期，区别于手机样式的单列骨架。
 */
@Composable
private fun TabletDetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "tablet-detail-skeleton-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tablet-detail-skeleton-alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 左侧：视频区占位（垂直居中）
        Box(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
            )
        }

        // 右侧：详情占位
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                Column(modifier = Modifier.padding(15.dp)) {
                    // 标题
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .alpha(alpha)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .alpha(alpha)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 作者行
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .alpha(alpha)
                        )
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .alpha(alpha)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 元信息行
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .alpha(alpha)
                        )
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .alpha(alpha)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 操作按钮行
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .alpha(alpha)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 标签行骨架
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .alpha(alpha)
                            )
                        }
                    }
                }
            }
        }
    }
}