package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.PrivacyAwareAsyncImage
import app.amisles.hanime.core.ui.components.ScreenHeader
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.model.PlaylistDetail

@Composable
fun UserPlaylistDetailScreen(
    url: String,
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    viewModel: UserPlaylistDetailViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(url) { viewModel.load(url) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = detail?.title ?: stringResource(R.string.playlist_title),
            onBackClick = onBackClick
        )

        val current = detail
        when {
            isLoading && current == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }

            error != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.orEmpty(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { viewModel.load(url) }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }

            current != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    PlaylistHeader(detail = current)
                }
                items(current.videos, key = { it.id }) { video ->
                    PlaylistVideoRow(video = video, onClick = { onVideoClick(video.videoUrl) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(detail: PlaylistDetail) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = detail.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        val meta = buildString {
            append(stringResource(R.string.common_playlist_videos_count, detail.videoCount))
            if (detail.author.isNotBlank()) {
                append(" · ")
                append(stringResource(R.string.cloud_playlist_created_by, detail.author))
            }
        }
        Text(
            text = meta,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (detail.description.isNotBlank()) {
            Text(
                text = detail.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun PlaylistVideoRow(video: HanimeVideo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .surfaceEdge(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            PrivacyAwareAsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize()
            )
            if (video.duration.isNotBlank()) {
                Text(
                    text = video.duration,
                    fontSize = 10.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.author.isNotBlank()) {
                Text(
                    text = video.author,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
