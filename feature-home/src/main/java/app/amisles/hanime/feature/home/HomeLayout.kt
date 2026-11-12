package app.amisles.hanime.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.WindowSizeInfo
import app.amisles.hanime.core.ui.theme.WindowWidthSizeClass

internal val HOME_SIDE_PADDING = 10.dp
internal val HOME_SECTION_GAP = 20.dp

internal val HERO_GAP = 16.dp
private val HERO_MIN_HEIGHT = 180.dp
private const val HERO_BANNER_RATIO = 0.62f
private const val HERO_COVER_ASPECT = 9f / 16f
private const val HERO_VIEWPORT_RATIO = 0.4f
private const val HERO_MIN_WIDTH_DP = 840
private const val HERO_MIN_WINDOW_HEIGHT_DP = 600
private const val SECTION_TWO_COLUMN_MIN_WIDTH_DP = 1000

internal data class HomeHeroLayout(
    val twoColumn: Boolean,
    val bannerWidth: Dp,
    val sideWidth: Dp,
    val height: Dp
)

internal fun homeContentWidth(widthDp: Int, contentMaxWidth: Dp): Dp =
    if (contentMaxWidth != Dp.Unspecified) minOf(widthDp.toFloat(), contentMaxWidth.value).dp else widthDp.dp

/**
 * 宽屏 hero 高度按封面比例与视口高度算，不取 bannerHeight：配置值是为单列准备的，
 * 套到两栏宽度上会退回长条裁切。
 *
 * [HomeHeroLayout.height] 为 0 表示沿用配置高度（单列布局），否则由本函数给出。
 */
internal fun homeHeroLayout(
    widthDp: Int,
    heightDp: Int,
    contentMaxWidth: Dp,
    hasSideContent: Boolean
): HomeHeroLayout {
    val innerWidth = homeContentWidth(widthDp, contentMaxWidth) - HOME_SIDE_PADDING * 2
    val wide = innerWidth.value >= HERO_MIN_WIDTH_DP && heightDp >= HERO_MIN_WINDOW_HEIGHT_DP
    if (!wide) {
        return HomeHeroLayout(twoColumn = false, bannerWidth = innerWidth, sideWidth = 0.dp, height = 0.dp)
    }
    val bannerWidth = if (hasSideContent) (innerWidth.value * HERO_BANNER_RATIO).dp else innerWidth
    val sideWidth = if (hasSideContent) innerWidth - bannerWidth - HERO_GAP else 0.dp
    val height = minOf(bannerWidth.value * HERO_COVER_ASPECT, heightDp * HERO_VIEWPORT_RATIO)
        .coerceAtLeast(HERO_MIN_HEIGHT.value)
        .dp
    return HomeHeroLayout(
        twoColumn = hasSideContent,
        bannerWidth = bannerWidth,
        sideWidth = sideWidth,
        height = height
    )
}

internal fun homeTwoColumnSections(sizeInfo: WindowSizeInfo): Boolean =
    homeContentWidth(sizeInfo.widthDp, sizeInfo.contentMaxWidth).value >= SECTION_TWO_COLUMN_MIN_WIDTH_DP

internal fun homeSectionBlockWidth(sizeInfo: WindowSizeInfo, twoColumnSections: Boolean): Dp =
    if (twoColumnSections) {
        (homeContentWidth(sizeInfo.widthDp, sizeInfo.contentMaxWidth) - HOME_SIDE_PADDING * 2 - HOME_SECTION_GAP) / 2
    } else {
        homeContentWidth(sizeInfo.widthDp, sizeInfo.contentMaxWidth)
    }

internal fun homeSectionColumns(cfg: UiConfig, sizeInfo: WindowSizeInfo, twoColumnSections: Boolean): Int =
    if (twoColumnSections || sizeInfo.widthClass == WindowWidthSizeClass.Expanded) {
        cfg.gridColumnsFor(homeSectionBlockWidth(sizeInfo, twoColumnSections) - HOME_SIDE_PADDING * 2)
    } else {
        sizeInfo.gridColumns(cfg)
    }
