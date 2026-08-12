package app.amisles.hanime.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.data.preferences.Preferences
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun SubscriptionsScreen(
    initialQuery: String = "",
    onBackClick: () -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val viewModel: SubscriptionsViewModel = hiltViewModel()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val selectedQuery by viewModel.selectedQuery.collectAsStateWithLifecycle()
    val isLogin by Preferences.loginStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (isLogin) viewModel.load(initialQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部返回栏 + 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.subscriptions),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when {
            !isLogin -> {
                NotLoggedInView(onNavigateToLogin = onNavigateToLogin)
            }
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            error != null -> {
                KaomojiErrorView(
                    message = error ?: stringResource(R.string.subscriptions_load_failed),
                    onRetry = { viewModel.load(selectedQuery) }
                )
            }
            else -> {
                // 已订阅作者横向筛选行（含「全部」）
                if (artists.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "__all__") {
                            ArtistChip(
                                name = stringResource(R.string.subscriptions_all),
                                avatarUrl = "",
                                isSelected = selectedQuery.isEmpty(),
                                onClick = { viewModel.selectArtist("") }
                            )
                        }
                        items(artists, key = { it.name }) { artist ->
                            ArtistChip(
                                name = artist.name,
                                avatarUrl = artist.avatarUrl,
                                isSelected = selectedQuery == artist.name,
                                onClick = { viewModel.selectArtist(artist.name) }
                            )
                        }
                    }
                }

                // 当前范围标题：呼应官网「全部订阅」/「该作者的所有作品」语义
                ScopeHeader(selectedQuery = selectedQuery)

                if (videos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = stringResource(R.string.subscriptions_empty),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videos, key = { it.id }) { video ->
                            VideoListItem(
                                video = video,
                                onClick = { onVideoClick(video.videoUrl) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeHeader(selectedQuery: String) {
    val text = if (selectedQuery.isEmpty()) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(stringResource(R.string.subscriptions_all_scope))
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("「")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                append(selectedQuery)
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("」" + stringResource(R.string.subscriptions_author_scope))
            }
        }
    }
    Text(
        text = text,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 4.dp)
    )
}

@Composable
private fun ArtistChip(
    name: String,
    avatarUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val nameColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (avatarUrl.isNotEmpty()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = name,
            fontSize = 13.sp,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NotLoggedInView(
    onNavigateToLogin: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.subscriptions_not_logged_in),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToLogin) {
            Text(text = stringResource(R.string.login_button))
        }
    }
}
