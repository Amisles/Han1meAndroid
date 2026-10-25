package app.amisles.hanime.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.domain.model.AppUpdate

@Composable
fun UpdateAvailableDialog(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        UpdateDialogBody(update, onUpdate, onSkipVersion, onDismiss)
    }
}

@Composable
internal fun UpdateDialogBody(
    update: AppUpdate,
    onUpdate: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius + 8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.update_dialog_title, update.versionName),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (update.releaseNotes.isBlank()) {
                    Text(
                        text = stringResource(R.string.update_available),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MarkdownNotes(update.releaseNotes)
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton(
                    label = stringResource(R.string.update_do_not_remind),
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onSkipVersion
                )
                DialogButton(
                    label = stringResource(R.string.update_later),
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
                DialogButton(
                    label = stringResource(R.string.update_now),
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onUpdate
                )
            }
        }
    }
}

private val bulletColorRe = Regex("^\\s*[-*+]\\s+")
private val headingRe = Regex("^#{1,6}\\s+")

@Composable
private fun MarkdownNotes(raw: String) {
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant

    raw.lines().forEach { line ->
        when {
            line.isBlank() -> Spacer(Modifier.height(8.dp))
            headingRe.containsMatchIn(line) -> {
                val text = line.replace(headingRe, "")
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            bulletColorRe.containsMatchIn(line) -> Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "·",
                    fontSize = 13.sp,
                    color = accent,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = inlineMarkdown(line.replace(bulletColorRe, ""), body, accent, codeBg),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = body
                )
            }
            line.startsWith("    ") || line.startsWith("\t") -> Text(
                text = line.trim(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = body,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(codeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            else -> Text(
                text = inlineMarkdown(line, body, accent, codeBg),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = body,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

private fun inlineMarkdown(
    raw: String,
    body: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
    codeBg: androidx.compose.ui.graphics.Color
): AnnotatedString {
    if (!raw.contains("**") && !raw.contains('`')) return AnnotatedString(raw)
    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith("**", i) -> {
                    val end = raw.indexOf("**", i + 2)
                    if (end < 0) {
                        append(raw.substring(i)); i = raw.length
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent)) {
                            append(raw.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                }
                raw[i] == '`' -> {
                    val end = raw.indexOf('`', i + 1)
                    if (end < 0) {
                        append(raw.substring(i)); i = raw.length
                    } else {
                        withStyle(
                            SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = body)
                        ) {
                            append(raw.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                else -> {
                    append(raw[i])
                    i++
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val edge = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, edge, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 2,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}
