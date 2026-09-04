package app.amisles.hanime.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import java.io.File
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.data.preferences.ThemeMode
import app.amisles.hanime.core.ui.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.Palette
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class LanguageOption(val code: String, val label: String)

@Composable
fun appLanguageOptions() = listOf(
    LanguageOption(Preferences.LANGUAGE_ZH_CN, stringResource(R.string.settings_language_zh_cn)),
    LanguageOption(Preferences.LANGUAGE_ZH_TW, stringResource(R.string.settings_language_zh_tw)),
    LanguageOption(Preferences.LANGUAGE_EN, stringResource(R.string.settings_language_en)),
    LanguageOption(Preferences.LANGUAGE_JA, stringResource(R.string.settings_language_ja))
)

@Composable
private fun <T> SettingsDropdownCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit
) {
    var boxWidthPx by remember { mutableStateOf(0) }
    var menuWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.onSizeChanged { boxWidthPx = it.width }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = selectedLabel,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 0f else -90f)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier
                    .onSizeChanged { menuWidthPx = it.width }
                    .background(MaterialTheme.colorScheme.surface),
                offset = DpOffset(
                    x = with(density) {
                        (boxWidthPx - menuWidthPx).coerceAtLeast(0).toDp()
                    },
                    y = 0.dp
                )
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                color = if (value == selectedValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (value == selectedValue) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(value)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderPickerDialog(
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
                ?: context.getExternalFilesDir(null)?.let { File(it, "Downloads") }
                ?: File(context.filesDir, "Downloads")
        )
    }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var toastRes by remember { mutableStateOf<Int?>(null) }

    val subDirs = remember(currentDir) {
        currentDir.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    LaunchedEffect(toastRes) {
        toastRes?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
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
                    val safe = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val created = runCatching { File(currentDir, safe).mkdirs() }.getOrDefault(false)
                    if (created) {
                        currentDir = File(currentDir, safe)
                        newFolderName = ""
                        showNewFolder = false
                    } else {
                        toastRes = R.string.settings_download_storage_enter_name
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
                if (currentDir.parentFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDir = currentDir.parentFile!! }
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

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 处理一次性 UI 事件（Toast / recreate）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.Toast -> {
                    val msg = if (event.arg != null) {
                        context.getString(event.messageResId, event.arg)
                    } else {
                        context.getString(event.messageResId)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                SettingsUiEvent.RecreateActivity -> {
                    (context as? android.app.Activity)?.recreate()
                }
            }
        }
    }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var appLanguageMenuExpanded by remember { mutableStateOf(false) }
    var downloadConcurrentMenuExpanded by remember { mutableStateOf(false) }
    var themeModeMenuExpanded by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var baseUrlInput by remember { mutableStateOf(uiState.baseUrl) }

    var showStoragePathDialog by remember { mutableStateOf(false) }

    val languageOptions = appLanguageOptions()
    val concurrentCountSuffix = stringResource(R.string.settings_concurrent_count_suffix)
    val themeModeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )

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
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(R.string.profile_settings),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 应用语言
        SettingsDropdownCard(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_app_language),
            selectedLabel = languageOptions.find { it.code == uiState.appLanguage }?.label
                ?: stringResource(R.string.settings_language_zh_cn),
            expanded = appLanguageMenuExpanded,
            onExpandedChange = { appLanguageMenuExpanded = it },
            options = languageOptions.map { it.code to it.label },
            selectedValue = uiState.appLanguage,
            onSelect = { code -> viewModel.setAppLanguage(code) }
        )

        // 主题模式
        SettingsDropdownCard(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_theme_mode),
            selectedLabel = themeModeOptions.find { it.first == uiState.themeMode }?.second
                ?: stringResource(R.string.settings_theme_system),
            expanded = themeModeMenuExpanded,
            onExpandedChange = { themeModeMenuExpanded = it },
            options = themeModeOptions,
            selectedValue = uiState.themeMode,
            onSelect = { mode -> viewModel.setThemeMode(mode) }
        )

        // 同时下载数
        SettingsDropdownCard(
            icon = Icons.Default.Layers,
            title = stringResource(R.string.settings_max_concurrent),
            selectedLabel = "${uiState.maxDownloadConcurrent}$concurrentCountSuffix",
            expanded = downloadConcurrentMenuExpanded,
            onExpandedChange = { downloadConcurrentMenuExpanded = it },
            options = (1..5).map { it to "$it$concurrentCountSuffix" },
            selectedValue = uiState.maxDownloadConcurrent,
            onSelect = { count ->
                viewModel.setMaxDownloadConcurrent(count)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_concurrent_set_toast, count),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        // 下载存储路径
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp)
                .clickable { showStoragePathDialog = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_download_storage),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.downloadStoragePath.isBlank()) {
                            stringResource(R.string.settings_download_storage_external)
                        } else {
                            uiState.downloadStoragePath
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp)
                .clickable {
                    baseUrlInput = uiState.baseUrl
                    showBaseUrlDialog = true
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_base_url),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = uiState.baseUrl,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp)
                .clickable { onNavigate("diagnostics") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.settings_diagnostics),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp)
                .clickable { showClearCacheDialog = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.settings_clear_cache),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title), color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Text(
                    stringResource(R.string.settings_clear_cache_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // G6：成功/失败 Toast 由 SettingsViewModel 经 events 通道在删除完成后发送
                    viewModel.clearAppCache()
                    showClearCacheDialog = false
                }) {
                    Text(stringResource(R.string.common_confirm), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showBaseUrlDialog) {
        AlertDialog(
            onDismissRequest = { showBaseUrlDialog = false },
            title = { Text(stringResource(R.string.settings_base_url_title), color = MaterialTheme.colorScheme.onBackground) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_base_url_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        placeholder = {
                            Text(
                                text = Preferences.DEFAULT_BASE_URL,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBaseUrl(baseUrlInput)
                    showBaseUrlDialog = false
                    Toast.makeText(context, context.getString(R.string.settings_base_url_updated), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.common_save), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.restoreDefaultBaseUrl()
                    baseUrlInput = Preferences.DEFAULT_BASE_URL
                    showBaseUrlDialog = false
                    Toast.makeText(context, context.getString(R.string.settings_base_url_restored), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.common_restore_default), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showStoragePathDialog) {
        FolderPickerDialog(
            initialPath = uiState.downloadStoragePath,
            onDismiss = { showStoragePathDialog = false },
            onConfirm = { viewModel.setDownloadStoragePath(it) }
        )
    }
}
