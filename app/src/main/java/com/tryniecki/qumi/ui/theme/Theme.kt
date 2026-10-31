package com.tryniecki.qumi.ui.theme

import android.app.Activity
import android.app.ActivityManager
import android.graphics.drawable.ColorDrawable
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
    primary = PaletteAccent,
    onPrimary = Color(0xFF25134A),
    primaryContainer = Color(0xFF493274),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFD7B8FF),
    onSecondary = Color(0xFF351450),
    tertiary = Color(0xFFF2ADD8),
    onTertiary = Color(0xFF4B0E36),
    background = PaletteDarkBackground,
    onBackground = PaletteDarkText,
    surface = PaletteDarkSurface,
    onSurface = PaletteDarkText,
    surfaceVariant = PaletteDarkSurface2,
    onSurfaceVariant = PaletteDarkMuted,
    surfaceDim = PaletteDarkBackground,
    surfaceBright = PaletteDarkBorder,
    surfaceContainerLowest = PaletteDarkBackground,
    surfaceContainerLow = PaletteDarkSurface,
    surfaceContainer = PaletteDarkSurface2,
    surfaceContainerHigh = PaletteDarkSurface3,
    surfaceContainerHighest = PaletteDarkSurface3,
    outline = PaletteDarkBorder,
    outlineVariant = PaletteDarkSurface3,
    error = PaletteError,
)

private val LightColorScheme = lightColorScheme(
    primary = PaletteAccentStrong,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF26144A),
    secondary = Color(0xFF7A3FA6),
    onSecondary = Color.White,
    tertiary = PaletteMagenta,
    onTertiary = Color.White,
    background = PaletteLightBackground,
    onBackground = PaletteLightText,
    surface = PaletteLightSurface,
    onSurface = PaletteLightText,
    surfaceVariant = PaletteLightSurface2,
    onSurfaceVariant = PaletteLightMuted,
    surfaceDim = PaletteLightSurface3,
    surfaceBright = PaletteLightSurface,
    surfaceContainerLowest = PaletteLightSurface,
    surfaceContainerLow = PaletteLightBackground,
    surfaceContainer = PaletteLightSurface2,
    surfaceContainerHigh = PaletteLightSurface3,
    surfaceContainerHighest = PaletteLightSurface3,
    outline = PaletteLightBorder,
    outlineVariant = PaletteLightSurface3,
    error = Color(0xFFBA1A1A),
)

@Composable
fun AppTheme(
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
                // but uses Qumi's own light/dark color schemes.
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
    // The Activity is not recreated for a theme change, so the XML window background stays stale.
    window.setBackgroundDrawable(ColorDrawable(background))
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
