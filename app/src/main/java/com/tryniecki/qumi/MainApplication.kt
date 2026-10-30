package com.tryniecki.qumi

import android.app.Application
import com.tryniecki.qumi.image.CoilSetup

class MainApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        CoilSetup.init(this, container.appConfig.apiBaseUrl, container.sessionManager)
    }
}
