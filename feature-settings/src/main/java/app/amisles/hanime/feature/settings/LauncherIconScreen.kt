package app.amisles.hanime.feature.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.components.ScreenHeader
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SettingsGroupCard
import app.amisles.hanime.core.ui.components.SettingsGroupDivider
import app.amisles.hanime.core.ui.components.SettingsNavRow
import app.amisles.hanime.domain.model.LauncherIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun LauncherIcon.labelRes(): Int = when (this) {
    LauncherIcon.DEFAULT -> R.string.launcher_icon_default
    LauncherIcon.CALCULATOR -> R.string.launcher_icon_calculator
    LauncherIcon.NOTES -> R.string.launcher_icon_notes
    LauncherIcon.GALLERY -> R.string.launcher_icon_gallery
    LauncherIcon.MUSIC -> R.string.launcher_icon_music
    LauncherIcon.FILES -> R.string.launcher_icon_files
}

private fun LauncherIcon.artworkRes(): Int = when (this) {
    LauncherIcon.DEFAULT -> R.drawable.ic_app_logo
    LauncherIcon.CALCULATOR -> R.drawable.ic_launcher_calculator_foreground
    LauncherIcon.NOTES -> R.drawable.ic_launcher_notes_foreground
    LauncherIcon.GALLERY -> R.drawable.ic_launcher_gallery_foreground
    LauncherIcon.MUSIC -> R.drawable.ic_launcher_music_foreground
    LauncherIcon.FILES -> R.drawable.ic_launcher_files_foreground
}

private fun LauncherIcon.backgroundColorRes(): Int? = when (this) {
    LauncherIcon.DEFAULT -> null
    LauncherIcon.CALCULATOR -> R.color.ic_launcher_calculator_background
    LauncherIcon.NOTES -> R.color.ic_launcher_notes_background
    LauncherIcon.GALLERY -> R.color.ic_launcher_gallery_background
    LauncherIcon.MUSIC -> R.color.ic_launcher_music_background
    LauncherIcon.FILES -> R.color.ic_launcher_files_background
}

@Composable
fun LauncherIconScreen(
    onBackClick: () -> Unit = {},
    viewModel: LauncherIconViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    var pinning by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pinning = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { LauncherShortcut.pin(context, uri) }
            }
            pinning = false
            val messageRes = outcome.fold(
                onSuccess = { pinned ->
                    if (pinned) R.string.launcher_icon_custom_done
                    else R.string.launcher_icon_custom_unsupported
                },
                onFailure = { R.string.launcher_icon_custom_failed }
            )
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = stringResource(R.string.launcher_icon_title),
            onBackClick = onBackClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.launcher_icon_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 12.dp)
            )

            SettingsGroupCard {
                LauncherIcon.entries.forEachIndexed { index, icon ->
                    if (index > 0) SettingsGroupDivider()
                    LauncherIconRow(
                        icon = icon,
                        selected = icon == selected,
                        onClick = {
                            if (icon == selected) return@LauncherIconRow
                            val applied = viewModel.select(icon)
                            Toast.makeText(
                                context,
                                if (applied) R.string.launcher_icon_applied else R.string.launcher_icon_apply_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            if (applied) restartThroughEnabledEntry(context)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsGroupCard {
                SettingsNavRow(
                    icon = Icons.Outlined.Image,
                    title = stringResource(R.string.launcher_icon_custom_title),
                    subtitle = stringResource(R.string.launcher_icon_custom_summary),
                    onClick = {
                        if (!pinning) {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// 被禁用的入口若是当前任务的来源，系统会连任务一起结束；这里主动从新入口重开
private fun restartThroughEnabledEntry(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    runCatching { context.startActivity(launch) }
}

@Composable
private fun LauncherIconRow(
    icon: LauncherIcon,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val background = icon.backgroundColorRes()
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .then(
                    if (background != null) Modifier.background(colorResource(background))
                    else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon.artworkRes()),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(icon.labelRes()),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
