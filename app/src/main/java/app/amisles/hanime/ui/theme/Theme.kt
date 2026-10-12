package app.amisles.hanime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.amisles.hanime.core.ui.theme.ProvideWindowSizeInfo
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
import app.amisles.hanime.core.ui.theme.LocalUiConfig
import app.amisles.hanime.core.ui.theme.UiConfig
import app.amisles.hanime.core.ui.theme.HomeSectionConfig
import app.amisles.hanime.core.ui.theme.LocalHomeSectionConfig
import app.amisles.hanime.core.ui.theme.HanimePrimary
import app.amisles.hanime.core.ui.theme.HanimePrimaryLight
import app.amisles.hanime.core.ui.theme.HanimeBackground
import app.amisles.hanime.core.ui.theme.HanimeCard
import app.amisles.hanime.core.ui.theme.HanimeBorder
import app.amisles.hanime.core.ui.theme.HanimeTextPrimary
import app.amisles.hanime.core.ui.theme.HanimeTextSecondary
import app.amisles.hanime.core.ui.theme.HanimeBackgroundLight
import app.amisles.hanime.core.ui.theme.HanimeCardLight
import app.amisles.hanime.core.ui.theme.HanimeBorderLight
import app.amisles.hanime.core.ui.theme.HanimeTextPrimaryLight
import app.amisles.hanime.core.ui.theme.HanimeTextSecondaryLight
import app.amisles.hanime.data.preferences.ThemeMode

private val HanimeDarkColorScheme = darkColorScheme(
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
    error = HanimePrimary
)

private val HanimeLightColorScheme = lightColorScheme(
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
    error = HanimePrimary
)

@Composable
fun HanimeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    uiConfig: UiConfig = UiConfig.Default,
    homeSectionConfig: HomeSectionConfig = HomeSectionConfig.Default,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> HanimeDarkColorScheme
        else -> HanimeLightColorScheme
    }

    val density = LocalDensity.current
    val scaledDensity = remember(density, uiConfig.fontScale) {
        if (uiConfig.fontScale == 1f) density
        else Density(density.density, density.fontScale * uiConfig.fontScale)
    }

    // 全局禁用点击组件的涟漪效果
    CompositionLocalProvider(
        LocalRippleConfiguration provides null,
        LocalUiConfig provides uiConfig,
        LocalHomeSectionConfig provides homeSectionConfig,
        LocalDensity provides scaledDensity
    ) {
        ProvideWindowSizeInfo {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = if (currentWindowSizeInfo().largeTypography) TypographyLarge else Typography,
                content = content
            )
        }
    }
}
