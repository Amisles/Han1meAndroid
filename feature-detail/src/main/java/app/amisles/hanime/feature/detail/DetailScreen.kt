package app.amisles.hanime.feature.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import app.amisles.hanime.core.ui.R
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
    val viewModel: DetailViewModel = viewModel()
    val context = LocalContext.current
    val videoDetail by viewModel.videoDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadQualities by viewModel.downloadQualities.collectAsState()
    val isLoadingQualities by viewModel.isLoadingQualities.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(Unit) {
        viewModel.initDownloadManager(context)
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl != null && videoUrl.isNotEmpty()) {
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

        item(key = "video_player") {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HanimePrimary)
                }
            } else if (videoDetail != null && videoDetail!!.defaultSourceUrl.isNotEmpty()) {
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

            if (detail.relatedVideos.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.detail_related),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HanimeTextPrimary,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp)
                    )
                }

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
            }
        }

        if (!isPlayerFullscreen) {
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