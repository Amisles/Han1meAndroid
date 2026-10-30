package app.amisles.hanime.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.components.ScreenHeader
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SettingsGroupCard
import app.amisles.hanime.core.ui.components.SettingsSwitchRow
import app.amisles.hanime.core.ui.model.homeSectionTitleResMap
import app.amisles.hanime.core.ui.theme.ContinueCardStyle
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.UiConfigCodec
import app.amisles.hanime.core.ui.theme.UiConfigImport
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.core.ui.theme.UiPreset
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/**
 * 界面定制页
 *
 * 顶部标题栏 + 可滚动设置区，预览区按窗口宽度换位：宽屏固定在右侧，窄屏浮在设置区底部，
 * 设置区用 contentPadding 让出预览高度，避免内容被永久遮挡。
 *
 * 所有调整先写进草稿并即时反映在预览区，「应用」才落盘、「还原」丢弃本次修改；
 * 带未应用的修改时返回会先确认，避免调了半天的配置被误丢。
 */
@Composable
fun CustomizeScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val appPrefs = LocalAppPreferences.current
    val sizeInfo = currentWindowSizeInfo()
    val wide = sizeInfo.isTablet || sizeInfo.widthDp >= 720
    val appliedConfig by UiConfigStore.config.collectAsStateWithLifecycle()
    val appliedSections by UiConfigStore.homeSections.collectAsStateWithLifecycle()
    val relatedLayout by appPrefs.relatedLayoutModeFlow.collectAsStateWithLifecycle()

    val draft = rememberCustomizeDraft(appliedConfig, appliedSections, relatedLayout)
    var previewHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val updateConfig: ((UiConfig) -> UiConfig) -> Unit = { transform -> draft.updateConfig(transform) }
    val updateSections: ((HomeSectionConfig) -> HomeSectionConfig) -> Unit = { transform -> draft.updateSections(transform) }

    val dirty = draft.dirty

    val applyDraft: () -> Unit = {
        UiConfigStore.set(draft.config)
        UiConfigStore.setHomeSections(draft.sections)
        if (draft.relatedLayout != relatedLayout) {
            val target = draft.relatedLayout
            if (target == null) appPrefs.clearRelatedLayoutMode() else appPrefs.setRelatedLayoutMode(target)
        }
        Toast.makeText(context, context.getString(R.string.customize_apply_done), Toast.LENGTH_SHORT).show()
    }
    val revertDraft: () -> Unit = { draft.revert() }

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (dirty) showDiscardDialog = true else onBackClick()
    }
    BackHandler(enabled = dirty) { showDiscardDialog = true }

    val config = draft.config
    val homeSections = draft.sections
    val activePreset = remember(config) { UiPreset.of(config) }
    val defaults = UiConfig.Default
    val sectionDefaults = HomeSectionConfig.Default
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importInput by rememberSaveable { mutableStateOf("") }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var expandedSection by rememberSaveable { mutableStateOf("") }
    val knownSectionTitles = remember { homeSectionTitleResMap.keys.toList() }
    val sectionOrder = remember(homeSections.order, knownSectionTitles) {
        val stored = homeSections.order.filter { it in knownSectionTitles }
        stored + knownSectionTitles.filterNot { it in stored }
    }
    val moveSection: (Int, Int) -> Unit = { index, delta ->
        val target = index + delta
        if (target in sectionOrder.indices) {
            val next = sectionOrder.toMutableList()
            val moved = next[index]
            next[index] = next[target]
            next[target] = moved
            updateSections { it.copy(order = next) }
        }
    }

    val sampleTitle = stringResource(R.string.customize_sample_title)
    val sampleAuthor = stringResource(R.string.customize_sample_author)
    val previewVideo = remember(sampleTitle, sampleAuthor) {
        HanimeVideo(
            id = "preview",
            title = sampleTitle,
            thumbnailUrl = "",
            duration = "12:34",
            likeRate = "98%",
            viewCount = "12.3K",
            author = sampleAuthor,
            publishTime = "3d",
            videoUrl = ""
        )
    }

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = stringResource(R.string.customize_title),
            onBackClick = requestBack,
            actions = {
                IconAction(
                    icon = Icons.Default.RestartAlt,
                    enabled = true,
                    labelRes = R.string.customize_reset,
                    onClick = { showResetDialog = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (wide) PREVIEW_PANE_WIDTH else 0.dp),
                contentPadding = PaddingValues(
                    bottom = if (wide) 24.dp else previewHeight + 10.dp
                )
            ) {
                item(key = "presets") {
                    GroupLabel(stringResource(R.string.customize_presets))
                    PresetSegment(
                        presets = UiPreset.entries,
                        selected = activePreset,
                        labelFor = { stringResource(presetLabelRes(it)) },
                        onSelect = { preset -> updateConfig { preset.config } }
                    )
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "navigation") { NavigationSection(config, defaults, updateConfig) }

                item(key = "appearance") { AppearanceSection(config, defaults, updateConfig) }

                item(key = "related-layout") {
                    GroupLabel(stringResource(R.string.customize_group_related))
                    SettingsGroupCard {
                        Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.customize_related_layout),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                if (draft.relatedLayout != null) {
                                    Text(
                                        text = stringResource(R.string.customize_reset),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { draft.setRelatedLayout(null) }
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.customize_related_layout_hint),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp, bottom = 8.dp)
                            )
                            TwoWaySegment(
                                firstLabel = stringResource(R.string.customize_layout_list),
                                secondLabel = stringResource(R.string.customize_layout_grid),
                                firstSelected = draft.relatedLayout != RelatedLayoutMode.GRID,
                                onSelectFirst = { draft.setRelatedLayout(RelatedLayoutMode.LIST) },
                                onSelectSecond = { draft.setRelatedLayout(RelatedLayoutMode.GRID) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "typography") { TypographySection(config, defaults, updateConfig) }

                item(key = "card") { CardSection(config, defaults, updateConfig) }

                item(key = "banner") {
                    GroupLabel(stringResource(R.string.customize_group_banner))
                    SettingsGroupCard {
                        SliderRow(
                            title = stringResource(R.string.customize_banner_height),
                            valueText = "${config.bannerHeight.value.roundToInt()}dp",
                            value = config.bannerHeight.value,
                            range = 180f..360f,
                            defaultValue = defaults.bannerHeight.value,
                            isDefault = config.bannerHeight == defaults.bannerHeight,
                            onReset = { updateConfig { it.copy(bannerHeight = defaults.bannerHeight) } },
                            onPreview = { v ->
                                updateConfig { it.copy(bannerHeight = v.roundToInt().toFloat().dp) }
                            },
                            onCommit = { v ->
                                updateConfig { it.copy(bannerHeight = v.roundToInt().toFloat().dp) }
                            }
                        )
                        RowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.ViewCarousel,
                            title = stringResource(R.string.customize_banner_tags),
                            checked = config.bannerShowTags,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(bannerShowTags = enabled) }
                            }
                        )
                        if (config.bannerShowTags) {
                            RowDivider()
                            SliderRow(
                                title = stringResource(R.string.customize_banner_max_tags),
                                valueText = "${config.bannerMaxTags}",
                                value = config.bannerMaxTags.toFloat(),
                                range = 0f..8f,
                                defaultValue = defaults.bannerMaxTags.toFloat(),
                                isDefault = config.bannerMaxTags == defaults.bannerMaxTags,
                                ticks = 9,
                                onReset = { updateConfig { it.copy(bannerMaxTags = defaults.bannerMaxTags) } },
                                onPreview = { v ->
                                    updateConfig { it.copy(bannerMaxTags = v.roundToInt()) }
                                },
                                onCommit = { v ->
                                    updateConfig { it.copy(bannerMaxTags = v.roundToInt()) }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "home_header") {
                    GroupLabel(stringResource(R.string.customize_group_home_header))
                    SettingsGroupCard {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Home,
                            title = stringResource(R.string.customize_compact_header),
                            subtitle = stringResource(R.string.customize_compact_header_summary),
                            checked = config.compactHomeHeader,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(compactHomeHeader = enabled) }
                            }
                        )
                        RowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Category,
                            title = stringResource(R.string.customize_category_bar),
                            subtitle = stringResource(R.string.customize_category_bar_summary),
                            checked = config.homeShowCategoryBar,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(homeShowCategoryBar = enabled) }
                            }
                        )
                        RowDivider()
                        Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.customize_continue_style),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                if (config.continueCardStyle != defaults.continueCardStyle) {
                                    IconAction(
                                        icon = Icons.Default.RestartAlt,
                                        enabled = true,
                                        labelRes = R.string.customize_reset_item,
                                        onClick = {
                                            updateConfig {
                                                it.copy(continueCardStyle = defaults.continueCardStyle)
                                            }
                                        },
                                        boxSize = 30.dp,
                                        iconSize = 17.dp
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ContinueCardStyle.entries.forEach { style ->
                                    ChosenChip(
                                        selected = style == config.continueCardStyle,
                                        label = stringResource(continueStyleLabelRes(style)),
                                        onClick = {
                                            updateConfig { it.copy(continueCardStyle = style) }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "nav_labels") { NavLabelsSection(config, defaults, updateConfig) }

                item(key = "grid") { GridSection(config, defaults, updateConfig) }

                item(key = "home_sections") {
                    GroupLabel(stringResource(R.string.customize_group_home_sections))
                    SettingsGroupCard {
                        SliderRow(
                            title = stringResource(R.string.customize_section_visible_count),
                            valueText = "${homeSections.visibleCount}",
                            value = homeSections.visibleCount.toFloat(),
                            range = HomeSectionConfig.MIN_VISIBLE_COUNT.toFloat()..HomeSectionConfig.MAX_VISIBLE_COUNT.toFloat(),
                            defaultValue = sectionDefaults.visibleCount.toFloat(),
                            isDefault = homeSections.visibleCount == sectionDefaults.visibleCount,
                            ticks = HomeSectionConfig.MAX_VISIBLE_COUNT - HomeSectionConfig.MIN_VISIBLE_COUNT + 1,
                            onReset = {
                                updateSections { it.copy(visibleCount = sectionDefaults.visibleCount) }
                            },
                            onPreview = { v ->
                                updateSections { it.copy(visibleCount = v.roundToInt()) }
                            },
                            onCommit = { v ->
                                updateSections { it.copy(visibleCount = v.roundToInt()) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(CARD_GAP))
                    SectionOrderCard(
                        order = sectionOrder,
                        homeSections = homeSections,
                        titleFor = { homeSectionTitleResMap[it]?.let { res -> stringResource(res) } ?: it },
                        expandedTitle = expandedSection,
                        onToggleExpand = { title ->
                            expandedSection = if (expandedSection == title) "" else title
                        },
                        onMove = moveSection,
                        onLayoutChange = { title, mode ->
                            updateSections { cfg ->
                                val next = cfg.layouts.toMutableMap()
                                if (mode == HomeSectionLayout.AUTO) {
                                    next.remove(title)
                                } else {
                                    next[title] = mode
                                }
                                cfg.copy(layouts = next)
                            }
                        },
                        onHideChange = { title, hidden ->
                            updateSections { cfg ->
                                cfg.copy(hidden = if (hidden) cfg.hidden + title else cfg.hidden - title)
                            }
                        },
                        onPinChange = { title, pinned ->
                            updateSections { cfg ->
                                cfg.copy(
                                    pinned = if (pinned) {
                                        (listOf(title) + cfg.pinned).distinct()
                                    } else {
                                        cfg.pinned - title
                                    }
                                )
                            }
                        },
                        onCountChange = { title, count ->
                            updateSections { cfg ->
                                cfg.copy(visibleCounts = cfg.visibleCounts + (title to count))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "backup") {
                    GroupLabel(stringResource(R.string.customize_group_backup))
                    SettingsGroupCard {
                        ActionRow(
                            icon = Icons.Default.Share,
                            title = stringResource(R.string.customize_export),
                            onClick = {
                                copyToClipboard(context, UiConfigCodec.export(draft.config, draft.sections))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.customize_export_done),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        RowDivider()
                        ActionRow(
                            icon = Icons.Default.ContentCopy,
                            title = stringResource(R.string.customize_import),
                            onClick = {
                                importInput = readClipboardIfConfig(context)
                                showImportDialog = true
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "notice") {
                    OverflowNotice()
                }
            }

            PreviewPane(
                config = config,
                video = previewVideo,
                presetLabel = activePreset?.let { stringResource(presetLabelRes(it)) },
                fontScaleText = String.format(Locale.US, "%.2f×", config.fontScale),
                wide = wide,
                modifier = if (wide) {
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(PREVIEW_PANE_WIDTH)
                        .padding(start = 4.dp, end = 15.dp, top = 4.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 6.dp)
                        .onSizeChanged { previewHeight = with(density) { it.height.toDp() } }
                }
            )
        }

        ActionBar(dirty = dirty, onRevert = revertDraft, onApply = applyDraft)
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(R.string.customize_reset),
            message = stringResource(R.string.customize_reset_confirm),
            confirmLabel = stringResource(R.string.common_confirm),
            onConfirm = {
                UiConfigStore.reset()
                draft.replace(UiConfig.Default, HomeSectionConfig.Default)
                Toast.makeText(context, context.getString(R.string.customize_reset_done), Toast.LENGTH_SHORT).show()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.customize_import)) },
            text = {
                OutlinedTextField(
                    value = importInput,
                    onValueChange = { importInput = it },
                    placeholder = { Text(stringResource(R.string.customize_import_hint)) },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val result = UiConfigStore.importFromText(importInput)
                        val messageRes = when (result) {
                            is UiConfigImport.Success -> {
                                draft.replace(UiConfigStore.config.value, UiConfigStore.homeSections.value)
                                R.string.customize_import_done
                            }
                            UiConfigImport.UnsupportedVersion -> R.string.customize_import_version
                            UiConfigImport.InvalidFormat -> R.string.customize_import_invalid
                        }
                        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
                        showImportDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.customize_discard_title)) },
            text = { Text(stringResource(R.string.customize_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        revertDraft()
                        onBackClick()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.customize_discard_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/* ───────────────────────── 预览区 ───────────────────────── */
