package com.tryniecki.kajutabot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.tryniecki.kajutabot.auth.DiscordOAuth
import com.tryniecki.kajutabot.image.CoilSetup
import com.tryniecki.kajutabot.ui.KajutaBotApp
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.theme.KajutaBotTheme
import com.tryniecki.kajutabot.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private lateinit var appViewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.025f)
                .scaleY(1.025f)
                .setDuration(180L)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()

        container = (application as KajutaBotApplication).container
        CoilSetup.init(
            context = this,
            apiBaseUrl = container.appConfig.apiBaseUrl,
            sessionManager = container.sessionManager,
        )
        appViewModel = ViewModelProvider(
            this,
            AppViewModel.factory(container),
        )[AppViewModel::class.java]

        handleIntent(intent)

        val themePreferences = ThemePreferences(this)

        setContent {
            var themeMode by remember {
                mutableStateOf(themePreferences.themeMode)
            }

            KajutaBotTheme(themeMode = themeMode) {
                KajutaBotApp(
                    container = container,
                    appViewModel = appViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        // Update Compose immediately. The Activity handles uiMode
                        // configuration changes itself, so switching themes never
                        // tears down and rebuilds the navigation/UI tree.
                        themePreferences.themeMode = mode
                        themeMode = mode
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (!::appViewModel.isInitialized) return

        when (intent.action) {
            Intent.ACTION_SEND -> handleShareIntent(intent)
            Intent.ACTION_VIEW -> handleViewIntent(intent.data)
            else -> {
                // OAuth callback can also arrive as VIEW without explicit action on some devices.
                if (intent.data != null) handleViewIntent(intent.data)
            }
        }
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = extractUrl(sharedText) ?: return
        appViewModel.onSharedUrl(url)
    }

    private fun handleViewIntent(uri: Uri?) {
        if (uri == null) return
        if (!isOAuthCallbackUri(uri)) return

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        // error_description is intentionally ignored for UI (not logged, not displayed raw).
        appViewModel.handleOAuthCallback(code, state, error)
    }

    private fun isOAuthCallbackUri(uri: Uri): Boolean {
        val clientId = try {
            container.appConfig.discordClientId
        } catch (_: Exception) {
            ""
        }
        if (clientId.isNotBlank()) {
            return DiscordOAuth.isOAuthCallback(uri, clientId)
        }
        // Fallback for misconfigured builds: still recognize the custom scheme shape.
        val scheme = uri.scheme.orEmpty()
        return scheme.startsWith("discord-") && uri.path == "/authorize/callback"
    }

    private fun extractUrl(text: String): String? =
        URL_REGEX.find(text)?.value

    private companion object {
        val URL_REGEX = Regex("""https?://\S+""")
    }
}
