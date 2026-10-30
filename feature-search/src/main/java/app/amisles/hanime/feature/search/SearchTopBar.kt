package app.amisles.hanime.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SearchField

@Composable
internal fun SearchTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.search_placeholder),
            onSearch = onSearch,
            onClear = onClear,
            modifier = Modifier.weight(1f)
        )
    }
}
