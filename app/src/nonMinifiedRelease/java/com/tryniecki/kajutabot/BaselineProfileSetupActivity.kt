package com.tryniecki.kajutabot

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tryniecki.kajutabot.auth.GuestLoginResult
import com.tryniecki.kajutabot.ui.text.resolve
import kotlinx.coroutines.launch

/**
 * Test-only state preparation entry point for Baseline Profile generation.
 *
 * This class is compiled only into AGP Baseline Profile test target variants
 * (nonMinifiedRelease / benchmarkRelease), never into the real release APK.
 */
class BaselineProfileSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            runCatching { prepareState() }
                .onSuccess { showStatus(SUCCESS_MARKER) }
                .onFailure(::showFailure)
        }
    }

    private suspend fun prepareState() {
        val container = (application as KajutaBotApplication).container
        val sessionManager = container.sessionManager

        // Reuse a valid persisted session when one exists. On a clean benchmark install this
        // falls through to guest login, avoiding Discord OAuth and any UI automation setup.
        sessionManager.restore()
        if (sessionManager.currentUserSession() == null) {
            when (val result = sessionManager.continueAsGuest()) {
                GuestLoginResult.SignedIn -> Unit
                is GuestLoginResult.Failed -> error(result.message.resolve(this))
                GuestLoginResult.Superseded -> error("Guest login was superseded by another session change.")
            }
        }

        val session = checkNotNull(sessionManager.currentUserSession()) {
            "No active session after Baseline Profile setup."
        }

        // The generator profiles the returning-user path, not first-run onboarding. Keep selection
        // empty and deterministic; PlayerViewModel will resolve the available guest guild normally.
        container.preferencesRepository.clearGuildSelection()
        container.preferencesRepository.setOnboardingCompleted(
            sessionType = session.sessionType,
            discordUserId = session.user.discordUserId,
        )
    }

    private fun showFailure(error: Throwable) {
        val message = error.message ?: error::class.java.simpleName
        Log.e(TAG, "Baseline Profile setup failed", error)
        showStatus("$ERROR_PREFIX$message")
    }

    private fun showStatus(message: String) {
        setContentView(
            TextView(this).apply {
                text = message
                contentDescription = message
                textSize = 16f
                setPadding(32, 32, 32, 32)
            },
        )
    }

    companion object {
        const val ERROR_PREFIX = "BASELINE_PROFILE_SETUP_ERROR: "
        const val SUCCESS_MARKER = "BASELINE_PROFILE_SETUP_OK"
        private const val TAG = "BaselineProfileSetup"
    }
}
