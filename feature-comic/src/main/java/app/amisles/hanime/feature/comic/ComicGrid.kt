package app.amisles.hanime.feature.comic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun ComicGrid(
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyGridScope.(cardWidth: Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gap = COMIC_GRID_GAP.dp
        val cardWidth = (maxWidth - COMIC_GRID_PADDING.dp * 2 - gap * (COMIC_GRID_COLUMNS - 1)) / COMIC_GRID_COLUMNS
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(COMIC_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = COMIC_GRID_PADDING.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = contentPadding
        ) {
            content(cardWidth)
        }
    }
}
