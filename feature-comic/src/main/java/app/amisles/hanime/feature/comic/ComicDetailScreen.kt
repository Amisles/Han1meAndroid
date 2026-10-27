package app.amisles.hanime.feature.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.KaomojiErrorView
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.domain.model.ComicDetail
import app.amisles.hanime.domain.model.ComicItem
import app.amisles.hanime.domain.model.ComicPageThumb
import app.amisles.hanime.domain.model.ComicProgress
import app.amisles.hanime.domain.model.ComicSection
import coil3.compose.AsyncImage

private val COVER_WIDTH = 114.dp
private val COVER_HEIGHT = 160.dp
private const val PREVIEW_COLUMNS = 4
private const val PREVIEW_LIMIT = 8
private const val TITLE_COLLAPSE_OFFSET = 72

@Composable
fun ComicDetailScreen(
    comicId: String,
    onBackClick: () -> Unit,
    onReadClick: (String, Int, String, String) -> Unit,
    onComicClick: (String) -> Unit,
    viewModel: ComicDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(comicId) { viewModel.load(comicId) }

    val listState = rememberLazyListState()
    val collapsed by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > TITLE_COLLAPSE_OFFSET
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ComicDetailTopBar(
            title = state.detail?.displayTitle.orEmpty(),
            visible = collapsed,
            onBackClick = onBackClick
        )
        Box(modifier = Modifier.fillMaxSize()) {
            val detail = state.detail
            if (detail != null) {
                ComicDetailContent(
                    detail = detail,
                    progress = state.progress,
                    listState = listState,
                    onReadClick = onReadClick,
                    onComicClick = onComicClick
                )
            }
            if (state.isLoading && detail == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            val error = state.error
            if (error != null && detail == null) {
                KaomojiErrorView(
                    message = error,
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun ComicDetailTopBar(title: String, visible: Boolean, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .alpha(if (visible) 1f else 0f)
        )
    }
}

@Composable
private fun ComicDetailContent(
    detail: ComicDetail,
    progress: ComicProgress?,
    listState: LazyListState,
    onReadClick: (String, Int, String, String) -> Unit,
    onComicClick: (String) -> Unit
) {
    val preview = remember(detail.pageThumbnails) {
        detail.pageThumbnails.take(PREVIEW_LIMIT)
    }
    val previewRows = remember(preview) { preview.chunked(PREVIEW_COLUMNS) }
    val hiddenPages = (detail.pageCount - preview.size).coerceAtLeast(0)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item(key = "hero") { ComicHero(detail) }

        if (detail.tags.isNotEmpty()) {
            item(key = "tagsTitle") {
                SectionTitle(stringResource(R.string.comic_section_tags), topPadding = 8.dp)
            }
            item(key = "tags") { ComicTagFlow(detail.tags) }
        }

        item(key = "metaTitle") {
            SectionTitle(stringResource(R.string.comic_section_info), topPadding = 18.dp)
        }
        item(key = "meta") { ComicMetaBlock(detail) }

        item(key = "action") {
            ComicReadAction(
                progress = progress,
                enabled = detail.pageCount > 0,
                onClick = { page ->
                    onReadClick(detail.id, page, detail.displayTitle, detail.coverUrl)
                }
            )
        }

        if (previewRows.isNotEmpty()) {
            item(key = "pagesTitle") {
                SectionTitleWithCount(
                    title = stringResource(R.string.comic_section_pages),
                    count = detail.pageCount,
                    topPadding = 22.dp
                )
            }
            items(
                count = previewRows.size,
                key = { index -> "pageRow" + index }
            ) { index ->
                ComicPagePreviewRow(
                    thumbs = previewRows[index],
                    trailingCount = if (index == previewRows.lastIndex) hiddenPages else 0,
                    onPageClick = { page ->
                        onReadClick(detail.id, page, detail.displayTitle, detail.coverUrl)
                    },
                    onMoreClick = { onReadClick(detail.id, 1, detail.displayTitle, detail.coverUrl) }
                )
            }
        }

        items(
            count = detail.relatedSections.size,
            key = { index -> "related" + index }
        ) { index ->
            ComicRelatedSection(
                section = detail.relatedSections[index],
                onComicClick = onComicClick
            )
        }
    }
}

@Composable
private fun ComicHero(detail: ComicDetail) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Box(
            modifier = Modifier
                .width(COVER_WIDTH)
                .height(COVER_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (detail.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = detail.coverUrl,
                    contentDescription = detail.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = detail.displayTitle,
                fontSize = 16.5.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.originalTitle.isNotBlank() && detail.originalTitle != detail.title) {
                Text(
                    text = detail.originalTitle,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IdChip(detail.id)
            Spacer(modifier = Modifier.height(1.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniFact(
                    label = stringResource(R.string.comic_label_pages),
                    value = detail.pageCount.toString()
                )
                if (detail.uploadedAt.isNotBlank()) {
                    MiniFact(
                        label = stringResource(R.string.comic_label_uploaded),
                        value = detail.uploadedAt
                    )
                }
            }
        }
    }
}

@Composable
private fun IdChip(id: String) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = "# " + id,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .surfaceEdge(shape)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun MiniFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SectionTitle(title: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = title,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 9.dp)
    )
}

@Composable
private fun SectionTitleWithCount(
    title: String,
    count: Int,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.comic_pages_count, count),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComicTagFlow(tags: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        tags.forEach { tag -> ComicTagChip(tag) }
    }
}

@Composable
private fun ComicTagChip(tag: String) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = tag,
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .surfaceEdge(shape)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun ComicMetaBlock(detail: ComicDetail) {
    val rows = buildList {
        if (detail.artists.isNotEmpty()) add(stringResource(R.string.comic_label_author) to detail.artists)
        if (detail.languages.isNotEmpty()) add(stringResource(R.string.comic_label_language) to detail.languages)
        if (detail.categories.isNotEmpty()) add(stringResource(R.string.comic_label_category) to detail.categories)
    }
    if (rows.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        rows.forEachIndexed { index, (label, values) ->
            ComicMetaRow(label = label, values = values)
            if (index != rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun ComicMetaRow(label: String, values: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(50.dp)
                .padding(top = 3.dp)
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEach { value -> MetaPill(value) }
        }
    }
}

@Composable
private fun MetaPill(value: String) {
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = value,
        fontSize = 12.5.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .surfaceEdge(shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun ComicReadAction(
    progress: ComicProgress?,
    enabled: Boolean,
    onClick: (Int) -> Unit
) {
    val resumePage = progress?.lastPage?.coerceAtLeast(1) ?: 1
    val label = if (progress != null && progress.lastPage > 1) {
        stringResource(R.string.comic_read_continue, progress.lastPage)
    } else {
        stringResource(R.string.comic_read_start)
    }
    Button(
        onClick = { onClick(resumePage) },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ComicPagePreviewRow(
    thumbs: List<ComicPageThumb>,
    trailingCount: Int,
    onPageClick: (Int) -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        thumbs.forEach { thumb ->
            PagePreviewCell(
                thumb = thumb,
                modifier = Modifier.weight(1f),
                onClick = { onPageClick(thumb.number) }
            )
        }
        if (trailingCount > 0) {
            MorePagesCell(
                count = trailingCount,
                modifier = Modifier.weight(1f),
                onClick = onMoreClick
            )
        } else {
            repeat(PREVIEW_COLUMNS - thumbs.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PagePreviewCell(thumb: ComicPageThumb, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .surfaceEdge(shape)
            .clickable(onClick = onClick)
    ) {
        if (thumb.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumb.thumbnailUrl,
                contentDescription = stringResource(R.string.comic_reader_page_short, thumb.number),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = thumb.number.toString(),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun MorePagesCell(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .surfaceEdge(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.comic_pages_more, count),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ComicRelatedSection(section: ComicSection, onComicClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionTitle(section.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            items(items = section.items, key = { it.id }) { item ->
                ComicRelatedCard(item = item, onClick = { onComicClick(item.id) })
            }
        }
    }
}

@Composable
private fun ComicRelatedCard(item: ComicItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .surfaceEdge(shape)
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = item.title,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
