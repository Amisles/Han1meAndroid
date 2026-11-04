package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R

data class PlaylistSaveItem(
    val target: String,
    val title: String,
    val selected: Boolean
)

data class PlaylistSaveUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val items: List<PlaylistSaveItem> = emptyList(),
    val creating: Boolean = false,
    val pendingCount: Int = 0,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSaveSheet(
    state: PlaylistSaveUiState,
    onToggle: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.visible) return

    var formOpen by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_sheet_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.playlist_sheet_public),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else {
                if (state.items.isEmpty() && !formOpen) {
                    Text(
                        text = stringResource(R.string.playlist_sheet_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                state.items.forEach { item ->
                    PlaylistSaveRow(item = item, onToggle = onToggle)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            if (formOpen) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_sheet_name_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.playlist_sheet_desc_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { formOpen = false; title = ""; description = "" }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        enabled = title.isNotBlank() && !state.creating,
                        onClick = {
                            onCreate(title.trim(), description.trim())
                            formOpen = false
                        }
                    ) {
                        Text(
                            if (state.creating) stringResource(R.string.playlist_sheet_creating)
                            else stringResource(R.string.playlist_sheet_create)
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.playlist_sheet_new),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { formOpen = true }
                        .padding(vertical = 12.dp)
                )
            }

            if (state.pendingCount > 0) {
                Text(
                    text = stringResource(R.string.playlist_sheet_pending_count, state.pendingCount),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            state.error?.let { message ->
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PlaylistSaveRow(item: PlaylistSaveItem, onToggle: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(item.target) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.selected, onCheckedChange = { onToggle(item.target) })
        Text(
            text = item.title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
