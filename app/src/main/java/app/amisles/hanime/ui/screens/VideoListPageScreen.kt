package app.amisles.hanime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.amisles.hanime.core.ui.R as CoreR
import app.amisles.hanime.core.ui.components.VideoCard
import app.amisles.hanime.core.ui.theme.ResponsiveContent
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.ui.theme.HanimeBackground
import app.amisles.hanime.ui.theme.HanimePrimary
import app.amisles.hanime.ui.theme.HanimeTextPrimary
import app.amisles.hanime.ui.theme.HanimeTextSecondary
import app.amisles.hanime.ui.viewmodel.VideoListPageViewModel

@Composable
fun VideoListPageScreen(
    title: String? = null,
    url: String = "",
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {}
) {
    val viewModel: VideoListPageViewModel = hiltViewModel()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val displayTitle = title ?: stringResource(CoreR.string.common_videos)

    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            viewModel.loadVideos(url)
        }
    }

    ResponsiveContent {
        Column(
            modifier = Modifier.fillMaxSize().background(HanimeBackground).statusBarsPadding()
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onBackClick() }, modifier = Modifier.size(48.dp)) {
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(currentWindowSizeInfo().gridColumns),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = videos,
                    key = { it.id }
                ) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video.videoUrl) })
                }
            }
        }
    }
    }
}
