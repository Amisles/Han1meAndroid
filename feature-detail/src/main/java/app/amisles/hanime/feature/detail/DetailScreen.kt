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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.feature.detail.components.VideoPlayer
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeBorder
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimePrimaryLight
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import app.amisles.hanime.data.preferences.Preferences
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    videoUrl: String? = null,
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onAuthorPageClick: (String) -> Unit = {}
) {
    val viewModel: DetailViewModel = hiltViewModel()
    val context = LocalContext.current
    val videoDetail by viewModel.videoDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadQualities by viewModel.downloadQualities.collectAsState()
    val isLoadingQualities by viewModel.isLoadingQualities.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val commentsError by viewModel.commentsError.collectAsState()
    val commentsLoaded by viewModel.commentsLoaded.collectAsState()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    // 0 = 相关影片，1 = 评论
    var selectedTab by remember { mutableStateOf(0) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            volume = 1f
        }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isPlayerFullscreen) Modifier.statusBarsPadding() else Modifier)
            .background(if (isPlayerFullscreen) Color.Black else HanimeBackground)
    ) {
        if (!isPlayerFullscreen) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HanimeBackground)
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onBackClick() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = HanimeTextPrimary,
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
            if (videoDetail != null && videoDetail!!.defaultSourceUrl.isNotEmpty()) {
                VideoPlayer(
                    exoPlayer = exoPlayer,
                    posterUrl = videoDetail!!.posterUrl,
                    videoSources = videoDetail!!.videoSources,
                    initialSourceUrl = videoDetail!!.defaultSourceUrl,
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
                        color = HanimeTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (!isPlayerFullscreen && videoDetail != null) {
            val detail = videoDetail!!

            item {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text(
                        text = detail.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HanimeTextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (detail.author.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
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
                                color = HanimePrimary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        if (detail.releaseDate.isNotEmpty()) {
                            Text(
                                text = "${stringResource(R.string.detail_release_date)}: ${detail.releaseDate}",
                                fontSize = 12.sp,
                                color = HanimeTextSecondary
                            )
                        }
                        if (detail.fileSize.isNotEmpty()) {
                            Text(
                                text = "${stringResource(R.string.detail_file_size)}: ${detail.fileSize}",
                                fontSize = 12.sp,
                                color = HanimeTextSecondary
                            )
                        }
                    }

                    if (detail.description.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 15.dp)
                                .clickable { showDescription = !showDescription },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showDescription) stringResource(R.string.detail_collapse) else stringResource(R.string.detail_expand),
                                fontSize = 13.sp,
                                color = HanimePrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showDescription) "▲" else "▼",
                                fontSize = 10.sp,
                                color = HanimePrimary
                            )
                        }

                        if (showDescription) {
                            Text(
                                text = detail.description,
                                fontSize = 13.sp,
                                color = HanimeTextSecondary,
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(HanimeCard, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                                    .padding(bottom = 15.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(HanimeCard, RoundedCornerShape(6.dp))
                                .border(1.dp, HanimeBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                                .clickable {
                                    showDownloadDialog = true
                                    viewModel.loadDownloadQualities()
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "下载",
                                modifier = Modifier.size(16.dp),
                                tint = HanimeTextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.detail_download),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = HanimeTextPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(HanimeCard, RoundedCornerShape(6.dp))
                                .border(1.dp, HanimeBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                                .clickable { viewModel.toggleFavorite() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "收藏",
                                modifier = Modifier.size(16.dp),
                                tint = if (isFavorite) HanimePrimary else HanimeTextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFavorite) stringResource(R.string.detail_unfavorite) else stringResource(R.string.detail_favorite),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isFavorite) HanimePrimary else HanimeTextPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(HanimeCard, RoundedCornerShape(6.dp))
                                .border(1.dp, HanimeBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                                .clickable {
                                    shareVideo(context, detail.title, videoUrl.orEmpty())
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "分享",
                                modifier = Modifier.size(16.dp),
                                tint = HanimeTextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.detail_share),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = HanimeTextPrimary
                            )
                        }
                    }

                    if (detail.tags.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.detail_tags),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HanimeTextPrimary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
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
                                    color = HanimeTextPrimary,
                                    modifier = Modifier
                                        .background(HanimeCard)
                                        .border(1.dp, HanimeBorder, RoundedCornerShape(4.dp))
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
                            .padding(horizontal = 15.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HanimeCard, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.detail_playlist),
                                        fontSize = 11.sp,
                                        color = Color.Black,
                                        modifier = Modifier
                                            .background(HanimePrimaryLight, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = playlist.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HanimeTextPrimary,
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
                                        color = HanimePrimary,
                                        modifier = Modifier.clickable { onAuthorClick(playlist.author) }
                                    )
                                    Text(
                                        text = " · " + stringResource(R.string.detail_playlist_count, playlist.videoCount),
                                        fontSize = 12.sp,
                                        color = HanimeTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
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
                                    color = HanimeTextPrimary,
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
                            .padding(horizontal = 15.dp, vertical = 8.dp),
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
                                .padding(horizontal = 15.dp, vertical = 2.dp)
                                .background(HanimeCard)
                                .padding(vertical = 4.dp, horizontal = 6.dp)
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
                                    color = HanimeTextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (video.author.isNotEmpty()) {
                                    Text(
                                        text = video.author,
                                        fontSize = 10.sp,
                                        color = HanimePrimary,
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
                                        color = HanimeTextSecondary
                                    )
                                    Text(
                                        text = video.likeRate,
                                        fontSize = 9.sp,
                                        color = HanimeTextSecondary
                                    )
                                    Text(
                                        text = video.viewCount,
                                        fontSize = 9.sp,
                                        color = HanimeTextSecondary
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
                            onRetry = { viewModel.loadComments(force = true) }
                        )
                    }
                }
            }
        }
        }

        if (!isPlayerFullscreen && !isLoading) {
            item {
                Spacer(modifier = Modifier.height(80.dp))
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
                    .background(HanimeCard, RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.detail_select_quality),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isLoadingQualities) {
                    CircularProgressIndicator(
                        color = HanimePrimary,
                        modifier = Modifier.padding(20.dp)
                    )
                } else if (downloadQualities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_no_download),
                        color = HanimeTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    downloadQualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(HanimeBackground, RoundedCornerShape(8.dp))
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
                                    color = HanimeTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                if (quality.fileSize.isNotEmpty() && quality.fileSize != "N/A") {
                                    Text(
                                        text = quality.fileSize,
                                        fontSize = 12.sp,
                                        color = HanimeTextSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.detail_download),
                                fontSize = 13.sp,
                                color = HanimePrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.common_cancel),
                    fontSize = 14.sp,
                    color = HanimeTextSecondary,
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
            containerColor = HanimeCard,
            contentColor = HanimeTextPrimary,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

private fun shareVideo(context: Context, title: String, url: String) {
    Preferences.incrementShareCount()
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
            .background(if (isSelected) HanimePrimary else HanimeCard)
            .border(
                width = 1.dp,
                color = if (isSelected) HanimePrimary else HanimeBorder,
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
            color = if (isSelected) Color.White else HanimeTextPrimary
        )
    }
}

/**
 * 评论区：包含加载中、错误、空、列表四种状态
 */
@Composable
private fun CommentSection(
    comments: List<app.amisles.hanime.domain.model.Comment>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    when {
        isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = HanimePrimary,
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
                    color = HanimeTextSecondary
                )
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                comments.forEach { comment ->
                    CommentItem(comment = comment)
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .padding(horizontal = 15.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 单条评论
 */
@Composable
private fun CommentItem(comment: app.amisles.hanime.domain.model.Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
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
                    color = HanimeTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = comment.time,
                    fontSize = 11.sp,
                    color = HanimeTextSecondary
                )
            }

            Text(
                text = comment.content,
                fontSize = 14.sp,
                color = HanimeTextPrimary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = HanimeTextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (comment.likeCount > 0) comment.likeCount.toString() else "0",
                        fontSize = 12.sp,
                        color = HanimeTextSecondary
                    )
                }
                if (comment.replyCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = HanimeTextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.comment_reply_count, comment.replyCount),
                            fontSize = 12.sp,
                            color = HanimeTextSecondary
                        )
                    }
                }
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
            .background(HanimeBackground)
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
                    .background(HanimeCard)
                    .alpha(alpha)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HanimeCard)
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
                        .background(HanimeCard)
                        .alpha(alpha)
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HanimeCard)
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
                        .background(HanimeCard)
                        .alpha(alpha)
                )
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(HanimeCard)
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
                            .background(HanimeCard)
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
                            .background(HanimeCard)
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
                .background(HanimeCard)
                .alpha(alpha)
        )

        // 相关推荐视频骨架（3 条）
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HanimeCard)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 90.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HanimeBackground)
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
                            .background(HanimeBackground)
                            .alpha(alpha)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HanimeBackground)
                            .alpha(alpha)
                    )
                }
            }
        }
    }
}