package app.amisles.hanime.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** 强调色种子：只替换 primary / secondary，背景与文字仍由中性色板与对比度档位决定。 */
enum class ThemeSeed(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val lightSecondary: Color,
    val darkSecondary: Color,
) {
    BRAND(
        lightPrimary = Color(0xFFDC143C),
        lightOnPrimary = Color(0xFF1A1A1A),
        darkPrimary = Color(0xFFDC143C),
        darkOnPrimary = Color.White,
        lightSecondary = Color(0xFFFF4757),
        darkSecondary = Color(0xFFFF7A8A),
    ),
    INDIGO(
        lightPrimary = Color(0xFF4F46E5),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFF8B87FF),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFF7C6CF0),
        darkSecondary = Color(0xFFA9A3FF),
    ),
    TEAL(
        lightPrimary = Color(0xFF0B7A70),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFF2DD4BF),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFF14B8A6),
        darkSecondary = Color(0xFF5EEAD4),
    ),
    PURPLE(
        lightPrimary = Color(0xFF8E24AA),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFFD07AF0),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFFAB47BC),
        darkSecondary = Color(0xFFE1A4F5),
    ),
    AMBER(
        lightPrimary = Color(0xFFB45309),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFFFBBF24),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFFD97706),
        darkSecondary = Color(0xFFFCD34D),
    ),
    GRAPHITE(
        lightPrimary = Color(0xFF37474F),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFFB0BEC5),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFF546E7A),
        darkSecondary = Color(0xFFCFD8DC),
    ),
    PINK(
        lightPrimary = Color(0xFFC2185B),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFFFF6F9C),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFFE91E63),
        darkSecondary = Color(0xFFFFA1BC),
    ),
    BLUE(
        lightPrimary = Color(0xFF1D4ED8),
        lightOnPrimary = Color.White,
        darkPrimary = Color(0xFF60A5FA),
        darkOnPrimary = Color(0xFF141414),
        lightSecondary = Color(0xFF2563EB),
        darkSecondary = Color(0xFF93C5FD),
    );

    fun primary(dark: Boolean): Color = if (dark) darkPrimary else lightPrimary

    fun onPrimary(dark: Boolean): Color = if (dark) darkOnPrimary else lightOnPrimary

    fun secondary(dark: Boolean): Color = if (dark) darkSecondary else lightSecondary
}

/** 文字与描边的对比强度。HIGH 只加深/提亮前景色，不改动强调色。 */
enum class ContrastLevel { STANDARD, HIGH }

@Immutable
data class AppearanceConfig(
    val seed: ThemeSeed = ThemeSeed.BRAND,
    val amoled: Boolean = false,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
) {
    companion object {
        val Default = AppearanceConfig()
    }
}

/** 当前实际生效的明暗状态，供需要自行构建配色（如预览区）的地方读取。 */
val LocalIsDarkTheme = compositionLocalOf { false }

private val HanimeDarkBase = darkColorScheme(
    primary = HanimePrimary,
    onPrimary = HanimeTextPrimary,
    primaryContainer = HanimeCard,
    onPrimaryContainer = HanimeTextPrimary,
    secondary = HanimePrimaryLight,
    onSecondary = HanimeTextPrimary,
    background = HanimeBackground,
    onBackground = HanimeTextPrimary,
    surface = HanimeCard,
    onSurface = HanimeTextPrimary,
    surfaceVariant = HanimeBorder,
    onSurfaceVariant = HanimeTextSecondary,
    outline = HanimeBorder,
    error = HanimeErrorDark,
    onError = HanimeOnErrorDark,
    errorContainer = HanimeErrorContainerDark,
    onErrorContainer = HanimeOnErrorContainerDark
)

private val HanimeLightBase = lightColorScheme(
    primary = HanimePrimary,
    onPrimary = HanimeTextPrimaryLight,
    primaryContainer = HanimeCardLight,
    onPrimaryContainer = HanimeTextPrimaryLight,
    secondary = HanimePrimaryLight,
    onSecondary = HanimeTextPrimaryLight,
    background = HanimeBackgroundLight,
    onBackground = HanimeTextPrimaryLight,
    surface = HanimeCardLight,
    onSurface = HanimeTextPrimaryLight,
    surfaceVariant = HanimeBorderLight,
    onSurfaceVariant = HanimeTextSecondaryLight,
    outline = HanimeBorderLight,
    error = HanimeError,
    onError = HanimeOnError,
    errorContainer = HanimeErrorContainer,
    onErrorContainer = HanimeOnErrorContainer
)

private val AmoledBackground = Color(0xFF000000)
private val AmoledSurface = Color(0xFF0C0C0C)
private val AmoledSurfaceVariant = Color(0xFF1C1C1C)
private val AmoledOutline = Color(0xFF2E2E2E)

private val HighContrastLightOnSurface = Color(0xFF000000)
private val HighContrastLightOnSurfaceVariant = Color(0xFF3A3A3A)
private val HighContrastLightOutline = Color(0xFF6E6E6E)
private val HighContrastDarkOnSurfaceVariant = Color(0xFFD6D6D6)
private val HighContrastDarkOutline = Color(0xFF6B6B6B)

/** 由基础中性色板 + 种子色 + 显示选项拼出最终配色。 */
fun hanimeColorScheme(dark: Boolean, appearance: AppearanceConfig): ColorScheme {
    val base = if (dark) HanimeDarkBase else HanimeLightBase
    var scheme = base.copy(
        primary = appearance.seed.primary(dark),
        onPrimary = appearance.seed.onPrimary(dark),
        secondary = appearance.seed.secondary(dark),
        onSecondary = if (dark) Color(0xFF141414) else Color.White,
        primaryContainer = appearance.seed.primary(dark).copy(alpha = 0.16f).compositeOver(base.surface),
        onPrimaryContainer = if (dark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A),
    )
    if (dark && appearance.amoled) {
        scheme = scheme.copy(
            background = AmoledBackground,
            surface = AmoledSurface,
            surfaceVariant = AmoledSurfaceVariant,
            primaryContainer = AmoledSurfaceVariant,
            outline = AmoledOutline,
        )
    }
    if (appearance.contrast == ContrastLevel.HIGH) {
        scheme = if (dark) {
            scheme.copy(
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = HighContrastDarkOnSurfaceVariant,
                outline = HighContrastDarkOutline,
            )
        } else {
            scheme.copy(
                onBackground = HighContrastLightOnSurface,
                onSurface = HighContrastLightOnSurface,
                onSurfaceVariant = HighContrastLightOnSurfaceVariant,
                outline = HighContrastLightOutline,
            )
        }
    }
    return scheme
}
