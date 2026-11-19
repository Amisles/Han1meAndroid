package app.amisles.hanime.feature.settings

import app.amisles.hanime.core.ui.R
import app.amisles.hanime.core.ui.theme.CardStyle
import app.amisles.hanime.core.ui.theme.ContinueCardStyle
import app.amisles.hanime.core.ui.theme.HomeSectionLayout
import app.amisles.hanime.core.ui.theme.NavigationStyle
import app.amisles.hanime.core.ui.theme.ThemeSeed
import app.amisles.hanime.core.ui.theme.ThumbnailQuality
import app.amisles.hanime.core.ui.theme.UiPreset

internal fun sectionLayoutLabelRes(layout: HomeSectionLayout): Int = when (layout) {
    HomeSectionLayout.AUTO -> R.string.customize_section_layout_auto
    HomeSectionLayout.LIST -> R.string.customize_section_layout_list
    HomeSectionLayout.GRID -> R.string.customize_section_layout_grid
    HomeSectionLayout.CAROUSEL -> R.string.customize_section_layout_carousel
}

internal fun presetLabelRes(preset: UiPreset): Int = when (preset) {
    UiPreset.COMPACT -> R.string.customize_preset_compact
    UiPreset.STANDARD -> R.string.customize_preset_standard
    UiPreset.COMFORTABLE -> R.string.customize_preset_comfortable
    UiPreset.LARGE -> R.string.customize_preset_large
}

internal fun continueStyleLabelRes(style: ContinueCardStyle): Int = when (style) {
    ContinueCardStyle.COVER -> R.string.customize_continue_cover
    ContinueCardStyle.MINIMAL -> R.string.customize_continue_minimal
}

internal fun navStyleLabelRes(style: NavigationStyle): Int = when (style) {
    NavigationStyle.AUTO -> R.string.customize_nav_auto
    NavigationStyle.BOTTOM -> R.string.customize_nav_bottom
    NavigationStyle.RAIL -> R.string.customize_nav_rail
}

internal fun cardStyleLabelRes(style: CardStyle): Int = when (style) {
    CardStyle.AUTO -> R.string.customize_card_style_auto
    CardStyle.FILLED -> R.string.customize_card_style_filled
    CardStyle.OUTLINED -> R.string.customize_card_style_outlined
    CardStyle.ELEVATED -> R.string.customize_card_style_elevated
    CardStyle.PLAIN -> R.string.customize_card_style_plain
}

internal fun seedLabelRes(seed: ThemeSeed): Int = when (seed) {
    ThemeSeed.BRAND -> R.string.customize_seed_brand
    ThemeSeed.INDIGO -> R.string.customize_seed_indigo
    ThemeSeed.TEAL -> R.string.customize_seed_teal
    ThemeSeed.PURPLE -> R.string.customize_seed_purple
    ThemeSeed.AMBER -> R.string.customize_seed_amber
    ThemeSeed.GRAPHITE -> R.string.customize_seed_graphite
    ThemeSeed.PINK -> R.string.customize_seed_pink
    ThemeSeed.BLUE -> R.string.customize_seed_blue
}

internal fun qualityLabelRes(quality: ThumbnailQuality): Int = when (quality) {
    ThumbnailQuality.LOW -> R.string.customize_quality_low
    ThumbnailQuality.STANDARD -> R.string.customize_quality_standard
    ThumbnailQuality.HIGH -> R.string.customize_quality_high
}
