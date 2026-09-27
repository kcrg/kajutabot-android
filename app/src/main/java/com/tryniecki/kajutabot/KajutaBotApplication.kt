package com.tryniecki.kajutabot

import android.app.Application
import com.tryniecki.kajutabot.image.CoilSetup

class KajutaBotApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        CoilSetup.init(this, container.appConfig.apiBaseUrl, container.sessionManager)
    }
}
