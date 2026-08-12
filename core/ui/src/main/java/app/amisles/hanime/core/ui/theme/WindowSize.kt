package app.amisles.hanime.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 窗口宽度分级（遵循 Material 断点）：
 * - Compact  : < 600dp  （手机）
 * - Medium   : 600–839dp（小平板 / 横屏手机）
 * - Expanded : ≥ 840dp  （平板 / 桌面）
 */
enum class WindowWidthSizeClass { Compact, Medium, Expanded }

/**
 * 当前窗口的响应式信息。由 [rememberWindowSizeInfo] 计算并通过 CompositionLocal 下发，
 * 各页面可用 [currentWindowSizeInfo] 读取，无需层层透传参数。
 */
data class WindowSizeInfo(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WindowWidthSizeClass,
    /** 是否为平板：取宽高较小边 ≥ 600dp，避免横屏手机被误判为平板 */
    val isTablet: Boolean,
    val isLandscape: Boolean
) {
    /** 视频栅格列数：手机 2 列，平板 3–4 列 */
    val gridColumns: Int
        get() = when (widthClass) {
            WindowWidthSizeClass.Compact -> 2
            WindowWidthSizeClass.Medium -> 3
            WindowWidthSizeClass.Expanded -> 4
        }

    /** 内容最大宽度：Compact 不限制，平板居中并限宽避免元素被拉伸过大 */
    val contentMaxWidth: Dp
        get() = when (widthClass) {
            WindowWidthSizeClass.Compact -> Dp.Unspecified
            WindowWidthSizeClass.Medium -> 840.dp
            WindowWidthSizeClass.Expanded -> 1080.dp
        }

    /** 平板是否使用左侧 NavigationRail（而非底部导航栏） */
    val useNavigationRail: Boolean get() = isTablet

    /** 平板是否使用放大排版 */
    val largeTypography: Boolean get() = isTablet
}

private val LocalWindowSizeInfo = compositionLocalOf { computeWindowSizeInfo(360, 640) }

@Composable
fun rememberWindowSizeInfo(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        computeWindowSizeInfo(configuration.screenWidthDp, configuration.screenHeightDp)
    }
}

fun computeWindowSizeInfo(widthDp: Int, heightDp: Int): WindowSizeInfo {
    val widthClass = when {
        widthDp < 600 -> WindowWidthSizeClass.Compact
        widthDp < 840 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Expanded
    }
    return WindowSizeInfo(
        widthDp = widthDp,
        heightDp = heightDp,
        widthClass = widthClass,
        isTablet = min(widthDp, heightDp) >= 600,
        isLandscape = widthDp > heightDp
    )
}

/** 读取当前窗口尺寸信息（需在 [ProvideWindowSizeInfo] 作用域内） */
@Composable
fun currentWindowSizeInfo(): WindowSizeInfo = LocalWindowSizeInfo.current

/**
 * 在作用域内提供窗口尺寸信息，供所有页面通过 [currentWindowSizeInfo] 读取。
 * 应置于 MaterialTheme 外层（app 的 HanimeTheme 内部已调用）。
 */
@Composable
fun ProvideWindowSizeInfo(content: @Composable () -> Unit) {
    val sizeInfo = rememberWindowSizeInfo()
    CompositionLocalProvider(LocalWindowSizeInfo provides sizeInfo) {
        content()
    }
}

/**
 * 响应式内容容器：将内容限制到 [WindowSizeInfo.contentMaxWidth] 并水平居中，
 * 解决大屏下内容被过度拉伸、控件空旷的问题。Compact 下等价于整宽，不改变手机布局。
 * 各页面只需把根布局包一层即可，页面内部已有的水平 padding 继续保留。
 */
@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val sizeInfo = currentWindowSizeInfo()
    val maxWidth = sizeInfo.contentMaxWidth
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}
