package app.amisles.hanime.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.HanimeSwitch
import app.amisles.hanime.core.ui.theme.HanimeDanger
import app.amisles.hanime.domain.model.AppUpdate

/** 把字节数格式化成 `12.3 MB`；0 或负数返回空串（调用方据此隐藏该行）。 */
internal fun formatApkSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    } else {
        String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

/**
 * 在浏览器中打开发行版页面 / 安装包直链。
 *
 * 刻意声明为 public（模块可见）：启动时的更新弹窗位于 :app 模块，
 * 需要一个与更新页完全一致的跳转实现，重复实现会带来行为漂移。
 */
fun openReleasePage(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
fun UpdateScreen(
    onBackClick: () -> Unit = {},
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    val skippedVersion by viewModel.skippedVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶栏
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
                text = stringResource(R.string.update_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { viewModel.check(force = true) },
                enabled = uiState !is UpdateUiState.Checking
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.update_check_now),
                    tint = if (uiState is UpdateUiState.Checking) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 当前版本
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_current_version),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = viewModel.currentVersion,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 检查结果区
            when (val state = uiState) {
                is UpdateUiState.Checking -> StatusCard(
                    icon = null,
                    title = stringResource(R.string.update_checking),
                    showProgress = true
                )

                is UpdateUiState.UpToDate -> StatusCard(
                    icon = Icons.Default.CheckCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.update_up_to_date),
                    subtitle = stringResource(R.string.update_up_to_date_desc)
                )

                is UpdateUiState.Failed -> StatusCard(
                    icon = Icons.Default.ErrorOutline,
                    iconTint = HanimeDanger,
                    title = stringResource(R.string.update_check_failed),
                    subtitle = state.message,
                    action = {
                        TextButton(onClick = { viewModel.check(force = true) }) {
                            Text(stringResource(R.string.update_retry))
                        }
                    }
                )

                is UpdateUiState.Available -> UpdateAvailableCard(
                    update = state.update,
                    currentVersion = state.currentVersion,
                    onDownload = { openReleasePage(context, state.update.apkUrl.ifBlank { state.update.htmlUrl }) },
                    onOpenRelease = { openReleasePage(context, state.update.htmlUrl) },
                    onSkip = { viewModel.skipCurrentVersion() }
                )

                // 被跳过的版本与「已是最新」必须分开呈现：
                // 用户看到「已是最新」会以为真的没有新版本，而事实是他此前主动忽略了这一版。
                is UpdateUiState.Skipped -> UpdateAvailableCard(
                    update = state.update,
                    currentVersion = state.currentVersion,
                    onDownload = { openReleasePage(context, state.update.apkUrl.ifBlank { state.update.htmlUrl }) },
                    onOpenRelease = { openReleasePage(context, state.update.htmlUrl) },
                    onSkip = { viewModel.clearSkippedVersion() },
                    skipped = true
                )

                UpdateUiState.Idle -> Unit
            }

            Spacer(Modifier.height(20.dp))

            // 偏好设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.update_auto_check),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.update_auto_check_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        HanimeSwitch(
                            checked = autoCheck,
                            onCheckedChange = { viewModel.setAutoCheckEnabled(it) }
                        )
                    }

                    // 仅在确有「跳过」记录时才显示清除入口，避免常态下一堆无效选项
                    if (skippedVersion.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(horizontal = 16.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.update_skipped_version),
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(
                                        R.string.update_skipped_version_desc,
                                        skippedVersion
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            TextButton(onClick = { viewModel.clearSkippedVersion() }) {
                                Text(stringResource(R.string.update_clear_skip))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 中性的状态卡片：检查中 / 已最新 / 失败共用。 */
@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    showProgress: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            action?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}

/** 有可用更新时的卡片：版本号、体积、变更说明与三个动作。 */
@Composable
private fun UpdateAvailableCard(
    update: AppUpdate,
    currentVersion: String,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit,
    onSkip: () -> Unit,
    /** true 表示该版本已被用户跳过，卡片以「已忽略」的措辞呈现。 */
    skipped: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (skipped) R.string.update_skipped_notice else R.string.update_available
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(
                            R.string.update_version_change,
                            currentVersion,
                            update.versionName
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            val sizeText = formatApkSize(update.apkSizeBytes)
            if (sizeText.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_package_size, sizeText),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            if (update.releaseNotes.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_release_notes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 14.dp)
                )
                // 变更说明是 Markdown 原文，按纯文本展示并限制高度：
                // 渲染 Markdown 需要额外依赖，而这里只要「能让用户看清改了什么」。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(top = 6.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = update.releaseNotes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(
                        if (update.hasApk) R.string.update_download else R.string.update_open_release
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 已忽略时提供「恢复提示」，否则给「不再提示」
                TextButton(onClick = onSkip) {
                    Text(
                        text = stringResource(
                            if (skipped) R.string.update_clear_skip else R.string.update_skip_version
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenRelease) {
                    Text(
                        text = stringResource(R.string.update_view_on_github),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/** 供字体截断场景复用的单行样式（保持与设置页一致）。 */
@Composable
@Suppress("unused")
private fun SingleLineLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
