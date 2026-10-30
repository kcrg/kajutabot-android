package com.tryniecki.qumi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tryniecki.qumi.api.model.discord.DiscordGuildResponse
import com.tryniecki.qumi.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.qumi.ui.auth.LoginScreen
import com.tryniecki.qumi.ui.onboarding.AccessCheckingScreen
import com.tryniecki.qumi.ui.onboarding.AccessErrorScreen
import com.tryniecki.qumi.ui.onboarding.NoAccessScreen
import com.tryniecki.qumi.ui.onboarding.OnboardingScreen
import com.tryniecki.qumi.ui.player.SharedEnqueueStatus
import com.tryniecki.qumi.ui.player.SharedTrackScreen
import com.tryniecki.qumi.ui.text.UiText
import com.tryniecki.qumi.ui.theme.AppTheme

/**
 * Test-only host for screens that the generator cannot reach in a live session without side
 * effects: they need signing out, or enqueue a track on the real bot as soon as they open.
 *
 * Every callback is a no-op, so nothing here reaches the backend. Compiled only into the
 * Baseline Profile target variants (nonMinifiedRelease / benchmarkRelease).
 */
class BaselineProfileScreensActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra(EXTRA_SCREEN)
        setContent {
            AppTheme {
                Screen(screen)
            }
        }
    }

    @Composable
    private fun Screen(screen: String?) {
        when (screen) {
            SCREEN_LOGIN -> LoginScreen(
                isSigningIn = false,
                errorMessage = null,
                isOAuthConfigured = true,
                onLoginClick = {},
                onGuestClick = {},
            )
            SCREEN_ACCESS_CHECKING -> AccessCheckingScreen()
            SCREEN_NO_ACCESS -> NoAccessScreen(onRetry = {}, onLogout = {})
            SCREEN_ACCESS_ERROR -> AccessErrorScreen(message = null, onRetry = {}, onLogout = {})
            SCREEN_ONBOARDING -> OnboardingScreen(
                guilds = listOf(DiscordGuildResponse("1", "Server", null, true)),
                voiceChannels = listOf(DiscordVoiceChannelResponse("2", "Voice", 0, 1, false)),
                selectedGuildId = "1",
                selectedChannelId = "2",
                isLoadingVoiceChannels = false,
                canDismiss = true,
                onGuildSelect = {},
                onChannelSelect = {},
                onComplete = {},
                onDismiss = {},
            )
            SCREEN_SHARED_LOADING -> SharedTrackScreen(
                status = SharedEnqueueStatus.Loading,
                unconfirmed = false,
                onClose = {},
            )
            SCREEN_SHARED_FAILED -> SharedTrackScreen(
                status = SharedEnqueueStatus.Failed(UiText.Dynamic("Failed")),
                unconfirmed = false,
                onClose = {},
            )
            SCREEN_DIALOG -> AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.player_clear_dialog_title)) },
                text = { Text(stringResource(R.string.player_clear_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = {}) { Text(stringResource(R.string.action_clear)) }
                },
                dismissButton = {
                    TextButton(onClick = {}) { Text(stringResource(R.string.action_cancel)) }
                },
            )
            else -> error("Unknown Baseline Profile screen: $screen")
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        private const val SCREEN_LOGIN = "login"
        private const val SCREEN_ACCESS_CHECKING = "access-checking"
        private const val SCREEN_NO_ACCESS = "no-access"
        private const val SCREEN_ACCESS_ERROR = "access-error"
        private const val SCREEN_ONBOARDING = "onboarding"
        private const val SCREEN_SHARED_LOADING = "shared-loading"
        private const val SCREEN_SHARED_FAILED = "shared-failed"
        private const val SCREEN_DIALOG = "dialog"
    }
}
