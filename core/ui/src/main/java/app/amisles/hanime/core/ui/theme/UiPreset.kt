package app.amisles.hanime.core.ui.theme

import androidx.compose.ui.unit.dp

enum class UiPreset(val config: UiConfig) {
    COMPACT(
        UiConfig.Default.copy(
            fontScale = 0.9f,
            cardCornerRadius = 6.dp,
            listThumbnailCornerRadius = 4.dp,
            listThumbnailWidth = 120.dp,
            cardTitleTopPadding = 4.dp,
            listItemPadding = 4.dp,
            listItemSpacing = 6.dp,
            listMetaSpacing = 8.dp,
            gridTargetWidth = 140.dp,
            gridGap = 8.dp,
            thumbnailQuality = ThumbnailQuality.LOW
        )
    ),
    STANDARD(UiConfig.Default),
    COMFORTABLE(
        UiConfig.Default.copy(
            fontScale = 1.05f,
            cardCornerRadius = 12.dp,
            listThumbnailCornerRadius = 10.dp,
            listThumbnailWidth = 170.dp,
            cardTitleTopPadding = 8.dp,
            listItemPadding = 8.dp,
            listItemSpacing = 10.dp,
            listMetaSpacing = 12.dp,
            gridTargetWidth = 200.dp,
            gridGap = 14.dp,
            thumbnailQuality = ThumbnailQuality.STANDARD
        )
    ),
    LARGE(
        UiConfig.Default.copy(
            fontScale = 1.15f,
            cardCornerRadius = 14.dp,
            listThumbnailCornerRadius = 12.dp,
            listThumbnailWidth = 200.dp,
            cardTitleTopPadding = 10.dp,
            listItemPadding = 10.dp,
            listItemSpacing = 12.dp,
            listMetaSpacing = 14.dp,
            gridTargetWidth = 260.dp,
            gridGap = 16.dp,
            thumbnailQuality = ThumbnailQuality.HIGH
        )
    );

    companion object {
        fun of(config: UiConfig): UiPreset? = entries.firstOrNull { it.config == config }
    }
}
