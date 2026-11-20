package app.amisles.hanime.ui.app

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.navigation.NavHostController
import app.amisles.hanime.MainActivity
import app.amisles.hanime.navigation.navigateToRoot

/** 外链与深链入口：把 Intent 转换成一次导航，并保证同一份 Intent 只消费一次。 */
@Composable
internal fun HandleAppIntents(navController: NavHostController) {
    val activity = LocalActivity.current as? ComponentActivity
    var handledIntentTag by rememberSaveable { mutableStateOf("") }
    var pendingIntent by remember { mutableStateOf(activity?.intent) }

    DisposableEffect(activity) {
        val listener = Consumer<Intent> { intent -> pendingIntent = intent }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent ?: return@LaunchedEffect
        val tag = listOf(
            intent.action,
            intent.data,
            intent.extras?.keySet()?.sorted()?.joinToString(",") { key -> "$key=${intent.extras?.get(key)}" }
        ).joinToString("|")
        if (handledIntentTag == tag) return@LaunchedEffect
        when (intent.action) {
            MainActivity.ACTION_OPEN_VIDEO -> {
                handledIntentTag = tag
                pendingIntent = null
                intent.data?.toString()?.takeIf { it.isNotBlank() }?.let { url ->
                    navController.navigate("detail?videoUrl=${Uri.encode(url)}")
                }
            }
            MainActivity.ACTION_PLAY_LOCAL -> {
                handledIntentTag = tag
                pendingIntent = null
                intent.getStringExtra(MainActivity.EXTRA_OPEN_FILE_PATH)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { path -> navController.navigate("localPlayer?filePath=${Uri.encode(path)}") }
            }
            MainActivity.ACTION_OPEN_DOWNLOADS -> {
                handledIntentTag = tag
                pendingIntent = null
                val taskId = intent.getIntExtra(MainActivity.EXTRA_HIGHLIGHT_TASK_ID, -1)
                navController.navigate(if (taskId >= 0) "download?highlight=$taskId" else "download")
            }
            MainActivity.ACTION_OPEN_SUBSCRIPTIONS -> {
                handledIntentTag = tag
                pendingIntent = null
                navController.navigateToRoot("subscriptions")
            }
        }
    }
}
