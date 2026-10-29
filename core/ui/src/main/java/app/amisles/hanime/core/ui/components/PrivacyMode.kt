package app.amisles.hanime.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** 隐私模式开关的界面侧镜像：为 true 时所有内容图片以占位遮罩呈现。 */
val LocalPrivacyMode = staticCompositionLocalOf { false }

/** 隐私模式下的图片占位：不带任何画面内容，仅一个中性图标。 */
@Composable
fun PrivacyPlaceholder(
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/** 内容图片入口：隐私模式下不发起加载，直接渲染占位。 */
@Composable
fun PrivacyAwareAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = 1f
) {
    if (LocalPrivacyMode.current) {
        PrivacyPlaceholder(modifier)
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            alpha = alpha
        )
    }
}
