package com.tryniecki.kajutabot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = KajutaAccent,
    onPrimary = Color(0xFF25134A),
    primaryContainer = Color(0xFF493274),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFD7B8FF),
    onSecondary = Color(0xFF351450),
    tertiary = Color(0xFFF2ADD8),
    onTertiary = Color(0xFF4B0E36),
    background = KajutaDarkBackground,
    onBackground = KajutaDarkText,
    surface = KajutaDarkSurface,
    onSurface = KajutaDarkText,
    surfaceVariant = KajutaDarkSurface2,
    onSurfaceVariant = KajutaDarkMuted,
    surfaceDim = KajutaDarkBackground,
    surfaceBright = KajutaDarkBorder,
    surfaceContainerLowest = KajutaDarkBackground,
    surfaceContainerLow = KajutaDarkSurface,
    surfaceContainer = KajutaDarkSurface2,
    surfaceContainerHigh = KajutaDarkSurface3,
    surfaceContainerHighest = KajutaDarkSurface3,
    outline = KajutaDarkBorder,
    outlineVariant = KajutaDarkSurface3,
    error = KajutaError,
)

private val LightColorScheme = lightColorScheme(
    primary = KajutaAccentStrong,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF26144A),
    secondary = Color(0xFF7A3FA6),
    onSecondary = Color.White,
    tertiary = KajutaMagenta,
    onTertiary = Color.White,
    background = KajutaLightBackground,
    onBackground = KajutaLightText,
    surface = KajutaLightSurface,
    onSurface = KajutaLightText,
    surfaceVariant = KajutaLightSurface2,
    onSurfaceVariant = KajutaLightMuted,
    surfaceDim = KajutaLightSurface3,
    surfaceBright = KajutaLightSurface,
    surfaceContainerLowest = KajutaLightSurface,
    surfaceContainerLow = KajutaLightBackground,
    surfaceContainer = KajutaLightSurface2,
    surfaceContainerHigh = KajutaLightSurface3,
    surfaceContainerHighest = KajutaLightSurface3,
    outline = KajutaLightBorder,
    outlineVariant = KajutaLightSurface3,
    error = Color(0xFFBA1A1A),
)

@Composable
fun KajutaBotTheme(
    themeMode: ThemeMode = ThemeMode.NATIVE,
    content: @Composable () -> Unit,
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.NATIVE -> systemDarkTheme
    }

    val context = LocalContext.current
    val colorScheme = when {
        themeMode == ThemeMode.NATIVE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // Standard (non-expressive) motion: calm springs without bounce.
        // All screen transitions read their specs from this scheme.
        motionScheme = MotionScheme.standard(),
        typography = Typography,
        content = content,
    )
}
