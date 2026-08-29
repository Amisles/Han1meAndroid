package app.amisles.hanime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.core.ui.R as CoreR
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import app.amisles.hanime.ui.viewmodel.PlaylistDetailViewModel
import coil3.compose.AsyncImage

@Composable
fun PlaylistDetailScreen(
    url: String = "",
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {}
) {
    val viewModel: PlaylistDetailViewModel = hiltViewModel()
    val playlistDetail by viewModel.playlistDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            viewModel.loadPlaylistDetail(url)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(HanimeBackground).statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBackClick() }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreR.string.common_back),
                    tint = HanimeTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HanimePrimary)
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = error ?: stringResource(CoreR.string.common_load_failed),
                    color = HanimeTextSecondary
                )
            }
        } else if (playlistDetail != null) {
            PlaylistDetailContent(
                detail = playlistDetail!!,
                onVideoClick = onVideoClick
            )
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    detail: app.amisles.hanime.domain.model.PlaylistDetail,
    onVideoClick: (String) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().background(HanimeCard).padding(15.dp)
            ) {
                if (detail.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = detail.coverUrl,
                        contentDescription = stringResource(CoreR.string.common_cover),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = detail.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (detail.authorAvatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = detail.authorAvatarUrl,
                            contentDescription = stringResource(CoreR.string.common_author_avatar),
                            modifier = Modifier.size(20.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = detail.author, fontSize = 13.sp, color = HanimePrimary)
                    Text(
                        text = " · " + stringResource(
                            CoreR.string.common_playlist_videos_count,
                            detail.videoCount
                        ),
                        fontSize = 12.sp,
                        color = HanimeTextSecondary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                if (detail.description.isNotEmpty()) {
                    Text(
                        text = detail.description,
                        fontSize = 12.sp,
                        color = HanimeTextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (detail.videos.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(CoreR.string.playlist_videos),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)
                )
            }
            items(
                items = detail.videos,
                key = { it.id }
            ) { video ->
                PlaylistVideoItem(
                    video = video,
                    onClick = { onVideoClick(video.videoUrl) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun PlaylistVideoItem(
    video: HanimeVideo,
    onClick: () -> Unit = {}
) {
    val gradient = gradients.getOrElse(video.id.hashCode() % gradients.size) { gradients[0] }
    val emoji = emojis.getOrElse(video.id.hashCode() % emojis.size) { emojis[0] }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 4.dp)
            .background(HanimeCard, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        app.amisles.hanime.core.ui.components.VideoThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            emoji = emoji,
            gradient = gradient,
            duration = video.duration,
            likeRate = video.likeRate,
            viewCount = video.viewCount,
            crop = true,
            modifier = Modifier.width(120.dp).height(68.dp).clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HanimeTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.publishTime.isNotEmpty()) {
                Text(
                    text = video.publishTime,
                    fontSize = 10.sp,
                    color = HanimeTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
