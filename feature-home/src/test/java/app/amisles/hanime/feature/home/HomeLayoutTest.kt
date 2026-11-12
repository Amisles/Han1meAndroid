package app.amisles.hanime.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.WindowHeightSizeClass
import app.amisles.hanime.core.ui.theme.computeWindowSizeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    @Test
    fun `content width follows window until capped`() {
        assertEquals(1080f, homeContentWidth(1280, 1080.dp).value, 0.01f)
        assertEquals(360f, homeContentWidth(360, Dp.Unspecified).value, 0.01f)
    }

    @Test
    fun `landscape phone keeps single column hero`() {
        assertFalse(homeHeroLayout(840, 390, Dp.Unspecified, hasSideContent = true).twoColumn)
        assertFalse(homeHeroLayout(700, 900, Dp.Unspecified, hasSideContent = true).twoColumn)
    }

    @Test
    fun `tablet landscape splits hero into two columns`() {
        val layout = homeHeroLayout(1280, 800, 1080.dp, hasSideContent = true)
        assertTrue(layout.twoColumn)
        assertEquals(657.2f, layout.bannerWidth.value, 0.5f)
        assertEquals(386.8f, layout.sideWidth.value, 0.5f)
        assertEquals(320f, layout.height.value, 0.5f)
    }

    @Test
    fun `expanded window without side content keeps a full width hero`() {
        val layout = homeHeroLayout(1280, 800, 1080.dp, hasSideContent = false)
        assertFalse(layout.twoColumn)
        assertEquals(1060f, layout.bannerWidth.value, 0.5f)
        assertEquals(320f, layout.height.value, 0.5f)
    }

    @Test
    fun `hero height follows cover aspect on tall screens`() {
        assertEquals(369.7f, homeHeroLayout(2560, 1600, 1080.dp, hasSideContent = true).height.value, 0.5f)
    }

    @Test
    fun `single column layout defers to configured banner height`() {
        assertEquals(0f, homeHeroLayout(360, 640, Dp.Unspecified, hasSideContent = true).height.value, 0.01f)
    }

    @Test
    fun `section columns grow with width only on expanded`() {
        val cfg = UiConfig.Default
        assertEquals(5, homeSectionColumns(cfg, computeWindowSizeInfo(1280, 800)))
        assertEquals(3, homeSectionColumns(cfg, computeWindowSizeInfo(800, 1280)))
        assertEquals(2, homeSectionColumns(cfg, computeWindowSizeInfo(360, 640)))
    }

    @Test
    fun `height class separates landscape phone from tablet`() {
        assertEquals(WindowHeightSizeClass.Compact, computeWindowSizeInfo(840, 390).heightClass)
        assertEquals(WindowHeightSizeClass.Medium, computeWindowSizeInfo(1280, 800).heightClass)
        assertEquals(WindowHeightSizeClass.Expanded, computeWindowSizeInfo(2560, 1600).heightClass)
    }
}
