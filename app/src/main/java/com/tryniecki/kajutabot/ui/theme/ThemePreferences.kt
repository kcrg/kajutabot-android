package com.tryniecki.kajutabot.ui.theme

import android.content.Context

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var themeMode: ThemeMode
        get() {
            val storedValue = preferences.getString(KEY_THEME_MODE, null)
            return ThemeMode.entries.firstOrNull { it.name == storedValue } ?: ThemeMode.NATIVE
        }
        set(value) {
            preferences.edit()
                .putString(KEY_THEME_MODE, value.name)
                .apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "kajutabot_preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
