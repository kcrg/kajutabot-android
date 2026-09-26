package com.tryniecki.kajutabot.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build

/** Applies the persisted app theme to platform resources and the system splash screen. */
class PlatformThemeController(context: Context) {
    private val appContext = context.applicationContext

    fun apply(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        appContext.getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (mode) {
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.NATIVE -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }
}
