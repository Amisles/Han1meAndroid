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
import androidx.compose.ui.platform.LocalContext
import app.amisles.hanime.core.ui.theme.ProvideWindowSizeInfo
import app.amisles.hanime.core.ui.theme.currentWindowSizeInfo
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

    // Disable ripple effects globally for all clickable components
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        ProvideWindowSizeInfo {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = if (currentWindowSizeInfo().largeTypography) TypographyLarge else Typography,
                content = content
            )
        }
    }
}
