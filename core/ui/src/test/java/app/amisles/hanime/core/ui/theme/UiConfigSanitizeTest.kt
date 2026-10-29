package app.amisles.hanime.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class UiConfigSanitizeTest {

    @Test
    fun cardTitleLinesClampToSupportedRange() {
        assertEquals(
            CardConfig.MIN_TITLE_LINES,
            UiConfig.Default.copy(card = CardConfig(titleLines = 0)).sanitized().card.titleLines
        )
        assertEquals(
            CardConfig.MAX_TITLE_LINES,
            UiConfig.Default.copy(card = CardConfig(titleLines = 9)).sanitized().card.titleLines
        )
    }

    @Test
    fun bannerHeightClampToSupportedRange() {
        assertEquals(180.dp, UiConfig.Default.copy(bannerHeight = 10.dp).sanitized().bannerHeight)
        assertEquals(360.dp, UiConfig.Default.copy(bannerHeight = 900.dp).sanitized().bannerHeight)
    }

    @Test
    fun bannerTagCountClampToSupportedRange() {
        assertEquals(0, UiConfig.Default.copy(bannerMaxTags = -3).sanitized().bannerMaxTags)
        assertEquals(8, UiConfig.Default.copy(bannerMaxTags = 20).sanitized().bannerMaxTags)
    }
}
