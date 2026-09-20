package com.tryniecki.kajutabot

import android.app.Application
import com.tryniecki.kajutabot.ui.theme.ThemePreferences

class KajutaBotApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        ThemePreferences(this).applyPlatformNightMode()
    }
}
