package com.tryniecki.kajutabot

import android.content.Context
import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.client.KajutaBotApiClientFactory
import com.tryniecki.kajutabot.api.client.KajutaBotAuthApi
import com.tryniecki.kajutabot.auth.AppConfig
import com.tryniecki.kajutabot.auth.OAuthPendingStorage
import com.tryniecki.kajutabot.auth.PendingOAuthStore
import com.tryniecki.kajutabot.auth.PendingOAuthStoreAdapter
import com.tryniecki.kajutabot.auth.PkceGenerator
import com.tryniecki.kajutabot.auth.SecureSessionStore
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.SessionStore
import com.tryniecki.kajutabot.prefs.GuildSelectionStore

/**
 * Minimal composition root. Created once by MainActivity and passed down.
 * No Hilt/Koin, no global ServiceLocator object.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appConfig = AppConfig(
        apiBaseUrl = BuildConfig.KAJUTABOT_API_BASE_URL,
        discordClientId = BuildConfig.KAJUTABOT_DISCORD_CLIENT_ID,
    )

    val sessionStore: SessionStore = SecureSessionStore(appContext)
    val pendingStore: PendingOAuthStore = PendingOAuthStore(appContext)
    val pendingStorage: OAuthPendingStorage = PendingOAuthStoreAdapter(pendingStore)
    val selectionStore = GuildSelectionStore(appContext)
    val pkceGenerator = PkceGenerator()

    val authApi: KajutaBotAuthApi =
        KajutaBotApiClientFactory.createAuth(appConfig.apiBaseUrl)

    // Created after sessionManager holder to allow token lambda without a cycle at init time.
    lateinit var sessionManager: SessionManager
        private set

    lateinit var api: KajutaBotApi
        private set

    init {
        var managerRef: SessionManager? = null
        api = KajutaBotApiClientFactory.create(appConfig.apiBaseUrl) {
            managerRef?.currentAccessToken()
        }
        sessionManager = SessionManager(
            authApi = authApi,
            apiProvider = { api },
            sessionStore = sessionStore,
            pendingStorage = pendingStorage,
            appConfig = appConfig,
            pkceGenerator = pkceGenerator,
        )
        managerRef = sessionManager
    }
}
