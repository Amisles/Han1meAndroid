package app.amisles.hanime.feature.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeBorder
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary

private data class ProfileMenuItem(
    val iconVector: ImageVector,
    val text: String,
    val action: () -> Unit
)

@Composable
private fun StatItem(
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = HanimeTextPrimary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = HanimeTextSecondary,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel()
    val isLogin by Preferences.loginStateFlow.collectAsStateWithLifecycle()
    val displayName by Preferences.savedUserIdFlow.collectAsStateWithLifecycle()
    val watchCount by profileViewModel.watchCount.collectAsStateWithLifecycle()
    val favoriteCount by profileViewModel.favoriteCount.collectAsStateWithLifecycle()
    val downloadCount by profileViewModel.downloadCount.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                profileViewModel.initDatabase(context)
                profileViewModel.loadCounts(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val menuItems = buildList {
        add(ProfileMenuItem(Icons.Default.History, stringResource(R.string.profile_watch_history)) { onNavigate("history") })
        add(ProfileMenuItem(Icons.Default.Favorite, stringResource(R.string.profile_favorites)) { onNavigate("favorite") })
        add(ProfileMenuItem(Icons.Default.Download, stringResource(R.string.profile_download_manager)) { onNavigate("download") })
        add(ProfileMenuItem(Icons.Default.Download, stringResource(R.string.profile_batch_download)) { onNavigate("batchDownload") })
        add(ProfileMenuItem(Icons.Default.Settings, stringResource(R.string.profile_settings)) { onNavigate("settings") })
        add(ProfileMenuItem(Icons.Default.Info, stringResource(R.string.profile_about)) { onNavigate("about") })
        if (isLogin) {
            add(ProfileMenuItem(Icons.Default.Logout, stringResource(R.string.profile_logout)) {
                Preferences.logout()
                Toast.makeText(context, context.getString(R.string.profile_logout_success), Toast.LENGTH_SHORT).show()
            })
        }
    }

    val letter = run {
        val raw = Preferences.extractDisplayEmail().ifBlank { displayName }
        if (raw.isBlank()) "H" else raw.take(1).uppercase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(HanimePrimary, HanimeBackground)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.clickable(enabled = !isLogin) {
                    if (!isLogin) onNavigate("login")
                }
            ) {
                Text(
                    text = if (isLogin) letter else "👤",
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    color = if (isLogin) HanimeTextPrimary else Color.Unspecified,
                    modifier = Modifier
                        .width(56.dp)
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLogin) Color.White.copy(alpha = 0.15f)
                            else Color.White.copy(alpha = 0.2f)
                        )
                        .padding(top = 12.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isLogin) stringResource(R.string.profile_member) else stringResource(R.string.profile_not_logged_in),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HanimeTextPrimary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = if (isLogin) stringResource(R.string.profile_logged_in) else stringResource(R.string.profile_click_login),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.clickable(enabled = !isLogin) {
                            if (!isLogin) onNavigate("login")
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    count = watchCount.toString(),
                    label = stringResource(R.string.profile_stat_watch),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    count = favoriteCount.toString(),
                    label = stringResource(R.string.profile_stat_favorite),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    count = downloadCount.toString(),
                    label = stringResource(R.string.profile_stat_download),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    count = "0",
                    label = stringResource(R.string.profile_stat_share),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 15.dp)
        ) {
            menuItems.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .clickable { item.action() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = item.iconVector,
                        contentDescription = item.text,
                        modifier = Modifier.size(22.dp),
                        tint = HanimeTextPrimary
                    )

                    Text(
                        text = item.text,
                        fontSize = 15.sp,
                        color = HanimeTextPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "→",
                        fontSize = 13.sp,
                        color = HanimeTextSecondary
                    )
                }

                if (index < menuItems.size - 1) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(HanimeBorder)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
