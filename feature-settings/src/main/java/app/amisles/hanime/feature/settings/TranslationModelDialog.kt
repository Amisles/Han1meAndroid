package app.amisles.hanime.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.model.TranslationModelState

@Composable
internal fun translationModelStatusText(
    state: TranslationModelState,
    isPublished: Boolean
): String = when (state) {
    is TranslationModelState.Downloading -> if (state.totalBytes > 0L) {
        stringResource(
            R.string.translation_model_downloading_percent,
            (state.progress * 100).toInt()
        )
    } else {
        stringResource(R.string.translation_model_downloading)
    }

    is TranslationModelState.Ready -> stringResource(R.string.translation_model_ready)
    TranslationModelState.Failed -> stringResource(R.string.translation_model_failed)
    TranslationModelState.NotDownloaded -> if (isPublished) {
        stringResource(R.string.translation_model_not_downloaded)
    } else {
        stringResource(R.string.translation_model_unpublished)
    }
}

@Composable
internal fun TranslationModelDialog(
    state: TranslationModelState,
    isPublished: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_translation_model),
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.translation_model_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    text = translationModelStatusText(state, isPublished),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (state is TranslationModelState.Downloading && state.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            when {
                state is TranslationModelState.Downloading -> TextButton(onClick = onCancelDownload) {
                    Text(
                        text = stringResource(R.string.translation_model_cancel_download),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                state is TranslationModelState.Ready -> TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                isPublished -> TextButton(onClick = onDownload) {
                    Text(
                        text = stringResource(R.string.translation_model_download),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
