package app.amisles.hanime.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
    fun everySeedKeepsTextOnAccentAboveLargeTextContrast() {
        // 当前色板只满足 WCAG AA 大字号，先把下限锁住；色板调整后应把阈值提到 4.5:1。
        ThemeSeed.entries.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val scheme = hanimeColorScheme(dark, AppearanceConfig(seed = seed))
                val ratio = contrastRatio(scheme.primary, scheme.onPrimary)
                assertTrue(
                    "${seed.name}/${if (dark) "dark" else "light"} 对比度 ${"%.2f".format(ratio)}:1 低于 3:1",
                    ratio >= 3f
                )
            }
        }
    }

    private fun contrastRatio(a: Color, b: Color): Float {
        val first = relativeLuminance(a)
        val second = relativeLuminance(b)
        return (max(first, second) + 0.05f) / (min(first, second) + 0.05f)
    }

    private fun relativeLuminance(color: Color): Float {
        fun channel(value: Float): Float =
            if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }
}
