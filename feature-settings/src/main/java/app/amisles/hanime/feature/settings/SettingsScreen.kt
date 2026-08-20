package app.amisles.hanime.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
    val externalBase = remember(context) {
        context.getExternalFilesDir(null)?.let { File(it, "Downloads") } ?: File(context.filesDir, "Downloads")
    }
    val internalBase = remember(context) { File(context.filesDir, "Downloads") }
    var storageLocation by remember(uiState.downloadStoragePath) {
        mutableStateOf(
            if (uiState.downloadStoragePath.isBlank() ||
                uiState.downloadStoragePath.startsWith(externalBase.absolutePath)
            ) "external" else "internal"
        )
    }
    var storageSubfolder by remember(uiState.downloadStoragePath) {
        mutableStateOf(
            if (uiState.downloadStoragePath.isBlank()) ""
            else uiState.downloadStoragePath
                .removePrefix(externalBase.absolutePath)
                .removePrefix(internalBase.absolutePath)
                .removePrefix(File.separator)
                .removePrefix("/")
        )
    }

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
                    viewModel.clearAppCache()
                    showClearCacheDialog = false
                    Toast.makeText(context, context.getString(R.string.settings_cache_cleared), Toast.LENGTH_SHORT).show()
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
        AlertDialog(
            onDismissRequest = { showStoragePathDialog = false },
            title = {
                Text(
                    stringResource(R.string.settings_download_storage_dialog_title),
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_download_storage_current) + "：" +
                            if (uiState.downloadStoragePath.isBlank()) {
                                stringResource(R.string.settings_download_storage_external)
                            } else {
                                uiState.downloadStoragePath
                            },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    // 外部存储（默认）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { storageLocation = "external" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = storageLocation == "external",
                            onClick = { storageLocation = "external" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_download_storage_external),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    // 内部存储（更私密）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { storageLocation = "internal" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = storageLocation == "internal",
                            onClick = { storageLocation = "internal" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_download_storage_internal),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = storageSubfolder,
                        onValueChange = { storageSubfolder = it },
                        label = { Text(stringResource(R.string.settings_download_storage_subfolder)) },
                        placeholder = {
                            Text(
                                stringResource(R.string.settings_download_storage_subfolder_hint),
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 恢复默认路径
                    Text(
                        text = stringResource(R.string.settings_download_storage_restored),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                viewModel.setDownloadStoragePath("")
                                showStoragePathDialog = false
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val base = if (storageLocation == "external") externalBase else internalBase
                    val folder = storageSubfolder.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val finalPath = if (folder.isBlank()) base.absolutePath else File(base, folder).absolutePath
                    viewModel.setDownloadStoragePath(finalPath)
                    showStoragePathDialog = false
                }) {
                    Text(stringResource(R.string.common_save), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoragePathDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
