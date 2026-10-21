package app.amisles.hanime.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 统一样式的开关。
 *
 * 默认 Material3 开关在本主题下有两处不可用：
 * 1. 关闭态滑块取 `outline`，而浅色板把 outline 映射成与轨道相同的 `#E0E0E0`，
 *    滑块与轨道同色，视觉上直接消失；
 * 2. 开启态滑块取 `onPrimary`（近黑）、轨道取 `primary`（`#DC143C`），红黑硬碰，
 *    浅色模式下对比度过高、观感刺眼。
 *
 * 这里显式指定配色：
 * - 关闭态：滑块用 `onSurfaceVariant`、轨道用 `surfaceVariant`，并补一层淡描边保证边界可辨；
 * - 开启态：滑块用白色、轨道用降低不透明度的主色，保留品牌识别但压低饱和度。
 */
@Composable
fun HanimeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = CHECKED_TRACK_ALPHA),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNCHECKED_BORDER_ALPHA)
        )
    )
}

/**
 * 开启态轨道不透明度。主色 `#DC143C` 直接铺满会把白色滑块压成高对比红白配，
 * 0.68 叠加在卡片底色上得到偏柔和的玫瑰红，深浅色下都保留「已开启」的识别度。
 */
private const val CHECKED_TRACK_ALPHA = 0.68f

private const val UNCHECKED_BORDER_ALPHA = 0.24f
