package app.amisles.hanime.feature.settings

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.amisles.hanime.domain.preferences.ThemeMode
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.domain.repository.SettingsRepository
import app.amisles.hanime.core.ui.components.SettingsGroupCard
import app.amisles.hanime.core.ui.components.SettingsGroupDivider
import app.amisles.hanime.core.ui.components.SettingsSwitchRow
import app.amisles.hanime.core.ui.components.SettingsNavRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.outlined.Palette
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class LanguageOption(val code: String, val label: String)

/** 语言选项：(语言代码, 展示文案资源 id)；返回资源 id 而非已解析字符串，便于在 Composable 内解析。 */
private fun languageOptionRes(): List<Pair<String, Int>> = listOf(
    SettingsRepository.LANGUAGE_ZH_CN to R.string.settings_language_zh_cn,
    SettingsRepository.LANGUAGE_ZH_TW to R.string.settings_language_zh_tw,
    SettingsRepository.LANGUAGE_EN to R.string.settings_language_en,
    SettingsRepository.LANGUAGE_JA to R.string.settings_language_ja
)

@Composable
private fun <T> SettingsDropdownRow(
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
                modifier = Modifier.size(24.dp)
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
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?.let { File(it, "Hanime") }
                ?: context.getExternalFilesDir(null)?.let { File(it, "Hanime") }
                ?: context.filesDir
        )
    }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var toastRes by remember { mutableStateOf<Int?>(null) }

    val roots = remember(context) { pickerRoots(context) }

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
    var downloadRateLimitMenuExpanded by remember { mutableStateOf(false) }
    var themeModeMenuExpanded by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var baseUrlInput by remember { mutableStateOf(uiState.baseUrl) }

    var showStoragePathDialog by remember { mutableStateOf(false) }
    var longPressMenuExpanded by remember { mutableStateOf(false) }

    val languageOptions = languageOptionRes()
        .map { LanguageOption(it.first, stringResource(it.second)) }
    val concurrentCountSuffix = stringResource(R.string.settings_concurrent_count_suffix)
    val rateLimitSuffix = stringResource(R.string.settings_download_rate_limit_suffix)
    val themeModeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )
    val longPressSpeedOptions = listOf(1.5f, 2f, 2.5f, 3f).map { it to "${it}×" }

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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            // 外观：语言 / 主题 / 界面定制
            SettingsGroupCard {
                SettingsDropdownRow(
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
                SettingsGroupDivider()
                SettingsDropdownRow(
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
                SettingsGroupDivider()
                SettingsNavRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.settings_customize_ui),
                    subtitle = stringResource(R.string.settings_customize_ui_summary),
                    onClick = { onNavigate("customize") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 播放：长按倍速 / 双击快进 / 自动画质
            SettingsGroupCard {
                SettingsDropdownRow(
                    icon = Icons.Outlined.Speed,
                    title = stringResource(R.string.settings_long_press_speed),
                    selectedLabel = "${uiState.longPressBoostSpeed}×",
                    expanded = longPressMenuExpanded,
                    onExpandedChange = { longPressMenuExpanded = it },
                    options = longPressSpeedOptions,
                    selectedValue = uiState.longPressBoostSpeed,
                    onSelect = { speed -> viewModel.setLongPressBoostSpeed(speed) }
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.TouchApp,
                    title = stringResource(R.string.settings_double_tap_seek),
                    subtitle = stringResource(R.string.settings_double_tap_seek_summary),
                    checked = uiState.doubleTapSeek,
                    onCheckedChange = { enabled -> viewModel.setDoubleTapSeek(enabled) }
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.HighQuality,
                    title = stringResource(R.string.settings_auto_quality),
                    subtitle = stringResource(R.string.settings_auto_quality_summary),
                    checked = uiState.autoQuality,
                    onCheckedChange = { enabled -> viewModel.setAutoQuality(enabled) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 下载：并发数 / 存储路径
            SettingsGroupCard {
                SettingsDropdownRow(
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
                SettingsGroupDivider()
                SettingsDropdownRow(
                    icon = Icons.Outlined.Speed,
                    title = stringResource(R.string.settings_download_rate_limit),
                    selectedLabel = if (uiState.downloadRateLimit == 0) {
                        stringResource(R.string.settings_download_rate_limit_unlimited)
                    } else {
                        "${uiState.downloadRateLimit}$rateLimitSuffix"
                    },
                    expanded = downloadRateLimitMenuExpanded,
                    onExpandedChange = { downloadRateLimitMenuExpanded = it },
                    options = listOf(0, 1, 2, 5, 10, 20).map {
                        it to if (it == 0) {
                            stringResource(R.string.settings_download_rate_limit_unlimited)
                        } else {
                            "$it$rateLimitSuffix"
                        }
                    },
                    selectedValue = uiState.downloadRateLimit,
                    onSelect = { viewModel.setDownloadRateLimit(it) }
                )
                SettingsGroupDivider()
                SettingsNavRow(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.settings_download_storage),
                    subtitle = if (uiState.downloadStoragePath.isBlank()) {
                        stringResource(R.string.settings_download_storage_external)
                    } else {
                        uiState.downloadStoragePath
                    },
                    onClick = { showStoragePathDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 通知：订阅更新提醒
            SettingsGroupCard {
                SettingsSwitchRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.settings_subscription_reminder),
                    subtitle = stringResource(R.string.settings_subscription_reminder_summary),
                    checked = uiState.subscriptionReminder,
                    onCheckedChange = { enabled -> viewModel.setSubscriptionReminder(enabled) }
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    icon = Icons.Outlined.Insights,
                    title = stringResource(R.string.settings_watch_stats),
                    subtitle = stringResource(R.string.settings_watch_stats_summary),
                    checked = uiState.watchStatsEnabled,
                    onCheckedChange = { enabled -> viewModel.setWatchStatsEnabled(enabled) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 网络与数据：镜像站 / 诊断 / 缓存
            SettingsGroupCard {
                SettingsNavRow(
                    icon = Icons.Outlined.Link,
                    title = stringResource(R.string.settings_base_url),
                    subtitle = uiState.baseUrl,
                    onClick = {
                        baseUrlInput = uiState.baseUrl
                        showBaseUrlDialog = true
                    }
                )
                SettingsGroupDivider()
                SettingsNavRow(
                    icon = Icons.Outlined.NetworkCheck,
                    title = stringResource(R.string.settings_diagnostics),
                    onClick = { onNavigate("diagnostics") }
                )
                SettingsGroupDivider()
                SettingsNavRow(
                    icon = Icons.Outlined.CleaningServices,
                    title = stringResource(R.string.settings_clear_cache),
                    onClick = { showClearCacheDialog = true }
                )
                SettingsGroupDivider()
                SettingsNavRow(
                    icon = Icons.Outlined.Backup,
                    title = stringResource(R.string.backup_entry),
                    subtitle = stringResource(R.string.backup_entry_summary),
                    onClick = { onNavigate("backup") }
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
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
                    // 成功/失败 Toast 由 SettingsViewModel 经 events 通道在删除完成后发送
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
                                text = SettingsRepository.DEFAULT_BASE_URL,
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
                    // 非法输入会被清洗成默认地址，据返回值提示用户真实结果
                    val applied = viewModel.setBaseUrl(baseUrlInput)
                    showBaseUrlDialog = false
                    Toast.makeText(
                        context,
                        context.getString(
                            if (applied) R.string.settings_base_url_updated
                            else R.string.settings_base_url_invalid
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text(stringResource(R.string.common_save), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.restoreDefaultBaseUrl()
                    baseUrlInput = SettingsRepository.DEFAULT_BASE_URL
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
