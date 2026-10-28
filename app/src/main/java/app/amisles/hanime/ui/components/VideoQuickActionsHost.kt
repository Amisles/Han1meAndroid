package app.amisles.hanime.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.VideoActionSheet
import app.amisles.hanime.feature.detail.components.DetailDownloadDialog
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.feature.detail.util.shareVideo
import app.amisles.hanime.ui.viewmodel.QuickDownloadEvent
import app.amisles.hanime.ui.viewmodel.VideoQuickActionsViewModel

/**
 * 视频长按快捷动作的宿主：收藏切换、分享、画质选择下载与一次事件提示。
 * 放在 :app 层以同时触达 :feature-* 的界面回调与 :feature-detail 的下载弹窗组件。
 */
@Composable
fun VideoQuickActionsHost(
    sheetVideo: HanimeVideo?,
    onDismiss: () -> Unit,
    onSearchRelated: (String) -> Unit,
    viewModel: VideoQuickActionsViewModel
) {
    val context = LocalContext.current
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    LaunchedEffect(sheetVideo) {
        sheetVideo?.let { viewModel.ensureFavoritesLoaded(listOf(extractId(it))) }
    }
    val target by viewModel.target.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            val text = when (message) {
                is Int -> context.getString(message)
                is String -> message
                else -> ""
            }
            if (text.isNotEmpty()) {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.downloadEvents.collect { event ->
            val res = when (event) {
                is QuickDownloadEvent.Started -> R.string.detail_download_added
                QuickDownloadEvent.AlreadyActive -> R.string.detail_download_in_progress
                QuickDownloadEvent.AlreadyCompleted -> R.string.detail_download_completed
                QuickDownloadEvent.InvalidUrl -> R.string.detail_download_invalid_url
                QuickDownloadEvent.Failed -> R.string.common_load_failed
            }
            Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()
        }
    }

    sheetVideo?.let { video ->
        VideoActionSheet(
            video = video,
            isFavorite = favoriteIds.contains(extractId(video)),
            onFavorite = { viewModel.toggleFavorite(video) },
            onDownload = { onDismiss(); viewModel.openDownloadSheet(video) },
            onShare = { shareVideo(context, video.title, video.videoUrl) },
            onSearchRelated = { onSearchRelated(video.author.ifBlank { video.title }) },
            onDismiss = onDismiss
        )
    }

    target?.let { t ->
        DetailDownloadDialog(
            isLoadingQualities = t.isLoading,
            downloadQualities = t.qualities,
            onQualitySelected = { quality ->
                viewModel.startQuickDownload(quality)
                onDismiss()
                viewModel.dismissDownloadSheet()
            },
            onDismiss = { onDismiss(); viewModel.dismissDownloadSheet() }
        )
    }
}

private fun extractId(video: HanimeVideo): String =
    app.amisles.hanime.domain.model.extractVideoId(video.videoUrl)
