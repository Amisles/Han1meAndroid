package app.amisles.hanime.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import coil3.compose.AsyncImage

@Composable
fun AuthorScreen(
    authorPageUrl: String = "",
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onViewAllVideos: (String) -> Unit = {},
    onViewAllPlaylists: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {}
) {
    val viewModel: AuthorViewModel = hiltViewModel()
    val authorData by viewModel.authorData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(authorPageUrl) {
        if (authorPageUrl.isNotEmpty()) {
            viewModel.loadAuthorPage(authorPageUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBackClick() }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
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
                Text(text = error ?: stringResource(R.string.author_load_failed), color = HanimeTextSecondary)
            }
        } else if (authorData != null) {
            AuthorContent(
                authorData = authorData!!,
                onVideoClick = onVideoClick,
                onViewAllVideos = onViewAllVideos,
                onViewAllPlaylists = onViewAllPlaylists,
                onPlaylistClick = onPlaylistClick
            )
        }
    }
}

@Composable
private fun AuthorContent(
    authorData: app.amisles.hanime.domain.model.AuthorPageData,
    onVideoClick: (String) -> Unit = {},
    onViewAllVideos: (String) -> Unit = {},
    onViewAllPlaylists: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HanimeCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = authorData.authorAvatarUrl,
                    contentDescription = "作者头像",
                    modifier = Modifier.size(80.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = authorData.authorName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HanimeTextPrimary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = authorData.subscriberCount, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HanimePrimary)
                        Text(text = "订阅者", fontSize = 12.sp, color = HanimeTextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = authorData.videoCount, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HanimePrimary)
                        Text(text = "视频", fontSize = 12.sp, color = HanimeTextSecondary)
                    }
                }
            }
        }

        if (authorData.videos.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "影片", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HanimeTextPrimary)
                    if (authorData.uploadedPageUrl.isNotEmpty()) {
                        Text(
                            text = "查看更多 →",
                            fontSize = 13.sp,
                            color = HanimeTextSecondary,
                            modifier = Modifier.clickable { onViewAllVideos(authorData.uploadedPageUrl) }
                        )
                    }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(authorData.videos) { video ->
                        VideoCard(video = video, onClick = { onVideoClick(video.videoUrl) })
                    }
                }
            }
        }

        if (authorData.playlists.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "播放清单", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HanimeTextPrimary)
                    if (authorData.playlistsPageUrl.isNotEmpty()) {
                        Text(
                            text = "查看更多 →",
                            fontSize = 13.sp,
                            color = HanimeTextSecondary,
                            modifier = Modifier.clickable { onViewAllPlaylists(authorData.playlistsPageUrl) }
                        )
                    }
                }
            }
            items(authorData.playlists) { playlist ->
                PlaylistSummaryCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.playlistUrl) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun PlaylistSummaryCard(
    playlist: PlaylistSummary,
    onClick: () -> Unit = {}
) {
    val gradient = gradients.getOrElse(playlist.title.hashCode() % gradients.size) { gradients[0] }
    val emoji = emojis.getOrElse(playlist.title.hashCode() % emojis.size) { emojis[0] }

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
            thumbnailUrl = playlist.thumbnailUrl,
            emoji = emoji,
            gradient = gradient,
            duration = "",
            likeRate = "",
            viewCount = playlist.videoCount,
            crop = true,
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = playlist.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HanimeTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = playlist.author, fontSize = 11.sp, color = HanimePrimary)
                if (playlist.publishTime.isNotEmpty()) {
                    Text(text = " · ${playlist.publishTime}", fontSize = 11.sp, color = HanimeTextSecondary)
                }
            }
        }
    }
}