package com.tryniecki.kajutabot.ui.theme

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
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
    // A new dynamic scheme instance would invalidate every color reader in the app.
    val colorScheme = remember(themeMode, systemDarkTheme, context, LocalConfiguration.current) {
        when (themeMode) {
            ThemeMode.LIGHT -> LightColorScheme
            ThemeMode.DARK -> DarkColorScheme
            ThemeMode.NATIVE -> {
                // Dynamic Material You colors are available from Android 12 (API 31).
                // On Android 10/11, Systemowy still follows the system night mode,
                // but uses KajutaBot's own light/dark color schemes.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (systemDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else if (systemDarkTheme) {
                    DarkColorScheme
                } else {
                    LightColorScheme
                }
            }
        }
    }

    val activity = context as? Activity
    val windowBackground = colorScheme.background.toArgb()
    SideEffect(activity, useDarkTheme, windowBackground) {
        activity?.matchWindowControlsToTheme(useDarkTheme, windowBackground)
    }
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}

/**
 * Desktop window controls are drawn over the app, so they need to be told what is behind them:
 * Android 14 reads the task's status bar color, which edge-to-edge leaves transparent and thus
 * dark, and Android 15 onwards reads the caption bar appearance.
 */
private fun Activity.matchWindowControlsToTheme(useDarkTheme: Boolean, background: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setTaskDescription(
            ActivityManager.TaskDescription.Builder()
                .setPrimaryColor(background)
                .setBackgroundColor(background)
                .setStatusBarColor(background)
                .build(),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        val transparent = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
        val light = WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        window.insetsController?.setSystemBarsAppearance(
            if (useDarkTheme) transparent else transparent or light,
            transparent or light,
        )
    }
}
