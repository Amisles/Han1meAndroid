package app.amisles.hanime.feature.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.amisles.hanime.core.ui.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal class DownloadScreenLaunchers(
    val importMigration: () -> Unit
)

@Composable
internal fun rememberDownloadScreenLaunchers(viewModel: DownloadViewModel): DownloadScreenLaunchers {
    val context = LocalContext.current
// 媒体读取权限：卸载重装后要靠它从 MediaStore 找回上一个安装写入的视频
val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_VIDEO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}
val mediaPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { viewModel.rescanDownloads() }

val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) {
    // 通知权限的弹窗结束后才轮到存储权限：系统同一时刻只处理一个请求，
    // 两个 launch 挨在一起时第二个会被静默丢弃。
    if (ContextCompat.checkSelfPermission(context, mediaPermission) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        mediaPermissionLauncher.launch(mediaPermission)
    }
}

// 每次进入本页都按当前授权状态决定是否申请：只用一次标志会让被拒后无法重试。
// 已授权时这里不会弹窗，系统也会自行限制重复询问次数。
LaunchedEffect(Unit) {
    val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    if (needsNotification) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else if (ContextCompat.checkSelfPermission(context, mediaPermission) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        mediaPermissionLauncher.launch(mediaPermission)
    }
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

return remember(migrationImportLauncher) {
    DownloadScreenLaunchers {
        migrationImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }
}
}
