package app.amisles.hanime.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceConfigTest {

    @Test
    fun seedDrivesPrimaryInBothModes() {
        val appearance = AppearanceConfig(seed = ThemeSeed.TEAL)

        assertEquals(ThemeSeed.TEAL.lightPrimary, hanimeColorScheme(false, appearance).primary)
        assertEquals(ThemeSeed.TEAL.darkPrimary, hanimeColorScheme(true, appearance).primary)
    }

    @Test
    fun amoledOnlyAppliesToDarkScheme() {
        val appearance = AppearanceConfig(amoled = true)

        assertEquals(Color.Black, hanimeColorScheme(true, appearance).background)
        assertEquals(
            hanimeColorScheme(false, AppearanceConfig()).background,
            hanimeColorScheme(false, appearance).background
        )
    }

    @Test
    fun highContrastStrengthensForegroundWithoutTouchingAccent() {
        val standard = hanimeColorScheme(false, AppearanceConfig())
        val high = hanimeColorScheme(false, AppearanceConfig(contrast = ContrastLevel.HIGH))

        assertEquals(standard.primary, high.primary)
        assertEquals(Color.Black, high.onSurface)
        assertNotEquals(standard.onSurfaceVariant, high.onSurfaceVariant)
    }

    @Test
    fun everySeedKeepsTextOnAccentReadable() {
        ThemeSeed.entries.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val scheme = hanimeColorScheme(dark, AppearanceConfig(seed = seed))
                assertTrue(
                    "${seed.name}/${if (dark) "dark" else "light"} 主色与前景色不能相同",
                    scheme.primary != scheme.onPrimary
                )
            }
        }
    }
}
