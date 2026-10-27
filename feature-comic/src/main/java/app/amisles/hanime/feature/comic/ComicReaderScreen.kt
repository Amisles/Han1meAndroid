package app.amisles.hanime.feature.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.surfaceEdge
import app.amisles.hanime.domain.model.ComicReadMode
import coil3.compose.AsyncImage

private val PAGE_ASPECT_RATIO = 0.7f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    comicId: String,
    startPage: Int,
    title: String,
    coverUrl: String,
    onBackClick: () -> Unit,
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = LocalAppPreferences.current
    val savedMode by prefs.comicReadModeFlow.collectAsStateWithLifecycle()
    val mode = savedMode ?: ComicReadMode.Paged

    LaunchedEffect(comicId, startPage) {
        viewModel.load(comicId, startPage, title, coverUrl)
    }

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var jumpVisible by rememberSaveable { mutableStateOf(false) }
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    val toggleChrome = { chromeVisible = !chromeVisible }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val chapter = state.chapter
        if (chapter != null && state.hasPages) {
            when (mode) {
                ComicReadMode.Paged -> PagedReader(
                    pages = chapter.pages,
                    startPage = state.currentPage,
                    jumpTarget = jumpTarget,
                    onJumpHandled = { jumpTarget = null },
                    onPageChanged = viewModel::onPageChanged,
                    onToggleChrome = toggleChrome
                )

                ComicReadMode.Continuous -> ContinuousReader(
                    pages = chapter.pages,
                    startPage = state.currentPage,
                    jumpTarget = jumpTarget,
                    onJumpHandled = { jumpTarget = null },
                    onPageChanged = viewModel::onPageChanged,
                    onToggleChrome = toggleChrome
                )
            }

            if (chromeVisible) {
                ReaderTopBar(
                    title = state.title.ifBlank { title },
                    pageCount = state.pageCount,
                    mode = mode,
                    onModeChange = { prefs.setComicReadMode(it) },
                    onBackClick = onBackClick,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                ReaderBottomBar(
                    currentPage = state.currentPage,
                    pageCount = state.pageCount,
                    onSeek = { jumpTarget = it },
                    onStep = { delta -> jumpTarget = state.currentPage + delta },
                    onOpenJump = { jumpVisible = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                ReadingProgress(
                    currentPage = state.currentPage,
                    pageCount = state.pageCount,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        if (state.isLoading && chapter == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        val error = state.error
        if (error != null && chapter == null) {
            KaomojiErrorView(
                message = error,
                onRetry = viewModel::retry,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (jumpVisible) {
        ModalBottomSheet(
            onDismissRequest = { jumpVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PageJumpPanel(
                pages = state.chapter?.pages.orEmpty(),
                currentPage = state.currentPage,
                onSelect = { page ->
                    jumpTarget = page
                    jumpVisible = false
                }
            )
        }
    }
}

@Composable
private fun PagedReader(
    pages: List<String>,
    startPage: Int,
    jumpTarget: Int?,
    onJumpHandled: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = (startPage - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    ) { pages.size.coerceAtLeast(1) }

    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            onPageChanged(index + 1)
        }
    }

    LaunchedEffect(jumpTarget, pages.size) {
        val target = jumpTarget ?: return@LaunchedEffect
        if (target in 1..pages.size) {
            pagerState.scrollToPage(target - 1)
        }
        onJumpHandled()
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { index ->
        Box(modifier = Modifier.fillMaxSize()) {
            PageImage(
                url = pages.getOrNull(index).orEmpty(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Row(modifier = Modifier.fillMaxSize()) {
                TapZone(weight = 0.3f) {
                    if (index > 0) onPageChanged(index)
                }
                TapZone(weight = 0.4f) { onToggleChrome() }
                TapZone(weight = 0.3f) {
                    if (index < pages.lastIndex) onPageChanged(index + 2)
                }
            }
        }
    }
}

@Composable
private fun RowScope.TapZone(weight: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .weight(weight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun ContinuousReader(
    pages: List<String>,
    startPage: Int,
    jumpTarget: Int?,
    onJumpHandled: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleChrome: () -> Unit
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (startPage - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    )

    LaunchedEffect(listState, pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            onPageChanged(index + 1)
        }
    }

    LaunchedEffect(jumpTarget, pages.size) {
        val target = jumpTarget ?: return@LaunchedEffect
        if (target in 1..pages.size) {
            listState.scrollToItem(target - 1)
        }
        onJumpHandled()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onToggleChrome() }
            },
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items = pages, key = { it }) { url ->
            PageImage(
                url = url,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PAGE_ASPECT_RATIO)
            )
        }
    }
}

@Composable
private fun PageImage(url: String, contentScale: ContentScale, modifier: Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    pageCount: Int,
    mode: ComicReadMode,
    onModeChange: (ComicReadMode) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )
                )
            )
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.comic_pages_count, pageCount),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ModeSwitch(mode = mode, onModeChange = onModeChange)
    }
}

@Composable
private fun ModeSwitch(mode: ComicReadMode, onModeChange: (ComicReadMode) -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .surfaceEdge(shape)
            .padding(2.dp)
    ) {
        ModeOption(
            label = stringResource(R.string.comic_reader_mode_paged),
            selected = mode == ComicReadMode.Paged,
            onClick = { onModeChange(ComicReadMode.Paged) }
        )
        ModeOption(
            label = stringResource(R.string.comic_reader_mode_continuous),
            selected = mode == ComicReadMode.Continuous,
            onClick = { onModeChange(ComicReadMode.Continuous) }
        )
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = label,
        fontSize = 11.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    onSeek: (Int) -> Unit,
    onStep: (Int) -> Unit,
    onOpenJump: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 拖动时只更新本地值，松手才跳页：逐帧跳会让 Pager 每帧重新布局
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val displayedPage = dragValue?.toInt()?.coerceIn(1, pageCount) ?: currentPage

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Slider(
            value = dragValue ?: currentPage.toFloat(),
            onValueChange = { value -> dragValue = value },
            onValueChangeFinished = {
                val target = (dragValue ?: currentPage.toFloat()).toInt()
                dragValue = null
                onSeek(target.coerceIn(1, pageCount))
            },
            valueRange = 1f..pageCount.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onStep(-1) }, enabled = displayedPage > 1) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.comic_reader_prev),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.comic_reader_page_of, displayedPage, pageCount),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onStep(1) }, enabled = displayedPage < pageCount) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.comic_reader_next),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onOpenJump) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = stringResource(R.string.comic_reader_jump),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ReadingProgress(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    val fraction = if (pageCount <= 0) 0f else currentPage.toFloat() / pageCount
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.comic_reader_page_of, currentPage, pageCount),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun PageJumpPanel(
    pages: List<String>,
    currentPage: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.comic_reader_jump),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.comic_pages_count, pages.size),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            gridItemsIndexed(items = pages, key = { _, url -> url }) { index, url ->
                val number = index + 1
                val selected = number == currentPage
                val shape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .surfaceEdge(shape)
                        .clickable { onSelect(number) }
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.comic_reader_page_short, number),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        text = number.toString(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .surfaceEdge(shape, borderWidth = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
