package app.amisles.hanime.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.common.util.CrashLogger
import app.amisles.hanime.core.common.util.CrashRecord
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.HanimeDanger
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val crashTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/**
 * 崩溃日志页。
 *
 * 崩溃记录只落在应用私有目录，导出走系统文件选择器；页面明确标注「不上传任何数据」，
 * 避免用户误以为这类日志会被自动回传。
 */
@Composable
fun CrashLogScreen(
    onBackClick: () -> Unit = {},
    viewModel: CrashLogViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsStateWithLifecycle()
    val exportText by viewModel.exportText.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val text = exportText
        if (uri == null || text == null) {
            viewModel.consumeExport()
            return@rememberLauncherForActivityResult
        }
        // openOutputStream 返回 null（目标不可写）与写入抛异常都视为失败：若一律提示
        // 「已导出」，用户以为日志已保存，之后清空崩溃记录即永久丢失
        val writeOk = runCatching {
            val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
            stream.use { it.write(text.toByteArray()) }
            true
        }.getOrDefault(false)
        viewModel.consumeExport()
        val toastRes = if (writeOk) R.string.crash_log_exported else R.string.crash_log_export_failed
        Toast.makeText(context, context.getString(toastRes), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(exportText) {
        if (exportText != null) {
            exportLauncher.launch("hanime-crash-" + System.currentTimeMillis() + ".txt")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.crash_log_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp)
            )
        }

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.crash_log_empty),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        Text(
            text = stringResource(R.string.crash_log_privacy),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            ActionChip(
                label = stringResource(R.string.crash_log_export),
                onClick = { viewModel.prepareExport() }
            )
            Spacer(modifier = Modifier.size(8.dp))
            ActionChip(
                label = stringResource(R.string.crash_log_clear),
                danger = true,
                onClick = { showClearDialog = true }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 15.dp,
                end = 15.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records, key = { it.file.absolutePath }) { record ->
                CrashRow(record)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.crash_log_clear),
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.crash_log_clear_confirm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearAll()
                }) {
                    Text(stringResource(R.string.common_delete), color = HanimeDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun CrashRow(record: CrashRecord) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    var expanded by remember { mutableStateOf(false) }
    val content = remember(record.file.absolutePath, expanded) {
        if (expanded) CrashLogger.read(record.file) else ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = crashTimeFormatter.format(Date(record.occurredAt)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = record.versionName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (record.device.isNotBlank()) {
            Text(
                text = record.device,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = if (expanded) content else record.preview,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ActionChip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(LocalUiConfig.current.cardCornerRadius)
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (danger) HanimeDanger else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (danger) HanimeDanger.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
