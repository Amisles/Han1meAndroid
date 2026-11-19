package app.amisles.hanime.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.LocalIsDarkTheme
import app.amisles.hanime.core.ui.theme.ThemeSeed
import app.amisles.hanime.core.ui.theme.UiPreset

internal val SCREEN_H_PADDING = 15.dp

internal val CARD_GAP = 6.dp

internal val CARD_INNER_H = 16.dp

internal val CARD_INNER_V = 12.dp

internal val GROUP_GAP = 14.dp

@Composable
internal fun GroupLabel(text: String) {
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
internal fun RowDivider() {
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
internal fun PresetSegment(
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
internal fun TwoWaySegment(
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
internal fun SeedRow(
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
internal fun SliderRow(
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

@Composable
internal fun <T> ChoiceRow(
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
internal fun ChosenChip(
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
internal fun ActionRow(
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
internal fun IconAction(
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
