package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.theme.HanimePrimary

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = HanimePrimary,
            modifier = Modifier.size(size.dp)
        )
    }
}