package app.amisles.hanime.feature.download

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R

internal class DownloadScreenLaunchers(
    val importMigration: () -> Unit,
    val notificationPermissionMissing: Boolean,
    val mediaPermissionMissing: Boolean,
    val requestNotificationPermission: () -> Unit,
    val requestMediaPermission: () -> Unit
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
internal fun rememberDownloadScreenLaunchers(viewModel: DownloadViewModel): DownloadScreenLaunchers {
    val context = LocalContext.current
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var notificationMissing by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        )
    }
    var mediaMissing by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, mediaPermission) !=
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationDenied by remember { mutableStateOf(false) }
    var mediaDenied by remember { mutableStateOf(false) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaMissing = !granted
        mediaDenied = !granted
        viewModel.rescanDownloads()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationMissing = !granted
        notificationDenied = !granted
    }

    LaunchedEffect(Unit) { viewModel.rescanDownloads() }

    val migrationExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        // 用户取消时也要清掉内存副本，否则会一直占着几 MB 直到下次导出
        if (uri == null) viewModel.consumeMigrationBytes()
        else viewModel.writeMigrationTo(uri, context)
    }

    val migrationImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.readMigrationFrom(it, context) { count ->
                Toast.makeText(
                    context,
                    context.getString(
                        if (count > 0) R.string.download_migration_import_done
                        else R.string.download_migration_import_failed,
                        count
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val migrationBytes by viewModel.migrationBytes.collectAsStateWithLifecycle()
    LaunchedEffect(migrationBytes) {
        migrationBytes?.let {
            migrationExportLauncher.launch("hanime-downloads-" + System.currentTimeMillis() + ".zip")
        }
    }

    return DownloadScreenLaunchers(
        importMigration = {
            migrationImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        },
        notificationPermissionMissing = notificationMissing,
        mediaPermissionMissing = mediaMissing,
        requestNotificationPermission = {
            val activity = context.findActivity()
            if (notificationDenied && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                context.openAppSettings()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        requestMediaPermission = {
            val activity = context.findActivity()
            if (mediaDenied && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, mediaPermission)
            ) {
                context.openAppSettings()
            } else {
                mediaPermissionLauncher.launch(mediaPermission)
            }
        }
    )
}
