package app.amisles.hanime.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.components.SettingsGroupCard
import app.amisles.hanime.core.ui.components.VideoGridCard
import app.amisles.hanime.core.ui.components.VideoListItem
import app.amisles.hanime.core.ui.theme.HanimeSuccess
import app.amisles.hanime.core.ui.theme.HanimeWarning
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.LocalIsDarkTheme
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.hanimeColorScheme
import app.amisles.hanime.domain.model.HanimeVideo
import kotlin.math.roundToInt

internal val PREVIEW_PANE_WIDTH = 320.dp

@Composable
internal fun PreviewPane(
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
internal fun ActionBar(
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
internal fun SectionOrderCard(
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
internal fun SectionOrderRow(
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
internal fun LayoutChooser(
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

/** 溢出风险提示：把 sanitized() 的夹取区间前置告知用户，避免"调不下去"的困惑。 */
@Composable
internal fun OverflowNotice() {
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
internal fun ConfirmDialog(
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

internal fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("hanime-ui-config", text))
}

/** 剪贴板内容不像本应用配置时返回空串，避免把无关文本灌进输入框。 */
internal fun readClipboardIfConfig(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    val text = clip.getItemAt(0).text?.toString().orEmpty()
    return if (text.contains("hanime-ui-config")) text else ""
}
