package com.tryniecki.kajutabot

import android.app.Application

class KajutaBotApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}
