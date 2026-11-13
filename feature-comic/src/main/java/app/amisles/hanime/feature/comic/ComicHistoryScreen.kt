package app.amisles.hanime.feature.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.PrivacyAwareAsyncImage
import app.amisles.hanime.core.ui.components.ScreenHeader
import app.amisles.hanime.core.ui.components.EmptyState
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.Icons
import app.amisles.hanime.domain.model.ComicProgress

@Composable
fun ComicHistoryScreen(
    onBackClick: () -> Unit = {},
    onReadClick: (comicId: String, page: Int, title: String, coverUrl: String) -> Unit = { _, _, _, _ -> },
    viewModel: ComicHistoryViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val progressList = items

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = stringResource(R.string.comic_history_title),
            onBackClick = onBackClick
        )
        if (progressList != null && progressList.isEmpty()) {
            EmptyState(
                icon = Icons.Default.MenuBook,
                title = stringResource(R.string.comic_history_empty),
                modifier = Modifier.fillMaxSize()
            )
        } else if (progressList != null) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(progressList, key = { it.comicId }) { progress ->
                    ComicHistoryRow(
                        progress = progress,
                        onClick = {
                            onReadClick(
                                progress.comicId,
                                progress.lastPage,
                                progress.title,
                                progress.coverUrl
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComicHistoryRow(progress: ComicProgress, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrivacyAwareAsyncImage(
            model = progress.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .size(width = 64.dp, height = 86.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = progress.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.comic_history_progress, progress.lastPage, progress.pageCount),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (progress.pageCount > 0) {
                val fraction = (progress.lastPage.toFloat() / progress.pageCount.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
