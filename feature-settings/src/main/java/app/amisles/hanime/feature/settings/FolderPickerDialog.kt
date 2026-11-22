package app.amisles.hanime.feature.settings

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.util.AppSnackbar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文件夹浏览器允许向上浏览到的根目录。
 *
 * 下载目录可能落在「主外部存储（含公共 Download）」或「应用自身的文件目录」下，因此把上溯限制在这些根之内。
 */
@Suppress("DEPRECATION") // 仅用 getExternalStorageDirectory 取根路径，不用于读写
private fun pickerRoots(context: Context): List<File> = buildList {
    Environment.getExternalStorageDirectory()?.let { add(it) }
    context.getExternalFilesDir(null)?.let { add(it) }
    add(context.filesDir)
}.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }

/** [current] 是否还能向上走：其父目录必须仍位于某个允许的根之内（含根本身）。 */
private fun canNavigateUp(current: File, roots: List<File>): Boolean {
    val parent = current.parentFile ?: return false
    val parentPath = runCatching { parent.canonicalPath }.getOrNull() ?: return false
    return roots.any { root ->
        parentPath == root.path || parentPath.startsWith(root.path + File.separator)
    }
}

@Composable
internal fun FolderPickerDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    context: Context = LocalContext.current
) {
    var currentDir by remember {
        mutableStateOf(
            runCatching {
                val f = File(initialPath)
                when {
                    f.exists() && f.isDirectory -> f
                    f.parentFile?.exists() == true -> f.parentFile!!
                    else -> null
                }
            }.getOrNull()
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?.let { File(it, "Hanime") }
                ?: context.getExternalFilesDir(null)?.let { File(it, "Hanime") }
                ?: context.filesDir
        )
    }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var toastRes by remember { mutableStateOf<Int?>(null) }
    // 建目录异步进行中置位：防止转场窗口内连点两次触发两次 mkdirs（第二次会因目录已存在
    // 返回 false 而误报失败）
    var creatingFolder by remember { mutableStateOf(false) }
    val folderScope = rememberCoroutineScope()

    val roots = remember(context) { pickerRoots(context) }

    // 目录列举在 IO 线程完成：/storage/emulated/0 等位置可能有数百个子目录，主线程
    // listFiles + 排序在慢存储上会阻塞数百毫秒乃至 ANR。key 变化即重新列举，
    // 列举期间沿用上一次结果（旧目录列表短暂残留一帧级时间后刷新）。
    val subDirs by produceState(emptyList<File>(), currentDir) {
        value = withContext(Dispatchers.IO) {
            currentDir.listFiles { f -> f.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
    }

    LaunchedEffect(toastRes) {
        toastRes?.let {
            AppSnackbar.show(context.getString(it))
            toastRes = null
        }
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text(stringResource(R.string.settings_download_storage_new_folder), color = MaterialTheme.colorScheme.onBackground) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(R.string.settings_download_storage_new_folder_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = newFolderName.trim()
                    if (raw.isBlank()) {
                        toastRes = R.string.settings_download_storage_enter_name
                        return@TextButton
                    }
                    if (creatingFolder) return@TextButton
                    creatingFolder = true
                    // 先捕获目标路径再进协程：协程挂起期间 currentDir 可能已被用户改动
                    val target = File(currentDir, raw.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                    folderScope.launch {
                        // mkdirs 是磁盘 IO，慢存储上可达数百毫秒，移出主线程
                        val created = withContext(Dispatchers.IO) {
                            runCatching { target.mkdirs() }.getOrDefault(false)
                        }
                        creatingFolder = false
                        if (created) {
                            currentDir = target
                            newFolderName = ""
                            showNewFolder = false
                        } else {
                            toastRes = R.string.settings_download_storage_create_failed
                        }
                    }
                }) {
                    Text(stringResource(R.string.common_confirm), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_download_storage_picker_title), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_download_storage_current_dir) + "：" + currentDir.absolutePath,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                if (canNavigateUp(currentDir, roots)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDir.parentFile?.let { parent -> currentDir = parent } }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("..", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                if (subDirs.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_download_storage_no_subdirs),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(subDirs, key = { it.absolutePath }) { dir ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentDir = dir }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    dir.name, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground, maxLines = 1
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { showNewFolder = true }) {
                    Text(stringResource(R.string.settings_download_storage_new_folder), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = {
                    onConfirm(currentDir.absolutePath)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.settings_download_storage_select_here), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

