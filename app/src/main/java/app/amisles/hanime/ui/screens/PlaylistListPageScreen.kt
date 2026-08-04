package app.amisles.hanime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.domain.model.PlaylistSummary
import app.amisles.hanime.core.ui.R as CoreR
import app.amisles.hanime.core.ui.model.emojis
import app.amisles.hanime.core.ui.model.gradients
import app.amisles.hanime.ui.theme.HanimeBackground
import app.amisles.hanime.ui.theme.HanimeCard
import app.amisles.hanime.ui.theme.HanimePrimary
import app.amisles.hanime.ui.theme.HanimeTextPrimary
import app.amisles.hanime.ui.theme.HanimeTextSecondary
import app.amisles.hanime.ui.viewmodel.PlaylistListPageViewModel

@Composable
fun PlaylistListPageScreen(
    title: String? = null,
    url: String = "",
    onBackClick: () -> Unit = {},
    onPlaylistClick: (String) -> Unit = {}
) {
    val viewModel: PlaylistListPageViewModel = hiltViewModel()
    val playlists by viewModel.playlists.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val displayTitle = title ?: stringResource(CoreR.string.playlist_title)

    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            viewModel.loadPlaylists(url)
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
            Text(
                text = displayTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = HanimeTextPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = playlists,
                    key = { it.playlistUrl }
                ) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.playlistUrl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistListItem(
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
            .padding(10.dp),
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
            modifier = Modifier.width(130.dp).height(73.dp).clip(RoundedCornerShape(6.dp))
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
                    Text(
                        text = " · ${playlist.publishTime}",
                        fontSize = 11.sp,
                        color = HanimeTextSecondary
                    )
                }
            }
        }
    }
}
