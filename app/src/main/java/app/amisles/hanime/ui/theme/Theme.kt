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

// 当前应用仅提供深色主题；浅色模式暂保留占位，后续如需可自定义配色
private val HanimeLightColorScheme = lightColorScheme(
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

@Composable
fun HanimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
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
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
