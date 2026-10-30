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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
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
import app.amisles.hanime.core.ui.theme.CardConfig
import app.amisles.hanime.core.ui.theme.CardStyle
import app.amisles.hanime.core.ui.theme.ContrastLevel
import app.amisles.hanime.core.ui.theme.ContinueCardStyle
import app.amisles.hanime.core.ui.theme.CornerMode
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.NavigationStyle
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.ThumbnailQuality
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

    var draftConfig by remember { mutableStateOf(appliedConfig) }
    var draftSections by remember { mutableStateOf(appliedSections) }
    var draftRelatedLayout by remember { mutableStateOf(relatedLayout) }
    var previewHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val updateConfig: ((UiConfig) -> UiConfig) -> Unit = { transform ->
        draftConfig = transform(draftConfig).sanitized()
    }
    val updateSections: ((HomeSectionConfig) -> HomeSectionConfig) -> Unit = { transform ->
        draftSections = transform(draftSections).sanitized()
    }

    val dirty = draftConfig != appliedConfig ||
        draftSections != appliedSections ||
        draftRelatedLayout != relatedLayout

    val applyDraft: () -> Unit = {
        UiConfigStore.set(draftConfig)
        UiConfigStore.setHomeSections(draftSections)
        if (draftRelatedLayout != relatedLayout) {
            val target = draftRelatedLayout
            if (target == null) appPrefs.clearRelatedLayoutMode() else appPrefs.setRelatedLayoutMode(target)
        }
        Toast.makeText(context, context.getString(R.string.customize_apply_done), Toast.LENGTH_SHORT).show()
    }
    val revertDraft: () -> Unit = {
        draftConfig = appliedConfig
        draftSections = appliedSections
        draftRelatedLayout = relatedLayout
    }

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        if (dirty) showDiscardDialog = true else onBackClick()
    }
    BackHandler(enabled = dirty) { showDiscardDialog = true }

    val config = draftConfig
    val homeSections = draftSections
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

                item(key = "navigation") {
                GroupLabel(stringResource(R.string.customize_group_navigation))
                SettingsGroupCard {
                    Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.customize_nav_style),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (config.navigationStyle != defaults.navigationStyle) {
                                IconAction(
                                    icon = Icons.Default.RestartAlt,
                                    enabled = true,
                                    labelRes = R.string.customize_reset_item,
                                    onClick = {
                                        updateConfig {
                                            it.copy(navigationStyle = defaults.navigationStyle)
                                        }
                                    },
                                    boxSize = 30.dp,
                                    iconSize = 17.dp
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.customize_nav_style_hint),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NavigationStyle.entries.forEach { style ->
                                ChosenChip(
                                    selected = style == config.navigationStyle,
                                    label = stringResource(navStyleLabelRes(style)),
                                    onClick = { updateConfig { it.copy(navigationStyle = style) } },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(GROUP_GAP))
            }

            item(key = "appearance") {
                    GroupLabel(stringResource(R.string.customize_group_appearance))
                    SettingsGroupCard {
                        Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.customize_seed),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(seedLabelRes(config.appearance.seed)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (config.appearance.seed != defaults.appearance.seed) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    IconAction(
                                        icon = Icons.Default.RestartAlt,
                                        enabled = true,
                                        labelRes = R.string.customize_reset_item,
                                        onClick = {
                                            updateConfig {
                                                it.copy(appearance = it.appearance.copy(seed = defaults.appearance.seed))
                                            }
                                        },
                                        boxSize = 30.dp,
                                        iconSize = 17.dp
                                    )
                                }
                            }
                            SeedRow(
                                selected = config.appearance.seed,
                                onSelect = { seed ->
                                    updateConfig { it.copy(appearance = it.appearance.copy(seed = seed)) }
                                }
                            )
                        }
                        RowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.DarkMode,
                            title = stringResource(R.string.customize_amoled),
                            subtitle = stringResource(R.string.customize_amoled_summary),
                            checked = config.appearance.amoled,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(appearance = it.appearance.copy(amoled = enabled)) }
                            }
                        )
                        RowDivider()
                        Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.customize_contrast),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                if (config.appearance.contrast != defaults.appearance.contrast) {
                                    IconAction(
                                        icon = Icons.Default.RestartAlt,
                                        enabled = true,
                                        labelRes = R.string.customize_reset_item,
                                        onClick = {
                                            updateConfig {
                                                it.copy(
                                                    appearance = it.appearance.copy(contrast = defaults.appearance.contrast)
                                                )
                                            }
                                        },
                                        boxSize = 30.dp,
                                        iconSize = 17.dp
                                    )
                                }
                            }
                            TwoWaySegment(
                                firstLabel = stringResource(R.string.customize_contrast_standard),
                                secondLabel = stringResource(R.string.customize_contrast_high),
                                firstSelected = config.appearance.contrast == ContrastLevel.STANDARD,
                                onSelectFirst = {
                                    updateConfig {
                                        it.copy(appearance = it.appearance.copy(contrast = ContrastLevel.STANDARD))
                                    }
                                },
                                onSelectSecond = {
                                    updateConfig {
                                        it.copy(appearance = it.appearance.copy(contrast = ContrastLevel.HIGH))
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

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
                                if (draftRelatedLayout != null) {
                                    Text(
                                        text = stringResource(R.string.customize_reset),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { draftRelatedLayout = null }
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
                                firstSelected = draftRelatedLayout != RelatedLayoutMode.GRID,
                                onSelectFirst = { draftRelatedLayout = RelatedLayoutMode.LIST },
                                onSelectSecond = { draftRelatedLayout = RelatedLayoutMode.GRID }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "typography") {
                    GroupLabel(stringResource(R.string.customize_group_typography))
                    SettingsGroupCard {
                        DecimalInputRow(
                            title = stringResource(R.string.customize_font_scale),
                            value = config.fontScale,
                            range = FONT_SCALE_RANGE,
                            unit = "×",
                            hint = stringResource(
                                R.string.customize_font_scale_hint,
                                formatDecimal(FONT_SCALE_RANGE.start),
                                formatDecimal(FONT_SCALE_RANGE.endInclusive)
                            ),
                            isDefault = config.fontScale == defaults.fontScale,
                            onReset = { updateConfig { it.copy(fontScale = defaults.fontScale) } },
                            onCommit = { v -> updateConfig { it.copy(fontScale = v) } }
                        )
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "card") {
                    GroupLabel(stringResource(R.string.customize_group_card))
                    SettingsGroupCard {
                        SliderRow(
                            title = stringResource(R.string.customize_aspect_ratio),
                            valueText = String.format(Locale.US, "%.2f:1", config.thumbnailAspectRatio),
                            value = config.thumbnailAspectRatio,
                            range = 1f..2f,
                            defaultValue = defaults.thumbnailAspectRatio,
                            isDefault = config.thumbnailAspectRatio == defaults.thumbnailAspectRatio,
                            onReset = {
                                updateConfig { it.copy(thumbnailAspectRatio = defaults.thumbnailAspectRatio) }
                            },
                            onPreview = { v ->
                                updateConfig { it.copy(thumbnailAspectRatio = (v * 100).roundToInt() / 100f) }
                            },
                            onCommit = { v ->
                                updateConfig { it.copy(thumbnailAspectRatio = (v * 100).roundToInt() / 100f) }
                            }
                        )
                        RowDivider()
                        SliderRow(
                            title = stringResource(R.string.customize_card_radius),
                            valueText = "${config.cardCornerRadius.value.roundToInt()}dp",
                            value = config.cardCornerRadius.value,
                            range = 0f..24f,
                            defaultValue = defaults.cardCornerRadius.value,
                            isDefault = config.cardCornerRadius == defaults.cardCornerRadius,
                            onReset = { updateConfig { it.copy(cardCornerRadius = defaults.cardCornerRadius) } },
                            onPreview = { v ->
                                updateConfig { it.copy(cardCornerRadius = v.roundToInt().toFloat().dp) }
                            },
                            onCommit = { v ->
                                updateConfig { it.copy(cardCornerRadius = v.roundToInt().toFloat().dp) }
                            }
                        )
                        RowDivider()
                        SliderRow(
                            title = stringResource(R.string.customize_list_thumb_width),
                            valueText = "${config.listThumbnailWidth.value.roundToInt()}dp",
                            value = config.listThumbnailWidth.value,
                            range = 100f..200f,
                            defaultValue = defaults.listThumbnailWidth.value,
                            isDefault = config.listThumbnailWidth == defaults.listThumbnailWidth,
                            onReset = { updateConfig { it.copy(listThumbnailWidth = defaults.listThumbnailWidth) } },
                            onPreview = { v ->
                                updateConfig { it.copy(listThumbnailWidth = v.roundToInt().toFloat().dp) }
                            },
                            onCommit = { v ->
                                updateConfig { it.copy(listThumbnailWidth = v.roundToInt().toFloat().dp) }
                            }
                        )
                        RowDivider()
                        ChoiceRow(
                            title = stringResource(R.string.customize_image_quality),
                            options = ThumbnailQuality.entries.map { it to stringResource(qualityLabelRes(it)) },
                            selected = config.thumbnailQuality,
                            isDefault = config.thumbnailQuality == defaults.thumbnailQuality,
                            onReset = {
                                updateConfig { it.copy(thumbnailQuality = defaults.thumbnailQuality) }
                            },
                            onSelect = { quality -> updateConfig { it.copy(thumbnailQuality = quality) } }
                        )
                        RowDivider()
                        ChoiceRow(
                            title = stringResource(R.string.customize_card_style),
                            options = CardStyle.entries.map { it to stringResource(cardStyleLabelRes(it)) },
                            selected = config.card.style,
                            isDefault = config.card.style == defaults.card.style,
                            onReset = {
                                updateConfig { it.copy(card = it.card.copy(style = defaults.card.style)) }
                            },
                            onSelect = { style -> updateConfig { it.copy(card = it.card.copy(style = style)) } }
                        )
                        RowDivider()
                        ChoiceRow(
                            title = stringResource(R.string.customize_card_title_lines),
                            options = (CardConfig.MIN_TITLE_LINES..CardConfig.MAX_TITLE_LINES).map { lines ->
                                lines to stringResource(R.string.customize_card_title_lines_value, lines)
                            },
                            selected = config.card.titleLines,
                            isDefault = config.card.titleLines == defaults.card.titleLines,
                            onReset = {
                                updateConfig { it.copy(card = it.card.copy(titleLines = defaults.card.titleLines)) }
                            },
                            onSelect = { lines -> updateConfig { it.copy(card = it.card.copy(titleLines = lines)) } }
                        )
                        RowDivider()
                        Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.customize_card_corner_mode),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                if (config.card.cornerMode != defaults.card.cornerMode) {
                                    IconAction(
                                        icon = Icons.Default.RestartAlt,
                                        enabled = true,
                                        labelRes = R.string.customize_reset_item,
                                        onClick = {
                                            updateConfig {
                                                it.copy(card = it.card.copy(cornerMode = defaults.card.cornerMode))
                                            }
                                        },
                                        boxSize = 30.dp,
                                        iconSize = 17.dp
                                    )
                                }
                            }
                            TwoWaySegment(
                                firstLabel = stringResource(R.string.customize_card_corner_all),
                                secondLabel = stringResource(R.string.customize_card_corner_top),
                                firstSelected = config.card.cornerMode == CornerMode.ALL,
                                onSelectFirst = {
                                    updateConfig { it.copy(card = it.card.copy(cornerMode = CornerMode.ALL)) }
                                },
                                onSelectSecond = {
                                    updateConfig { it.copy(card = it.card.copy(cornerMode = CornerMode.TOP)) }
                                }
                            )
                        }
                        RowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Person,
                            title = stringResource(R.string.customize_card_show_author),
                            checked = config.card.showAuthor,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(card = it.card.copy(showAuthor = enabled)) }
                            }
                        )
                        RowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Schedule,
                            title = stringResource(R.string.customize_card_show_time),
                            checked = config.card.showPublishTime,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(card = it.card.copy(showPublishTime = enabled)) }
                            }
                        )
                        RowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Label,
                            title = stringResource(R.string.customize_card_show_badges),
                            checked = config.card.showBadges,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(card = it.card.copy(showBadges = enabled)) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

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

                item(key = "nav_labels") {
                    SettingsGroupCard {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.TextFields,
                            title = stringResource(R.string.customize_nav_labels),
                            subtitle = stringResource(R.string.customize_nav_labels_summary),
                            checked = config.navBarShowLabels,
                            onCheckedChange = { enabled ->
                                updateConfig { it.copy(navBarShowLabels = enabled) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

                item(key = "grid") {
                    GroupLabel(stringResource(R.string.customize_group_grid))
                    SettingsGroupCard {
                        SliderRow(
                            title = stringResource(R.string.customize_grid_target_width),
                            valueText = "${config.gridTargetWidth.value.roundToInt()}dp",
                            value = config.gridTargetWidth.value,
                            range = 96f..320f,
                            defaultValue = defaults.gridTargetWidth.value,
                            isDefault = config.gridTargetWidth == defaults.gridTargetWidth,
                            onReset = { updateConfig { it.copy(gridTargetWidth = defaults.gridTargetWidth) } },
                            onPreview = { v ->
                                updateConfig { it.copy(gridTargetWidth = v.roundToInt().toFloat().dp) }
                            },
                            onCommit = { v ->
                                updateConfig { it.copy(gridTargetWidth = v.roundToInt().toFloat().dp) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(GROUP_GAP))
                }

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
                                copyToClipboard(context, UiConfigCodec.export(draftConfig, draftSections))
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
                draftConfig = UiConfig.Default
                draftSections = HomeSectionConfig.Default
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
                                draftConfig = UiConfigStore.config.value
                                draftSections = UiConfigStore.homeSections.value
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
