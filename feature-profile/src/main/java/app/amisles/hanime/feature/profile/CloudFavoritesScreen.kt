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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.PrivacyAwareAsyncImage
import app.amisles.hanime.core.ui.components.ScreenHeader
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.domain.model.PlaylistSummary

@Composable
fun CloudFavoritesScreen(
    onBackClick: () -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onLocalFavoritesClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    viewModel: CloudFavoritesViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = stringResource(R.string.profile_favorites),
            onBackClick = onBackClick,
            actions = {
                TextButton(onClick = onLocalFavoritesClick) {
                    Text(
                        text = stringResource(R.string.cloud_favorite_local),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.common_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        when {
            !loggedIn -> CenterHint(
                text = stringResource(R.string.cloud_favorite_login_required),
                action = stringResource(R.string.login_button),
                onAction = onLoginClick
            )

            isLoading && playlists.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }

            error != null -> CenterHint(
                text = error.orEmpty(),
                action = stringResource(R.string.common_retry),
                onAction = { viewModel.refresh() }
            )

            playlists.isEmpty() -> CenterHint(
                text = stringResource(R.string.cloud_favorite_empty),
                hint = stringResource(R.string.cloud_favorite_empty_hint)
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(playlists, key = { it.playlistUrl }) { playlist ->
                    PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist.playlistUrl) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .surfaceEdge(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrivacyAwareAsyncImage(
            model = playlist.thumbnailUrl,
            contentDescription = playlist.title,
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.videoCount,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            val meta = listOf(playlist.author, playlist.publishTime)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CenterHint(text: String, hint: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
