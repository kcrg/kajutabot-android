package com.tryniecki.kajutabot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.app.SessionViewModelOwner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessContainerTest {
    private class ProbeViewModel : ViewModel()

    @Test fun recreationKeepsOneSessionManagerAndRetainedViewModels() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as KajutaBotApplication
        val sessionManager = application.container.sessionManager
        application.container.sessionStore.save(
            UserSession(
                accessToken = "instrumentation-access",
                accessTokenExpiresAtUtc = "2030-01-01T00:00:00Z",
                refreshToken = "instrumentation-refresh",
                refreshTokenExpiresAtUtc = "2030-02-01T00:00:00Z",
                user = AuthUserResponse("test-user", "test", "Test", null),
            ),
        )
        runBlocking { sessionManager.restore() }
        var manager: SessionManager? = null
        var appViewModel: AppViewModel? = null
        var sessionOwner: SessionViewModelOwner? = null
        val probe = ProbeViewModel()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val container = (activity.application as KajutaBotApplication).container
                    manager = container.sessionManager
                    val retained = ViewModelProvider(activity, AppViewModel.Factory(container))[AppViewModel::class.java]
                    appViewModel = retained
                    val owner = retained.ownerForSession(sessionManager.sessionIdentity.value!!)
                    sessionOwner = owner
                    owner.viewModelStore.put("probe", probe)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val container = (activity.application as KajutaBotApplication).container
                    assertSame(manager, container.sessionManager)
                    val retained = ViewModelProvider(activity, AppViewModel.Factory(container))[AppViewModel::class.java]
                    assertSame(appViewModel, retained)
                    val owner = retained.ownerForSession(sessionManager.sessionIdentity.value!!)
                    assertSame(sessionOwner, owner)
                    assertSame(probe, owner.viewModelStore["probe"])
                }
            }
        } finally {
            application.container.sessionStore.clear()
        }
    }
}
