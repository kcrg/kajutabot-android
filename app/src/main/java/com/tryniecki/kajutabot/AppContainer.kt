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
import com.tryniecki.kajutabot.prefs.FavoritesPreferences

/**
 * Minimal process-lifetime composition root owned by KajutaBotApplication.
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
    val favoritesPreferences = FavoritesPreferences(appContext)
    val pkceGenerator = PkceGenerator()

    val authApi: KajutaBotAuthApi =
        KajutaBotApiClientFactory.createAuth(appConfig.apiBaseUrl)

    private val tokenApis = LinkedHashMap<String, KajutaBotApi>(2, 0.75f, true)

    private fun apiForToken(token: String): KajutaBotApi = synchronized(tokenApis) {
        tokenApis[token]?.let { return@synchronized it }
        KajutaBotApiClientFactory.create(appConfig.apiBaseUrl) { token }.also { api ->
            tokenApis[token] = api
            if (tokenApis.size > 2) tokenApis.remove(tokenApis.keys.first())
        }
    }

    fun clearApiCache() = synchronized(tokenApis) { tokenApis.clear() }

    val sessionManager = SessionManager(
        authApi = authApi,
        apiProvider = ::apiForToken,
        sessionStore = sessionStore,
        pendingStorage = pendingStorage,
        appConfig = appConfig,
        pkceGenerator = pkceGenerator,
    )
}
