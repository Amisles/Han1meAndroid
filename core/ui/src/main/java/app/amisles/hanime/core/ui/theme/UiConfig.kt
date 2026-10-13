package app.amisles.hanime.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class UiConfig(
    val fontScale: Float = 1f,
    val thumbnailAspectRatio: Float = 16f / 9f,
    val cardCornerRadius: Dp = 8.dp,
    val listThumbnailWidth: Dp = 150.dp,
    val listThumbnailCornerRadius: Dp = 6.dp,
    val cardTitleTopPadding: Dp = 6.dp,
    val cardMetaSpacing: Dp = 4.dp,
    val listItemPadding: Dp = 6.dp,
    val listItemSpacing: Dp = 8.dp,
    val listMetaSpacing: Dp = 10.dp,
    val cardTitleFontSize: TextUnit = 13.sp,
    val cardMetaFontSize: TextUnit = 11.sp,
    val gridTitleFontSize: TextUnit = 12.sp,
    val gridTitleLineHeight: TextUnit = 16.sp,
    val gridMetaFontSize: TextUnit = 11.sp,
    val listTitleFontSize: TextUnit = 12.sp,
    val listMetaFontSize: TextUnit = 10.sp,
    val badgeFontSize: TextUnit = 10.sp,
    val gridColumnsCompact: Int = 2,
    val gridColumnsMedium: Int = 3,
    val gridColumnsExpanded: Int = 4,
    val gridTargetWidth: Dp = 168.dp,
    val gridGap: Dp = 12.dp,
    val gridMinColumns: Int = 2,
    val gridMaxColumns: Int = 5,
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.STANDARD,
) {
    fun gridColumnsFor(availableWidth: Dp): Int {
        val width = availableWidth.value
        if (width.isNaN() || width <= 0f) return gridMinColumns
        return ((availableWidth + gridGap) / (gridTargetWidth + gridGap)).toInt()
            .coerceIn(gridMinColumns, gridMaxColumns)
    }

    fun sanitized(): UiConfig {
        val minCols = gridMinColumns.coerceIn(1, 6)
        val maxCols = gridMaxColumns.coerceIn(minCols, 8)
        return copy(
            fontScale = fontScale.coerceIn(0.85f, 1.4f),
            thumbnailAspectRatio = thumbnailAspectRatio.coerceIn(1f, 2f),
            cardCornerRadius = cardCornerRadius.clamp(0f, 24f),
            listThumbnailWidth = listThumbnailWidth.clamp(100f, 200f),
            listThumbnailCornerRadius = listThumbnailCornerRadius.clamp(0f, 24f),
            cardTitleTopPadding = cardTitleTopPadding.clamp(0f, 16f),
            cardMetaSpacing = cardMetaSpacing.clamp(0f, 16f),
            listItemPadding = listItemPadding.clamp(0f, 16f),
            listItemSpacing = listItemSpacing.clamp(0f, 24f),
            listMetaSpacing = listMetaSpacing.clamp(0f, 24f),
            cardTitleFontSize = cardTitleFontSize.clampSp(10f, 20f),
            cardMetaFontSize = cardMetaFontSize.clampSp(8f, 18f),
            gridTitleFontSize = gridTitleFontSize.clampSp(10f, 20f),
            gridTitleLineHeight = gridTitleLineHeight.clampSp(12f, 26f),
            gridMetaFontSize = gridMetaFontSize.clampSp(8f, 18f),
            listTitleFontSize = listTitleFontSize.clampSp(10f, 20f),
            listMetaFontSize = listMetaFontSize.clampSp(8f, 18f),
            badgeFontSize = badgeFontSize.clampSp(8f, 16f),
            gridColumnsCompact = gridColumnsCompact.coerceIn(1, 6),
            gridColumnsMedium = gridColumnsMedium.coerceIn(1, 6),
            gridColumnsExpanded = gridColumnsExpanded.coerceIn(1, 6),
            gridTargetWidth = gridTargetWidth.clamp(96f, 320f),
            gridGap = gridGap.clamp(0f, 32f),
            gridMinColumns = minCols,
            gridMaxColumns = maxCols,
        )
    }

    companion object {
        val Default = UiConfig()
    }
}

private fun Dp.clamp(min: Float, max: Float): Dp = value.coerceIn(min, max).dp

private fun TextUnit.clampSp(min: Float, max: Float): TextUnit =
    if (isSp) value.coerceIn(min, max).sp else this

enum class ThumbnailQuality(val sizeScale: Float) {
    LOW(0.5f),
    STANDARD(1f),
    HIGH(1.5f),
}

val LocalUiConfig = staticCompositionLocalOf { UiConfig.Default }
