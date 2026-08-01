package app.amisles.hanime.feature.settings

import android.content.Context
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
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.data.preferences.Preferences
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary

data class LanguageOption(val code: String, val label: String)

val languageOptions = listOf(
    LanguageOption("zhs", "简体中文"),
    LanguageOption("zh", "繁體中文"),
    LanguageOption("ja", "日本語"),
    LanguageOption("en", "English")
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val videoLanguage by Preferences.videoLanguageFlow.collectAsState()
    val maxDownloadConcurrent by Preferences.maxDownloadConcurrentFlow.collectAsState()
    val baseUrl by Preferences.baseUrlFlow.collectAsState()
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var downloadConcurrentMenuExpanded by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var baseUrlInput by remember { mutableStateOf(baseUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HanimeBackground)
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
                    contentDescription = "返回",
                    tint = HanimeTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "设置",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = HanimeTextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = HanimeCard),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { languageMenuExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = HanimePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "视频语言",
                        fontSize = 15.sp,
                        color = HanimeTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = languageOptions.find { it.code == videoLanguage }?.label ?: "简体中文",
                        fontSize = 14.sp,
                        color = HanimeTextSecondary
                    )
                    Icon(
                        imageVector = if (languageMenuExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                        contentDescription = null,
                        tint = HanimeTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                    offset = DpOffset(x = 0.dp, y = 0.dp)
                ) {
                    languageOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontSize = 14.sp,
                                    color = if (option.code == videoLanguage) HanimePrimary else HanimeTextSecondary,
                                    fontWeight = if (option.code == videoLanguage) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            onClick = {
                                Preferences.setVideoLanguage(option.code)
                                languageMenuExpanded = false
                                Toast.makeText(context, "语言已切换为${option.label}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = HanimeCard),
            shape = RoundedCornerShape(10.dp)
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { downloadConcurrentMenuExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = HanimePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "同时下载数",
                        fontSize = 15.sp,
                        color = HanimeTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${maxDownloadConcurrent}个",
                        fontSize = 14.sp,
                        color = HanimeTextSecondary
                    )
                    Icon(
                        imageVector = if (downloadConcurrentMenuExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                        contentDescription = null,
                        tint = HanimeTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = downloadConcurrentMenuExpanded,
                    onDismissRequest = { downloadConcurrentMenuExpanded = false },
                    modifier = Modifier.background(HanimeCard),
                    offset = DpOffset(x = 0.dp, y = 0.dp)
                ) {
                    (1..5).forEach { count ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${count}个",
                                    fontSize = 14.sp,
                                    color = if (count == maxDownloadConcurrent) HanimePrimary else HanimeTextSecondary,
                                    fontWeight = if (count == maxDownloadConcurrent) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            onClick = {
                                Preferences.setMaxDownloadConcurrent(count)
                                downloadConcurrentMenuExpanded = false
                                Toast.makeText(context, "已设置为${count}个同时下载", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp)
                .clickable {
                    baseUrlInput = baseUrl
                    showBaseUrlDialog = true
                },
            colors = CardDefaults.cardColors(containerColor = HanimeCard),
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
                    tint = HanimePrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "官网网址",
                        fontSize = 15.sp,
                        color = HanimeTextPrimary
                    )
                    Text(
                        text = baseUrl,
                        fontSize = 12.sp,
                        color = HanimeTextSecondary,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = HanimeTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 6.dp)
                .clickable { showClearCacheDialog = true },
            colors = CardDefaults.cardColors(containerColor = HanimeCard),
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
                    tint = HanimePrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "清除缓存",
                    fontSize = 15.sp,
                    color = HanimeTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = HanimeTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除缓存", color = HanimeTextPrimary) },
            text = {
                Text(
                    "确定要清除应用缓存吗？此操作不会删除下载的视频和收藏记录。",
                    color = HanimeTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearAppCache(context)
                    showClearCacheDialog = false
                    Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                }) {
                    Text("确定", color = HanimePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消", color = HanimeTextSecondary)
                }
            },
            containerColor = HanimeCard
        )
    }

    if (showBaseUrlDialog) {
        AlertDialog(
            onDismissRequest = { showBaseUrlDialog = false },
            title = { Text("官网网址", color = HanimeTextPrimary) },
            text = {
                Column {
                    Text(
                        "请输入官网地址，留空则恢复默认",
                        color = HanimeTextSecondary,
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
                                color = HanimeTextSecondary
                            )
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = HanimeTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Preferences.setBaseUrl(baseUrlInput)
                    showBaseUrlDialog = false
                    Toast.makeText(context, "官网网址已更新", Toast.LENGTH_SHORT).show()
                }) {
                    Text("保存", color = HanimePrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    baseUrlInput = Preferences.DEFAULT_BASE_URL
                    Preferences.setBaseUrl(Preferences.DEFAULT_BASE_URL)
                    showBaseUrlDialog = false
                    Toast.makeText(context, "已恢复默认网址", Toast.LENGTH_SHORT).show()
                }) {
                    Text("恢复默认", color = HanimeTextSecondary)
                }
            },
            containerColor = HanimeCard
        )
    }
}

private fun clearAppCache(context: Context) {
    try {
        val cacheDir = context.cacheDir
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}