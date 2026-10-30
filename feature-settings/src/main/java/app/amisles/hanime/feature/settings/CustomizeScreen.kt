package app.amisles.hanime.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.amisles.hanime.core.ui.components.ScreenHeader
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SettingsGroupCard
import app.amisles.hanime.core.ui.components.SettingsSwitchRow
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.model.homeSectionTitleResMap
import app.amisles.hanime.core.ui.theme.CardConfig
import app.amisles.hanime.core.ui.theme.CardStyle
import app.amisles.hanime.core.ui.theme.ContrastLevel
import app.amisles.hanime.core.ui.theme.ContinueCardStyle
import app.amisles.hanime.core.ui.theme.CornerMode
import app.amisles.hanime.core.ui.theme.HanimeSuccess
import app.amisles.hanime.core.ui.theme.HanimeWarning
import app.amisles.hanime.core.ui.theme.LocalAppPreferences
import app.amisles.hanime.core.ui.theme.LocalIsDarkTheme
import app.amisles.hanime.core.ui.theme.NavigationStyle
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.ThemeSeed
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.ThumbnailQuality
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.UiConfigCodec
import app.amisles.hanime.core.ui.theme.UiConfigImport
import app.amisles.hanime.core.ui.theme.UiConfigStore
import app.amisles.hanime.core.ui.theme.UiPreset
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.theme.hanimeColorScheme
import app.amisles.hanime.domain.model.HanimeVideo
import app.amisles.hanime.domain.preferences.RelatedLayoutMode
import java.util.Locale
import kotlin.math.roundToInt

/* 间距常量集中定义，避免 6.5/3.5 这类魔法 dp 散落在布局里。 */
private val SCREEN_H_PADDING = 15.dp
private val CARD_GAP = 6.dp
private val CARD_INNER_H = 16.dp
private val CARD_INNER_V = 12.dp
private val GROUP_GAP = 14.dp
private val PREVIEW_PANE_WIDTH = 320.dp
private val SLIDER_BAR_TOP = 12.dp

/** 与 UiConfig.sanitized() 中 fontScale 的夹取区间保持一致。 */
private val FONT_SCALE_RANGE = 0.85f..1.4f
private val INPUT_BOX_WIDTH = 80.dp
private val INPUT_BOX_HEIGHT = 34.dp
private const val INPUT_MAX_INT_DIGITS = 1
private const val INPUT_MAX_DECIMALS = 2

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

@Composable
private fun PreviewPane(
    config: UiConfig,
    video: HanimeVideo,
    presetLabel: String?,
    fontScaleText: String,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    // 预览区自成一套主题与配置：草稿的配色与卡片样式只在这里生效，应用前的全局界面保持不变
    MaterialTheme(
        colorScheme = hanimeColorScheme(LocalIsDarkTheme.current, config.appearance)
    ) {
        CompositionLocalProvider(LocalUiConfig provides config) {
            Card(
                // 空点击：预览浮层压在列表之上，必须自己吃掉触摸，否则点击会穿透到下方的卡片
                modifier = modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(HanimeSuccess)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.customize_preview),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (presetLabel != null) {
                            Text(
                                text = " · $presetLabel",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = fontScaleText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    VideoListItem(
                        video = video,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                    if (wide) {
                        Spacer(modifier = Modifier.height(8.dp))
                        VideoGridCard(
                            video = video,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────── 底部操作栏 ───────────────────────── */

@Composable
private fun ActionBar(
    dirty: Boolean,
    onRevert: () -> Unit,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = SCREEN_H_PADDING, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRevert,
            enabled = dirty,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = stringResource(R.string.customize_revert), fontSize = 14.sp)
        }
        Button(
            onClick = onApply,
            enabled = dirty,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = stringResource(R.string.customize_apply), fontSize = 14.sp)
        }
    }
}

/* ───────────────────────── 分组容器 ───────────────────────── */

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = SCREEN_H_PADDING + 2.dp,
            top = 2.dp,
            bottom = CARD_GAP + 1.dp
        )
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = CARD_INNER_H),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}

/* ───────────────────────── 预设分段控件 ───────────────────────── */

/**
 * 分段控件：四档预设置于同一容器内，选中项以连通色块表示。
 * 与并列 chip 的差别在于语义 —— 预设是互斥且有限的枚举，
 * 分段控件的"唯一选中"表达比可横滑的 chip 组更准确，也消除了横向滚动。
 */
@Composable
private fun PresetSegment(
    presets: List<UiPreset>,
    selected: UiPreset?,
    labelFor: @Composable (UiPreset) -> String,
    onSelect: (UiPreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_H_PADDING)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = preset == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onSelect(preset) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelFor(preset),
                    fontSize = 13.sp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}


/* ───────────────────────── 二选一分段 ───────────────────────── */

@Composable
private fun TwoWaySegment(
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    onSelectFirst: () -> Unit,
    onSelectSecond: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(firstLabel to firstSelected, secondLabel to !firstSelected).forEachIndexed { index, (label, selected) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { if (index == 0) onSelectFirst() else onSelectSecond() }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/* ───────────────────────── 主题色块 ───────────────────────── */

@Composable
private fun SeedRow(
    selected: ThemeSeed,
    onSelect: (ThemeSeed) -> Unit
) {
    val dark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeSeed.entries.forEach { seed ->
            val label = stringResource(seedLabelRes(seed))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(seed) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(seed.primary(dark))
                        .then(
                            if (seed == selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (seed == selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = seed.onPrimary(dark),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ───────────────────────── 滑杆行 ───────────────────────── */

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    isDefault: Boolean,
    onReset: () -> Unit,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    ticks: Int = 0
) {
    Column(
        modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            if (!isDefault) {
                Spacer(modifier = Modifier.width(2.dp))
                IconAction(
                    icon = Icons.Default.RestartAlt,
                    enabled = true,
                    labelRes = R.string.customize_reset_item,
                    onClick = onReset,
                    boxSize = 30.dp,
                    iconSize = 17.dp
                )
            }
        }
        CustomSlider(
            value = value,
            range = range,
            defaultValue = defaultValue,
            ticks = ticks,
            onPreview = onPreview,
            onCommit = onCommit,
            modifier = Modifier.padding(top = SLIDER_BAR_TOP)
        )
    }
}

/**
 * 数值手动输入行，[unit] 作为只读后缀显示在输入框内。
 *
 * 输入过程中会经过"尚不构成合法值"的中间态（空串、"0."）。**此时不提交**：
 * 若对每次按键都做区间夹取再回写，用户敲 "0." 会被立刻改写成 "0.85"，导致永远输入不了 0.9 这类值。
 * 因此仅在解析结果落入 [range] 时实时提交以驱动预览，越界时以警示色提示，失焦时再归一到实际生效值，
 * 保证输入框显示与已存储的值不会长期不一致。
 */
@Composable
private fun DecimalInputRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    hint: String,
    isDefault: Boolean,
    onReset: () -> Unit,
    onCommit: (Float) -> Unit
) {
    var text by remember { mutableStateOf(formatDecimal(value)) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 仅在外部驱动（应用预设 / 恢复默认）导致数值变化时同步；数值含义一致则不覆盖，避免打断正在输入的文本
    LaunchedEffect(value) {
        if (text.toFloatOrNull() != value) text = formatDecimal(value)
    }

    val parsed = text.toFloatOrNull()
    val outOfRange = parsed != null && parsed !in range

    Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .width(INPUT_BOX_WIDTH)
                    .height(INPUT_BOX_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        val filtered = filterDecimalInput(raw)
                        text = filtered
                        filtered.toFloatOrNull()?.takeIf { it in range }?.let(onCommit)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (focused && !state.isFocused) text = formatDecimal(value)
                            focused = state.isFocused
                        }
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isDefault) {
                Spacer(modifier = Modifier.width(2.dp))
                IconAction(
                    icon = Icons.Default.RestartAlt,
                    enabled = true,
                    labelRes = R.string.customize_reset_item,
                    onClick = onReset,
                    boxSize = 30.dp,
                    iconSize = 17.dp
                )
            }
        }
        Text(
            text = hint,
            fontSize = 10.sp,
            color = if (outOfRange) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * 自绘滑杆。
 *
 * 不使用 Material3 Slider 的原因：
 *   1. 需要在轨道上叠加步进刻度与默认值基准，Slider 不提供该 slot；
 *   2. 轨道与滑块需按设计精确控制（4dp 轨道 / 16dp 方形滑块），默认样式无法对齐；
 *   3. 与项目既有"自绘控件以规避实验性 API"的取向一致。
 *
 * 手感相关的三条约束（每条都对应一次真实缺陷，改动前请先读懂）：
 *   1. **拖拽位置必须在控件内部连续累积**。外部 [value] 是量化后的值（字号 0.01、条数 1 档），
 *      若每次都拿 delta 从量化值重新推算，不足一档的位移会被反复丢弃：表现为拖动发涩，
 *      且档位越粗越严重 —— 条数档为 3–12（单档约 30dp），逐帧位移远小于一档，几乎拖不动。
 *   2. **拖动期间滑块不得走动画**。spring 过渡会让滑块始终落后手指，产生黏滞感；
 *      仅在拖动结束、应用预设等由外部驱动的位置变化时才用动画。
 *   3. **触摸区不等同于滑块**。滑块视觉高 26dp 太窄，故 drag 挂在整条 40dp 高的容器上。
 */
@Composable
private fun CustomSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    ticks: Int = 0
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val valueFraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val defaultFraction = ((defaultValue - range.start) / span).coerceIn(0f, 1f)

    var trackWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val thumbWidth = 16.dp

    // 非 null 表示正在拖动，保存未量化的连续位置，用于保留不足一档的位移残量
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownFraction = dragFraction ?: valueFraction

    val targetOffset = with(density) { (shownFraction * trackWidthPx).toDp() }
    val thumbSpec: AnimationSpec<Dp> =
        if (dragFraction != null) snap() else spring(visibilityThreshold = 0.01.dp)
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = thumbSpec,
        label = "thumbOffset"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val latestValue = remember { mutableStateOf(value) }
    val latestPreview = rememberUpdatedState(onPreview)
    val latestCommit = rememberUpdatedState(onCommit)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                state = rememberDraggableState { delta ->
                    if (trackWidthPx <= 0f) return@rememberDraggableState
                    val base = dragFraction ?: valueFraction
                    val next = (base + delta / trackWidthPx).coerceIn(0f, 1f)
                    dragFraction = next
                    val raw = range.start + next * span
                    latestValue.value = raw
                    latestPreview.value(raw)
                },
                onDragStarted = { dragFraction = valueFraction },
                onDragStopped = {
                    dragFraction = null
                    latestCommit.value(latestValue.value)
                }
            )
    ) {
        // 轨道已内缩半个滑块，滑块中心走完整个轨道跨度；此内缩同时是下方各叠加层的坐标基准，
        // 故填充与刻线都放在这一层内，与滑块共用同一映射。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = thumbWidth / 2),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    .onSizeChanged { trackWidthPx = it.width.toFloat() }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(shownFraction)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (ticks > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(ticks) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
            if (defaultFraction > 0.001f && defaultFraction < 0.999f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(defaultFraction)
                        .height(40.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(width = 2.dp, height = 12.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(width = thumbWidth, height = 26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onBackground)
        )
    }
}

/* ───────────────────────── 档位选择行 ───────────────────────── */

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    isDefault: Boolean,
    onReset: () -> Unit,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = CARD_INNER_V)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (!isDefault) {
                IconAction(
                    icon = Icons.Default.RestartAlt,
                    enabled = true,
                    labelRes = R.string.customize_reset_item,
                    onClick = onReset,
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
            options.forEach { (optionValue, label) ->
                ChosenChip(
                    selected = optionValue == selected,
                    label = label,
                    onClick = { onSelect(optionValue) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChosenChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/* ───────────────────────── 首页分区排序 ───────────────────────── */

@Composable
private fun SectionOrderCard(
    order: List<String>,
    homeSections: HomeSectionConfig,
    titleFor: @Composable (String) -> String,
    expandedTitle: String,
    onToggleExpand: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onLayoutChange: (String, HomeSectionLayout) -> Unit,
    onHideChange: (String, Boolean) -> Unit,
    onPinChange: (String, Boolean) -> Unit,
    onCountChange: (String, Int) -> Unit
) {
    SettingsGroupCard {
        Text(
            text = stringResource(R.string.customize_section_order),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CARD_INNER_H, top = CARD_INNER_V, bottom = 2.dp)
        )
        order.forEachIndexed { index, title ->
            if (index > 0) RowDivider()
            val layout = homeSections.layoutFor(title)
            val expanded = title == expandedTitle
            SectionOrderRow(
                title = titleFor(title),
                layout = layout,
                layoutLabel = stringResource(sectionLayoutLabelRes(layout)),
                hidden = homeSections.isHidden(title),
                pinned = title in homeSections.pinned,
                canMoveUp = index > 0,
                canMoveDown = index < order.lastIndex,
                onToggleExpand = { onToggleExpand(title) },
                onMoveUp = { onMove(index, -1) },
                onMoveDown = { onMove(index, 1) },
                onToggleHide = { onHideChange(title, !homeSections.isHidden(title)) },
                onTogglePin = { onPinChange(title, title !in homeSections.pinned) }
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = CARD_INNER_H, end = CARD_INNER_H, bottom = CARD_INNER_V)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    LayoutChooser(
                        selected = layout,
                        onSelect = { mode ->
                            onLayoutChange(title, mode)
                            onToggleExpand(title)
                        }
                    )
                    Text(
                        text = stringResource(R.string.customize_section_count),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    SliderRow(
                        title = titleFor(title),
                        valueText = homeSections.countFor(title).toString(),
                        value = homeSections.countFor(title).toFloat(),
                        range = HomeSectionConfig.MIN_VISIBLE_COUNT.toFloat()..HomeSectionConfig.MAX_VISIBLE_COUNT.toFloat(),
                        defaultValue = homeSections.visibleCount.toFloat(),
                        isDefault = homeSections.countFor(title) == homeSections.visibleCount,
                        ticks = HomeSectionConfig.MAX_VISIBLE_COUNT - HomeSectionConfig.MIN_VISIBLE_COUNT + 1,
                        onReset = { onCountChange(title, homeSections.visibleCount) },
                        onPreview = { v -> onCountChange(title, v.roundToInt()) },
                        onCommit = { v -> onCountChange(title, v.roundToInt()) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SectionOrderRow(
    title: String,
    layout: HomeSectionLayout,
    layoutLabel: String,
    hidden: Boolean,
    pinned: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleExpand: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleHide: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = CARD_INNER_H, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = stringResource(R.string.customize_section_pin),
            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            modifier = Modifier
                .size(16.dp)
                .clickable { onTogglePin() }
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = if (hidden) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textDecoration = if (hidden) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleHide() }
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (layout == HomeSectionLayout.AUTO) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    }
                )
                .clickable { onToggleExpand() }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = layoutLabel,
                fontSize = 10.sp,
                color = if (layout == HomeSectionLayout.AUTO) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        IconAction(
            icon = Icons.Default.KeyboardArrowUp,
            enabled = canMoveUp && !hidden,
            labelRes = R.string.customize_section_move_up,
            onClick = onMoveUp,
            boxSize = 30.dp,
            iconSize = 18.dp
        )
        IconAction(
            icon = Icons.Default.KeyboardArrowDown,
            enabled = canMoveDown && !hidden,
            labelRes = R.string.customize_section_move_down,
            onClick = onMoveDown,
            boxSize = 30.dp,
            iconSize = 18.dp
        )
    }
}

@Composable
private fun LayoutChooser(
    selected: HomeSectionLayout,
    onSelect: (HomeSectionLayout) -> Unit
) {
    Column(
        modifier = Modifier.padding(start = CARD_INNER_H, end = CARD_INNER_H, bottom = CARD_INNER_V)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeSectionLayout.entries.forEach { mode ->
                ChosenChip(
                    selected = mode == selected,
                    label = stringResource(sectionLayoutLabelRes(mode)),
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/* ───────────────────────── 通用行 ───────────────────────── */

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = CARD_INNER_H, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    enabled: Boolean,
    labelRes: Int,
    onClick: () -> Unit,
    boxSize: Dp = 36.dp,
    iconSize: Dp = 19.dp
) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(RoundedCornerShape(boxSize / 2))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(iconSize)
        )
    }
}

/** 溢出风险提示：把 sanitized() 的夹取区间前置告知用户，避免"调不下去"的困惑。 */
@Composable
private fun OverflowNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SCREEN_H_PADDING)
            .clip(RoundedCornerShape(10.dp))
            .background(HanimeWarning.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = HanimeWarning,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = stringResource(R.string.customize_overflow_notice),
            fontSize = 11.sp,
            color = HanimeWarning,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/* ───────────────────────── 工具 ───────────────────────── */

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("hanime-ui-config", text))
}

/** 剪贴板内容不像本应用配置时返回空串，避免把无关文本灌进输入框。 */
private fun readClipboardIfConfig(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    val text = clip.getItemAt(0).text?.toString().orEmpty()
    return if (text.contains("hanime-ui-config")) text else ""
}

private fun formatDecimal(value: Float): String = String.format(Locale.US, "%.2f", value)

/**
 * 过滤为「最多 [INPUT_MAX_INT_DIGITS] 位整数 + 最多 [INPUT_MAX_DECIMALS] 位小数」的字符串。
 * 逐字符重建而非正则替换：需要同时约束整数位、小数位与小数点个数，并自然丢弃粘贴进来的其他字符。
 */
private fun filterDecimalInput(raw: String): String {
    val sb = StringBuilder()
    var dotSeen = false
    var intDigits = 0
    var decimals = 0
    for (ch in raw) {
        when {
            ch == '.' && !dotSeen -> {
                sb.append(ch); dotSeen = true
            }

            ch.isDigit() && !dotSeen -> if (intDigits < INPUT_MAX_INT_DIGITS) {
                sb.append(ch); intDigits++
            }

            ch.isDigit() -> if (decimals < INPUT_MAX_DECIMALS) {
                sb.append(ch); decimals++
            }
        }
    }
    return sb.toString()
}

private fun sectionLayoutLabelRes(layout: HomeSectionLayout): Int = when (layout) {
    HomeSectionLayout.AUTO -> R.string.customize_section_layout_auto
    HomeSectionLayout.LIST -> R.string.customize_section_layout_list
    HomeSectionLayout.GRID -> R.string.customize_section_layout_grid
    HomeSectionLayout.CAROUSEL -> R.string.customize_section_layout_carousel
}

private fun presetLabelRes(preset: UiPreset): Int = when (preset) {
    UiPreset.COMPACT -> R.string.customize_preset_compact
    UiPreset.STANDARD -> R.string.customize_preset_standard
    UiPreset.COMFORTABLE -> R.string.customize_preset_comfortable
    UiPreset.LARGE -> R.string.customize_preset_large
}

private fun continueStyleLabelRes(style: ContinueCardStyle): Int = when (style) {
    ContinueCardStyle.COVER -> R.string.customize_continue_cover
    ContinueCardStyle.MINIMAL -> R.string.customize_continue_minimal
}

private fun navStyleLabelRes(style: NavigationStyle): Int = when (style) {
    NavigationStyle.AUTO -> R.string.customize_nav_auto
    NavigationStyle.BOTTOM -> R.string.customize_nav_bottom
    NavigationStyle.RAIL -> R.string.customize_nav_rail
}

private fun cardStyleLabelRes(style: CardStyle): Int = when (style) {
    CardStyle.AUTO -> R.string.customize_card_style_auto
    CardStyle.FILLED -> R.string.customize_card_style_filled
    CardStyle.OUTLINED -> R.string.customize_card_style_outlined
    CardStyle.ELEVATED -> R.string.customize_card_style_elevated
    CardStyle.PLAIN -> R.string.customize_card_style_plain
}

private fun seedLabelRes(seed: ThemeSeed): Int = when (seed) {
    ThemeSeed.BRAND -> R.string.customize_seed_brand
    ThemeSeed.INDIGO -> R.string.customize_seed_indigo
    ThemeSeed.TEAL -> R.string.customize_seed_teal
    ThemeSeed.PURPLE -> R.string.customize_seed_purple
    ThemeSeed.AMBER -> R.string.customize_seed_amber
    ThemeSeed.GRAPHITE -> R.string.customize_seed_graphite
    ThemeSeed.PINK -> R.string.customize_seed_pink
    ThemeSeed.BLUE -> R.string.customize_seed_blue
}

private fun qualityLabelRes(quality: ThumbnailQuality): Int = when (quality) {
    ThumbnailQuality.LOW -> R.string.customize_quality_low
    ThumbnailQuality.STANDARD -> R.string.customize_quality_standard
    ThumbnailQuality.HIGH -> R.string.customize_quality_high
}
