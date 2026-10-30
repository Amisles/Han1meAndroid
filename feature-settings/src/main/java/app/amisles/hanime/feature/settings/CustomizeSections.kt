package app.amisles.hanime.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SettingsGroupCard
import app.amisles.hanime.core.ui.components.SettingsSwitchRow
import app.amisles.hanime.core.ui.theme.CardConfig
import app.amisles.hanime.core.ui.theme.CardStyle
import app.amisles.hanime.core.ui.theme.ContrastLevel
import app.amisles.hanime.core.ui.theme.CornerMode
import app.amisles.hanime.core.ui.theme.NavigationStyle
import app.amisles.hanime.core.ui.theme.ThumbnailQuality
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.amisles.hanime.core.ui.theme.UiConfig
import androidx.compose.runtime.Composable

@Composable
internal fun NavigationSection(
    config: UiConfig,
    defaults: UiConfig,
    updateConfig: ((UiConfig) -> UiConfig) -> Unit
) {
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

@Composable
internal fun AppearanceSection(
    config: UiConfig,
    defaults: UiConfig,
    updateConfig: ((UiConfig) -> UiConfig) -> Unit
) {
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

@Composable
internal fun TypographySection(
    config: UiConfig,
    defaults: UiConfig,
    updateConfig: ((UiConfig) -> UiConfig) -> Unit
) {
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

@Composable
internal fun CardSection(
    config: UiConfig,
    defaults: UiConfig,
    updateConfig: ((UiConfig) -> UiConfig) -> Unit
) {
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

@Composable
internal fun NavLabelsSection(
    config: UiConfig,
    defaults: UiConfig,
    updateConfig: ((UiConfig) -> UiConfig) -> Unit
) {
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

@Composable
internal fun GridSection(
    config: UiConfig,
    defaults: UiConfig,
    updateConfig: ((UiConfig) -> UiConfig) -> Unit
) {
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
